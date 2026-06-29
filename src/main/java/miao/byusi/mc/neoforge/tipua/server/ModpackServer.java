package miao.byusi.mc.neoforge.tipua.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.network.ModpackHandshakePayload;
import miao.byusi.mc.neoforge.tipua.network.ModpackManifestPayload;
import miao.byusi.mc.neoforge.tipua.network.ModpackRequestPayload;
import miao.byusi.mc.neoforge.tipua.parser.ModInfo;
import miao.byusi.mc.neoforge.tipua.parser.ModpackParser;
import miao.byusi.mc.neoforge.tipua.parser.ModpackParserRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModpackServer {
    private static HttpServer httpServer;
    private static Timer checkTimer;
    private static String currentHash = "";
    private static List<ModInfo> currentMods = new CopyOnWriteArrayList<>();
    private static File modpackFile;

    public static void initialize() {
        if (!FMLLoader.getDist().isDedicatedServer()) {
            return;
        }

        modpackFile = new File(Config.SERVER.modpackPath.get());
        if (!modpackFile.isAbsolute()) {
            modpackFile = new File(TIPUAMod.MODPACK_DIR, Config.SERVER.modpackPath.get());
        }

        if (Config.SERVER.enableFileServer.get()) {
            startHttpServer();
        }

        scheduleCheck();
        initialCheck();
    }

    private static void startHttpServer() {
        try {
            int port = Config.SERVER.httpPort.get();
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);

            httpServer.createContext("/mod/", new ModFileHandler());
            httpServer.createContext("/modpack", new ModpackHandler());

            httpServer.setExecutor(null);
            httpServer.start();

            TIPUAMod.LOGGER.info("TIPUA HTTP server started on port {}", port);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to start HTTP server", e);
        }
    }

    private static void scheduleCheck() {
        if (checkTimer != null) {
            checkTimer.cancel();
        }

        int interval = Config.SERVER.checkIntervalSeconds.get() * 1000;
        checkTimer = new Timer(true);
        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkModpackChanges();
            }
        }, 0, interval);
    }

    private static void initialCheck() {
        checkModpackChanges();
    }

    private static void checkModpackChanges() {
        if (!modpackFile.exists()) {
            TIPUAMod.LOGGER.warn("Modpack file not found: {}", modpackFile.getAbsolutePath());
            return;
        }

        String newHash = calculateFileHash(modpackFile);
        if (!newHash.equals(currentHash)) {
            TIPUAMod.LOGGER.info("Modpack changed! Old hash: {}, New hash: {}", currentHash, newHash);
            currentHash = newHash;
            parseModpack();
        }
    }

    private static String calculateFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream is = new FileInputStream(file)) {
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

    private static void parseModpack() {
        ModpackParser parser = ModpackParserRegistry.findParser(modpackFile);
        if (parser == null) {
            parser = ModpackParserRegistry.findParserByFormat(Config.SERVER.defaultModpackFormat.get());
        }

        if (parser != null) {
            String baseUrl = "http://localhost:" + Config.SERVER.httpPort.get();
            currentMods = parser.parse(modpackFile, baseUrl);
            TIPUAMod.LOGGER.info("Parsed modpack with {} mods", currentMods.size());
        } else {
            TIPUAMod.LOGGER.error("No parser found for modpack: {}", modpackFile.getName());
            currentMods.clear();
        }
    }

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        String serverAddress = player.connection.getConnection().getRemoteAddress().toString();
        if (serverAddress.contains("/")) {
            serverAddress = serverAddress.substring(0, serverAddress.indexOf('/'));
        }

        ModpackHandshakePayload payload = new ModpackHandshakePayload(
                currentHash,
                serverAddress,
                Config.SERVER.httpPort.get()
        );

        PacketDistributor.sendToPlayer(player, payload);
        TIPUAMod.LOGGER.info("Sent handshake to player: {}", player.getDisplayName().getString());
    }

    public static void onRequest(ModpackRequestPayload payload, IPayloadContext context) {
        ServerPlayer player = context.player();
        if (player == null) {
            return;
        }

        String clientHash = payload.clientHash();
        boolean requiresUpdate = !clientHash.equals(currentHash);

        ModpackManifestPayload manifestPayload = new ModpackManifestPayload(
                currentMods,
                currentHash,
                requiresUpdate
        );

        PacketDistributor.sendToPlayer(player, manifestPayload);
        TIPUAMod.LOGGER.info("Sent manifest to player: {}, requires update: {}",
                player.getDisplayName().getString(), requiresUpdate);
    }

    private static class ModFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String fileName = exchange.getRequestURI().getPath().replace("/mod/", "");

            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                TIPUAMod.LOGGER.warn("Path traversal attempt: {}", fileName);
                return;
            }

            Path modsDir = modpackFile.toPath().getParent().resolve("mods");
            if (!Files.exists(modsDir)) {
                modsDir = modpackFile.toPath().resolve("mods");
            }

            Path filePath = modsDir.resolve(fileName).normalize();

            if (!filePath.startsWith(modsDir)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                TIPUAMod.LOGGER.warn("Path traversal blocked: {}", fileName);
                return;
            }

            if (!Files.exists(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] data = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();

            TIPUAMod.LOGGER.debug("Served mod file: {}", fileName);
        }
    }

    private static class ModpackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!modpackFile.exists()) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] data = Files.readAllBytes(modpackFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();

            TIPUAMod.LOGGER.debug("Served modpack file");
        }
    }

    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            TIPUAMod.LOGGER.info("TIPUA HTTP server stopped");
        }
        if (checkTimer != null) {
            checkTimer.cancel();
        }
    }
}