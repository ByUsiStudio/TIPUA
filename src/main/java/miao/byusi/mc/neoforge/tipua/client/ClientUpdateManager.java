package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.neoforge.tipua.config.ClientConfig;
import miao.byusi.mc.neoforge.tipua.util.HashUtil;
import miao.byusi.mc.neoforge.tipua.util.ModrinthIndex;
import miao.byusi.mc.neoforge.tipua.util.RollbackManager;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ClientUpdateManager {
    private static ExecutorService updateExecutor;
    private static boolean hasCheckedUpdate = false;
    private static volatile ModpackDownloadScreen downloadScreen;
    private static volatile boolean isDownloading = false;
    private static String pendingServerVersion;
    private static boolean updateInProgress = false;
    private static String previousVersion = "0.0.0";

    public static void checkUpdateOnMainMenu() {
        if (hasCheckedUpdate) {
            return;
        }
        hasCheckedUpdate = true;

        if (VersionManager.isFirstRun()) {
            TIPUAMod.LOGGER.info("首次运行，不检查更新 / First run, skipping update check");
            VersionManager.markAsRun();
            return;
        }

        if (!ClientConfig.isAutoUpdate()) {
            TIPUAMod.LOGGER.info("自动更新已禁用 / Auto update disabled");
            return;
        }

        startUpdateCheck();
    }

    private static void startUpdateCheck() {
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        updateExecutor = Executors.newSingleThreadExecutor();
        updateExecutor.submit(() -> {
            try {
                doUpdateCheck();
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("更新检查失败 / Update check failed", e);
                showToast("更新失败 / Update failed", "检查更新时出错 / Error checking for updates");
                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.setError(e.getMessage());
                    }
                });
            }
        });
    }

    private static void doUpdateCheck() {
        String serverAddress = ClientConfig.getServerAddress();
        int serverPort = ClientConfig.getHttpPort();
        String serverUrl = "http://" + serverAddress + ":" + serverPort;

        TIPUAMod.LOGGER.info("开始检查更新: {} / Checking updates: {}", serverUrl, serverUrl);

        String serverVersion = fetchServerVersion(serverUrl);
        if (serverVersion.isEmpty() || !VersionManager.isValidVersion(serverVersion)) {
            TIPUAMod.LOGGER.error("无法获取服务器版本或版本格式无效 / Failed to get server version or invalid format");
            showToast("更新失败 / Update failed", "服务器不可用 / Server unavailable");
            return;
        }

        String localVersion = VersionManager.getLocalVersion();
        int comparison = VersionManager.compareVersions(serverVersion, localVersion);

        if (comparison <= 0) {
            TIPUAMod.LOGGER.info("版本一致或更新，无需更新 / Version match or newer, no update needed. Local: {}, Server: {}", localVersion, serverVersion);
            if (ClientConfig.isShowUpdateNotification()) {
                showToast("已是最新 / Up to date", "整合包已是最新版本 / Modpack is up to date");
            }
            return;
        }

        TIPUAMod.LOGGER.info("需要更新，本地版本: {}, 服务器版本: {} / Update needed, local: {}, server: {}",
                localVersion, serverVersion, localVersion, serverVersion);

        if (ClientConfig.isShowUpdateNotification()) {
            showToast("发现新版本 / Update available", "新版本: " + serverVersion);
        }

        pendingServerVersion = serverVersion;

        Path gameDir = FMLPaths.GAMEDIR.get();
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path indexFile = configDir.resolve("tipua").resolve("modrinth.index.json");
        Path modsDir = gameDir.resolve("mods");

        boolean indexExists = Files.exists(indexFile);
        boolean modsExists = Files.exists(modsDir) && Files.isDirectory(modsDir);

        TIPUAMod.LOGGER.info("索引文件存在: {}, mods目录存在: {}", indexExists, modsExists);

        String indexJson = null;
        if (!indexExists) {
            indexJson = fetchIndexFromServer(serverUrl);
            if (indexJson == null || indexJson.isEmpty()) {
                TIPUAMod.LOGGER.error("无法从服务器获取索引文件 / Failed to get index file from server");
                showToast("更新失败 / Update failed", "无法获取索引文件 / Failed to get index file");
                return;
            }
            TIPUAMod.LOGGER.info("已从服务器获取索引文件 / Index file fetched from server");
        } else {
            try {
                indexJson = Files.readString(indexFile);
                TIPUAMod.LOGGER.info("已读取本地索引文件 / Index file read from local");
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("读取本地索引文件失败 / Failed to read local index file", e);
                indexJson = fetchIndexFromServer(serverUrl);
                if (indexJson == null || indexJson.isEmpty()) {
                    showToast("更新失败 / Update failed", "无法获取索引文件 / Failed to get index file");
                    return;
                }
            }
        }

        ModrinthIndex index = ModrinthIndex.fromJson(indexJson);
        if (index.getFiles().isEmpty()) {
            TIPUAMod.LOGGER.error("索引文件中没有文件列表 / No files in index");
            showToast("更新失败 / Update failed", "索引文件无效 / Invalid index file");
            return;
        }

        if (!indexExists && modsExists) {
            TIPUAMod.LOGGER.info("索引文件不存在但mods目录存在，删除mods目录 / Index not found but mods exists, deleting mods");
            deleteDirectory(modsDir);
        }

        if (indexExists && !modsExists) {
            TIPUAMod.LOGGER.info("索引文件存在但mods目录不存在，开始下载 / Index exists but mods missing, starting download");
        }

        previousVersion = VersionManager.getLocalVersion();

        showDownloadScreen(serverVersion);

        startIndexDownload(index, serverUrl);
    }

    private static void showDownloadScreen(String version) {
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                downloadScreen = new ModpackDownloadScreen(minecraft.screen, version, () -> {
                    cancelDownload();
                }, () -> {
                    performAutoRollback();
                });
                minecraft.setScreen(downloadScreen);
                downloadScreen.addLogEntry("info", "开始下载整合包... / Starting modpack download...");
            }
        });
    }

    private static void startIndexDownload(ModrinthIndex index, String serverUrl) {
        isDownloading = true;
        updateInProgress = true;

        boolean backupSuccess = RollbackManager.backupBeforeUpdate(FMLPaths.GAMEDIR.get(), pendingServerVersion);
        if (!backupSuccess) {
            TIPUAMod.LOGGER.warn("创建回滚备份失败");
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        List<ModrinthIndex.FileEntry> files = index.getFiles();
        long totalSize = index.getTotalSize();
        long downloadedSize = 0;

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.updateDownloadProgress(0, totalSize);
                downloadScreen.setFileProgress(0, files.size(), "");
            }
        });

        for (int i = 0; i < files.size(); i++) {
            if (!isDownloading) {
                TIPUAMod.LOGGER.info("下载已取消 / Download cancelled");
                return;
            }

            ModrinthIndex.FileEntry entry = files.get(i);
            String fileName = entry.path.substring(entry.path.lastIndexOf('/') + 1);
            final int currentIndex = i + 1;
            final int totalFileCount = files.size();
            final String currentFileName = fileName;

            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.setFileProgress(currentIndex, totalFileCount, currentFileName);
                    downloadScreen.addLogEntry("info", "正在下载: " + currentFileName);
                }
            });

            Path targetPath = gameDir.resolve(entry.path);

            try {
                Files.createDirectories(targetPath.getParent());

                boolean downloaded = downloadFileWithRetry(entry, targetPath);
                if (!downloaded) {
                    handleUpdateFailure("下载失败: " + fileName, null);
                    return;
                }

                boolean verified = HashUtil.verifyAll(targetPath.toFile(), entry.getSha1(), entry.getSha512());
                if (!verified) {
                    handleUpdateFailure("哈希校验失败: " + fileName, null);
                    return;
                }

                downloadedSize += entry.fileSize;
                final long currentDownloadedSize = downloadedSize;
                final long totalDownloadSize = totalSize;
                final String completedFileName = fileName;

                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.updateDownloadProgress(currentDownloadedSize, totalDownloadSize);
                        downloadScreen.addLogEntry("info", "下载完成: " + completedFileName);
                    }
                });

                TIPUAMod.LOGGER.info("文件下载完成: {} / File downloaded: {}", fileName, fileName);

            } catch (IOException e) {
                handleUpdateFailure("下载错误: " + fileName, null);
                return;
            }
        }

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.addLogEntry("info", "所有文件下载完成，开始下载data.zip（如果存在）");
            }
        });

        String dataZipUrl = serverUrl + "/data.zip";
        Path dataZipPath = gameDir.resolve(".tipua_data.zip");

        if (downloadDataZip(dataZipUrl, dataZipPath)) {
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "开始解压data.zip...");
                }
            });
            if (!extractDataZip(dataZipPath, gameDir)) {
                handleUpdateFailure("data.zip解压失败", dataZipPath);
                return;
            }
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "data.zip解压完成");
                }
            });

            try {
                Files.delete(dataZipPath);
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("清理data.zip临时文件失败");
            }
        } else {
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "服务器未提供data.zip，跳过");
                }
            });
        }

        VersionManager.saveLocalVersion(pendingServerVersion);

        isDownloading = false;
        updateInProgress = false;

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.showRestartButton();
            }
        });

        showToast("更新完成 / Update completed", "请重启游戏以使更改生效 / Please restart to apply changes");
        TIPUAMod.LOGGER.info("更新完成 / Update completed");
    }

    private static boolean downloadFileWithRetry(ModrinthIndex.FileEntry entry, Path targetPath) {
        int maxRetries = ClientConfig.getMaxRetryAttempts();
        int retryDelay = ClientConfig.getRetryDelaySeconds();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            for (String url : entry.downloads) {
                try {
                    if (downloadFile(url, targetPath)) {
                        return true;
                    }
                } catch (IOException e) {
                    TIPUAMod.LOGGER.warn("下载失败，尝试下一个地址: {}", url);
                }
            }

            if (attempt < maxRetries) {
                TIPUAMod.LOGGER.info("重试下载: {} / Retrying download: {}", attempt + 1, attempt + 1);
                try {
                    Thread.sleep(retryDelay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }

    private static boolean downloadFile(String urlStr, Path targetPath) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(ClientConfig.getDownloadTimeoutSeconds() * 1000);
        connection.setReadTimeout(ClientConfig.getDownloadTimeoutSeconds() * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            return false;
        }

        long contentLength = connection.getContentLengthLong();

        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(targetPath)) {

            byte[] buffer = new byte[8192];
            int read;
            long downloaded = 0;

            while ((read = is.read(buffer)) != -1) {
                if (!isDownloading) {
                    throw new IOException("下载已取消");
                }
                os.write(buffer, 0, read);
                downloaded += read;

                if (downloadScreen != null && contentLength > 0) {
                    final long currentDownloaded = downloaded;
                    final long totalContentLength = contentLength;
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.addLogEntry("info", String.format("下载中... %d/%d", currentDownloaded, totalContentLength));
                        }
                    });
                }
            }
        } finally {
            connection.disconnect();
        }

        return true;
    }

    private static boolean downloadDataZip(String urlStr, Path targetPath) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                return false;
            }

            try (InputStream is = connection.getInputStream();
                 OutputStream os = Files.newOutputStream(targetPath)) {

                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }

            TIPUAMod.LOGGER.info("data.zip下载完成 / data.zip downloaded");
            return true;

        } catch (IOException e) {
            TIPUAMod.LOGGER.info("data.zip下载失败（可能不存在） / data.zip download failed (may not exist)");
            return false;
        }
    }

    private static boolean extractDataZip(Path zipPath, Path targetDir) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    TIPUAMod.LOGGER.warn("跳过可疑路径: {}", entry.getName());
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }

            TIPUAMod.LOGGER.info("data.zip解压完成 / data.zip extraction completed");
            return true;

        } catch (IOException e) {
            TIPUAMod.LOGGER.error("data.zip解压失败 / data.zip extraction failed", e);
            return false;
        }
    }

    private static void handleUpdateFailure(String errorMessage, Path tempPath) {
        isDownloading = false;
        updateInProgress = false;

        TIPUAMod.LOGGER.error("更新失败: {} / Update failed: {}", errorMessage, errorMessage);

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setError(errorMessage);

                if (RollbackManager.hasRollbackBackup(FMLPaths.GAMEDIR.get())) {
                    downloadScreen.showRollbackButton();

                    if (ClientConfig.isAutoRollback()) {
                        performAutoRollback();
                    }
                } else {
                    downloadScreen.showExitButton();
                }
            }
        });

        showToast("更新失败 / Update failed", errorMessage);

        if (tempPath != null && Files.exists(tempPath)) {
            try {
                Files.delete(tempPath);
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("清理临时文件失败 / Failed to clean temp file", e);
            }
        }
    }

    private static void performAutoRollback() {
        TIPUAMod.LOGGER.info("开始自动回滚到版本: {} / Starting automatic rollback to version: {}", previousVersion, previousVersion);

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.addLogEntry("info", String.format("[自动回滚/Auto Rollback] 回滚到版本 %s", previousVersion));
            }
        });

        boolean rollbackSuccess = RollbackManager.performRollback(FMLPaths.GAMEDIR.get());

        if (rollbackSuccess) {
            TIPUAMod.LOGGER.info("自动回滚成功 / Automatic rollback successful");
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("success", String.format("[回滚成功/Rollback Success] 已回滚到版本 %s", previousVersion));
                    downloadScreen.showRestartButton();
                }
            });

            showToast("自动回滚成功 / Auto-rollback successful", "请重启游戏以使更改生效 / Please restart to apply changes");
        } else {
            TIPUAMod.LOGGER.error("自动回滚失败 / Automatic rollback failed");
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("error", "[回滚失败/Rollback Failed] 自动回滚失败");
                    downloadScreen.showExitButton();
                }
            });

            showToast("自动回滚失败 / Auto-rollback failed", "请手动执行 /tipua rollback");
        }
    }

    private static void cancelDownload() {
        isDownloading = false;
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        TIPUAMod.LOGGER.info("下载已取消 / Download cancelled");
    }

    private static String fetchServerVersion(String serverUrl) {
        try {
            URL url = new URL(serverUrl + "/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("服务器返回错误: {} / Server error: {}", connection.getResponseCode(), connection.getResponseCode());
                return "";
            }

            try (InputStream is = connection.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                return reader.readLine().trim();
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("获取服务器版本失败 / Failed to fetch server version", e);
            return "";
        }
    }

    private static String fetchIndexFromServer(String serverUrl) {
        try {
            URL url = new URL(serverUrl + "/modrinth.index.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("获取索引文件失败: HTTP {} / Failed to get index file: HTTP {}", connection.getResponseCode(), connection.getResponseCode());
                return "";
            }

            try (InputStream is = connection.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("获取索引文件失败 / Failed to fetch index file", e);
            return "";
        }
    }

    private static void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                TIPUAMod.LOGGER.error("删除失败: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("删除目录失败: {}", directory, e);
        }
    }

    private static void executeOnMainThread(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(runnable);
        }
    }

    private static void showToast(String title, String message) {
        TIPUAMod.LOGGER.info("[通知/Notification] {} - {}", title, message);
    }
}