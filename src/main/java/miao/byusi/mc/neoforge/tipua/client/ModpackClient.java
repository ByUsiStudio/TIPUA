package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.network.ModpackHandshakePayload;
import miao.byusi.mc.neoforge.tipua.network.ModpackManifestPayload;
import miao.byusi.mc.neoforge.tipua.network.ModpackRequestPayload;
import miao.byusi.mc.neoforge.tipua.network.ModpackUpdatePayload;
import miao.byusi.mc.neoforge.tipua.parser.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ModpackClient {
    private static String serverHash = "";
    private static String serverAddress = "";
    private static int serverHttpPort = 25566;
    private static ExecutorService downloadExecutor;

    public static void onHandshake(ModpackHandshakePayload payload) {
        serverHash = payload.modpackHash();
        serverAddress = payload.serverAddress();
        serverHttpPort = payload.httpPort();

        TIPUAMod.LOGGER.info("Received handshake: hash={}, server={}:{}",
                serverHash, serverAddress, serverHttpPort);

        if (Config.CLIENT.autoUpdate.get()) {
            String localHash = getLocalHash();
            ModpackRequestPayload request = new ModpackRequestPayload(localHash);
            PacketDistributor.sendToServer(request);
        }
    }

    public static void onManifest(ModpackManifestPayload payload) {
        if (!payload.requiresUpdate()) {
            TIPUAMod.LOGGER.info("Modpack is up to date");
            if (Config.CLIENT.showUpdateNotification.get()) {
                showToast("tipua.update.up_to_date", "tipua.update.no_changes");
            }
            return;
        }

        TIPUAMod.LOGGER.info("Modpack update required, {} mods to check", payload.mods().size());

        if (Config.CLIENT.showUpdateNotification.get()) {
            showToast("tipua.update.available", "tipua.update.checking");
        }

        downloadMods(payload.mods(), payload.modpackHash());
    }

    public static void onUpdate(ModpackUpdatePayload payload) {
        TIPUAMod.LOGGER.info("Update status: {} ({}%)", payload.message(), payload.progress());

        if (payload.completed()) {
            if (payload.success()) {
                showToast("tipua.update.completed", "tipua.update.restart_required");
            } else {
                showToast("tipua.update.failed", payload.message());
            }
        }
    }

    private static void downloadMods(List<ModInfo> mods, String newHash) {
        if (downloadExecutor != null) {
            downloadExecutor.shutdownNow();
        }

        downloadExecutor = Executors.newFixedThreadPool(Config.CLIENT.maxConcurrentDownloads.get());

        Path modsDir = FMLPaths.MODSDIR.get();
        AtomicInteger downloaded = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(mods.size());
        AtomicInteger failed = new AtomicInteger(0);

        for (ModInfo mod : mods) {
            if (Config.CLIENT.ignoredMods.get().contains(mod.id())) {
                TIPUAMod.LOGGER.info("Skipping ignored mod: {}", mod.id());
                total.decrementAndGet();
                continue;
            }

            downloadExecutor.submit(() -> {
                try {
                    Path targetPath = modsDir.resolve(mod.fileName());

                    if (Files.exists(targetPath)) {
                        String localHash = calculateHash(targetPath);
                        if (localHash.equals(mod.fileHash()) && !mod.fileHash().isEmpty()) {
                            TIPUAMod.LOGGER.info("Mod already up to date: {}", mod.fileName());
                            return;
                        }
                    }

                    String downloadUrl = mod.downloadUrl();
                    if (!downloadUrl.startsWith("http")) {
                        downloadUrl = "http://" + serverAddress + ":" + serverHttpPort + "/mod/" + mod.fileName();
                    }

                    TIPUAMod.LOGGER.info("Downloading mod: {}", mod.fileName());

                    URL url = new URL(downloadUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
                    connection.setReadTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
                    connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        TIPUAMod.LOGGER.error("Failed to download {}: HTTP {}", mod.fileName(), connection.getResponseCode());
                        failed.incrementAndGet();
                        return;
                    }

                    try (InputStream is = connection.getInputStream();
                         OutputStream os = Files.newOutputStream(targetPath)) {

                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    }

                    if (Config.CLIENT.enableHashVerification.get() && !mod.fileHash().isEmpty()) {
                        String downloadedHash = calculateHash(targetPath);
                        if (!downloadedHash.equals(mod.fileHash())) {
                            TIPUAMod.LOGGER.error("Hash verification failed for {}", mod.fileName());
                            Files.delete(targetPath);
                            failed.incrementAndGet();
                            return;
                        }
                    }

                    downloaded.incrementAndGet();
                    TIPUAMod.LOGGER.info("Downloaded mod: {}", mod.fileName());

                } catch (Exception e) {
                    TIPUAMod.LOGGER.error("Failed to download {}", mod.fileName(), e);
                    failed.incrementAndGet();
                } finally {
                    int progress = (downloaded.get() * 100) / Math.max(total.get(), 1);
                    if (downloaded.get() + failed.get() >= total.get()) {
                        saveLocalHash(newHash);

                        ModpackUpdatePayload updatePacket = new ModpackUpdatePayload(
                                "Download completed",
                                progress,
                                true,
                                failed.get() == 0
                        );
                        PacketDistributor.sendToServer(updatePacket);

                        downloadExecutor.shutdown();
                    }
                }
            });
        }
    }

    private static String getLocalHash() {
        File hashFile = new File(TIPUAMod.CONFIG_DIR, "modpack_hash.txt");
        if (hashFile.exists()) {
            try {
                return Files.readString(hashFile.toPath()).trim();
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Failed to read local hash", e);
            }
        }
        return "";
    }

    private static void saveLocalHash(String hash) {
        File hashFile = new File(TIPUAMod.CONFIG_DIR, "modpack_hash.txt");
        try {
            Files.writeString(hashFile.toPath(), hash);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to save local hash", e);
        }
    }

    private static String calculateHash(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream is = Files.newInputStream(path)) {
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("Failed to calculate hash", e);
            return "";
        }
    }

    private static void showToast(String titleKey, String messageKey) {
        Minecraft.getInstance().execute(() -> {
            SystemToast.add(Minecraft.getInstance().getToasts(),
                    SystemToast.SystemToastIds.PACK_LOAD_FAILURE,
                    Component.translatable(titleKey),
                    Component.translatable(messageKey));
        });
    }
}