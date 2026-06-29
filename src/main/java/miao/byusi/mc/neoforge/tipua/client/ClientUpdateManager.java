package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import miao.byusi.mc.neoforge.tipua.util.ZipHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端更新管理器
 * 进入主菜单后自动检查更新，第一次启动不检查
 */
public class ClientUpdateManager {
    private static ExecutorService updateExecutor;
    private static boolean hasCheckedUpdate = false;
    private static volatile ModpackDownloadScreen downloadScreen;
    private static volatile boolean isDownloading = false;

    /**
     * 进入主菜单后检查更新
     * 只在首次进入主菜单时检查，避免重复检查
     */
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

        if (!Config.CLIENT.autoUpdate.get()) {
            TIPUAMod.LOGGER.info("自动更新已禁用 / Auto update disabled");
            return;
        }

        startUpdateCheck();
    }

    /**
     * 开始检查更新（异步）
     */
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

    /**
     * 执行更新检查
     */
    private static void doUpdateCheck() {
        String serverAddress = Config.CLIENT.serverAddress.get();
        int serverPort = Config.CLIENT.httpPort.get();
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
            if (Config.CLIENT.showUpdateNotification.get()) {
                showToast("已是最新 / Up to date", "整合包已是最新版本 / Modpack is up to date");
            }
            return;
        }

        TIPUAMod.LOGGER.info("需要更新，本地版本: {}, 服务器版本: {} / Update needed, local: {}, server: {}",
                localVersion, serverVersion, localVersion, serverVersion);

        if (Config.CLIENT.showUpdateNotification.get()) {
            showToast("发现新版本 / Update available", "新版本: " + serverVersion);
        }

        isDownloading = true;
        final String finalServerVersion = serverVersion;
        
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen != null) {
                downloadScreen = new ModpackDownloadScreen(minecraft.screen, finalServerVersion, () -> {
                    cancelDownload();
                });
                minecraft.setScreen(downloadScreen);
            }
        });

        String downloadUrl = null;
        String clientUrl = Config.CLIENT.modpackDownloadUrl.get();
        
        if (clientUrl != null && !clientUrl.isEmpty()) {
            downloadUrl = clientUrl;
            TIPUAMod.LOGGER.info("使用客户端配置的下载地址 / Using client-configured download URL");
        } else {
            downloadUrl = fetchDownloadUrl(serverUrl);
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                TIPUAMod.LOGGER.error("无法获取下载地址 / Failed to get download URL");
                showToast("更新失败 / Update failed", "无法获取下载地址 / Failed to get download URL");
                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.setError("无法获取下载地址");
                    }
                });
                isDownloading = false;
                return;
            }
            TIPUAMod.LOGGER.info("从服务端获取下载地址: {} / Download URL from server: {}", downloadUrl, downloadUrl);
        }
        
        Path downloadedZip = null;
        try {
            downloadedZip = downloadZipFromUrl(new URL(downloadUrl), finalServerVersion);
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("下载过程异常 / Download process exception", e);
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.setError("下载异常: " + e.getMessage());
                }
            });
            isDownloading = false;
            return;
        }

        if (downloadedZip == null || !Files.exists(downloadedZip)) {
            TIPUAMod.LOGGER.error("下载失败 / Download failed");
            showToast("更新失败 / Update failed", "下载整合包失败 / Failed to download modpack");
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.setError("下载失败");
                }
            });
            isDownloading = false;
            return;
        }

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setComplete();
            }
        });

        if (Config.CLIENT.autoExtract.get()) {
            TIPUAMod.LOGGER.info("开始解压ZIP / Extracting ZIP");
            long fileSize = downloadedZip.toFile().length();
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.updateProgress(fileSize, fileSize, "解压中...");
                }
            });
            
            if (!ZipHandler.extractZip(downloadedZip.toFile())) {
                TIPUAMod.LOGGER.error("解压失败 / Extraction failed");
                showToast("更新失败 / Update failed", "解压整合包失败 / Failed to extract modpack");
                isDownloading = false;
                return;
            }
        }

        VersionManager.saveLocalVersion(finalServerVersion);

        try {
            Files.delete(downloadedZip);
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理ZIP文件失败 / Failed to clean ZIP file", e);
        }

        isDownloading = false;

        showToast("更新完成 / Update completed", "请重启游戏以使更改生效 / Please restart to apply changes");
        TIPUAMod.LOGGER.info("更新完成 / Update completed");
    }

    /**
     * 通用下载方法
     */
    private static Path downloadZipFromUrl(URL url, String version) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
        connection.setReadTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("下载ZIP失败: HTTP " + connection.getResponseCode() + " / Download failed: HTTP " + connection.getResponseCode());
        }

        int contentLength = connection.getContentLength();
        Path tempZip = FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip");

        try (InputStream is = connection.getInputStream();
             OutputStream os = Files.newOutputStream(tempZip)) {

            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            long lastUpdateTime = System.currentTimeMillis();
            
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                totalRead += read;
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime > 500) {
                    final long finalTotalRead = totalRead;
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.updateProgress(finalTotalRead, contentLength, "下载中...");
                        }
                    });
                    lastUpdateTime = currentTime;
                }
                
                if (!isDownloading) {
                    throw new IOException("下载已取消 / Download cancelled");
                }
            }

            TIPUAMod.LOGGER.info("ZIP下载完成: {} bytes / ZIP downloaded: {} bytes", totalRead, totalRead);
        } finally {
            connection.disconnect();
        }

        return tempZip;
    }

    /**
     * 从服务器获取版本标识
     */
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

    /**
     * 从服务器获取下载地址（直链）
     */
    private static String fetchDownloadUrl(String serverUrl) {
        try {
            URL url = new URL(serverUrl + "/download-url");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("获取下载地址失败: HTTP {} / Failed to get download URL: HTTP {}", connection.getResponseCode(), connection.getResponseCode());
                return "";
            }

            try (InputStream is = connection.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                return reader.readLine().trim();
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("获取下载地址失败 / Failed to fetch download URL", e);
            return "";
        }
    }

    /**
     * 取消下载
     */
    private static void cancelDownload() {
        isDownloading = false;
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        TIPUAMod.LOGGER.info("下载已取消 / Download cancelled");
    }

    /**
     * 在主线程执行任务
     */
    private static void executeOnMainThread(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(runnable);
        }
    }

    /**
     * 显示通知日志
     */
    private static void showToast(String title, String message) {
        TIPUAMod.LOGGER.info("[通知/Notification] {} - {}", title, message);
    }
}