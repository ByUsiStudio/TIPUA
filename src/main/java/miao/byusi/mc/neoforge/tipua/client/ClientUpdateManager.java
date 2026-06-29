package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import miao.byusi.mc.neoforge.tipua.util.ZipHandler;
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

    /**
     * 进入主菜单后检查更新
     * 只在首次进入主菜单时检查，避免重复检查
     */
    public static void checkUpdateOnMainMenu() {
        // 如果已经检查过更新，不再重复检查
        if (hasCheckedUpdate) {
            return;
        }
        hasCheckedUpdate = true;

        // 如果是第一次运行，不检查更新
        if (VersionManager.isFirstRun()) {
            TIPUAMod.LOGGER.info("首次运行，不检查更新 / First run, skipping update check");
            VersionManager.markAsRun();
            return;
        }

        // 如果配置为不自动更新，不检查
        if (!Config.CLIENT.autoUpdate.get()) {
            TIPUAMod.LOGGER.info("自动更新已禁用 / Auto update disabled");
            return;
        }

        // 开始检查更新
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

        // 1. 查询服务器版本
        String serverVersion = fetchServerVersion(serverUrl);
        if (serverVersion.isEmpty()) {
            TIPUAMod.LOGGER.error("无法获取服务器版本 / Failed to get server version");
            showToast("更新失败 / Update failed", "服务器不可用 / Server unavailable");
            return;
        }

        // 2. 对比本地版本
        String localVersion = VersionManager.getLocalVersion();
        if (serverVersion.equals(localVersion)) {
            TIPUAMod.LOGGER.info("版本一致，无需更新 / Version match, no update needed");
            if (Config.CLIENT.showUpdateNotification.get()) {
                showToast("已是最新 / Up to date", "整合包已是最新版本 / Modpack is up to date");
            }
            return;
        }

        TIPUAMod.LOGGER.info("需要更新，本地版本: {}, 服务器版本: {} / Update needed, local: {}, server: {}",
                localVersion, serverVersion, localVersion, serverVersion);

        // 3. 显示更新通知
        if (Config.CLIENT.showUpdateNotification.get()) {
            showToast("发现新版本 / Update available", "正在下载并解压... / Downloading and extracting...");
        }

        // 4. 下载ZIP
        Path downloadedZip = downloadZip(serverUrl);
        if (downloadedZip == null || !Files.exists(downloadedZip)) {
            TIPUAMod.LOGGER.error("下载失败 / Download failed");
            showToast("更新失败 / Update failed", "下载整合包失败 / Failed to download modpack");
            return;
        }

        // 5. 哈希验证（可选）
        if (Config.CLIENT.enableHashVerification.get()) {
            String downloadedHash = VersionManager.calculateFileHash(downloadedZip.toFile());
            if (!downloadedHash.equals(serverVersion)) {
                TIPUAMod.LOGGER.error("哈希验证失败 / Hash verification failed");
                showToast("更新失败 / Update failed", "哈希验证失败 / Hash verification failed");
                try {
                    Files.delete(downloadedZip);
                } catch (IOException ignored) {
                }
                return;
            }
            TIPUAMod.LOGGER.info("哈希验证通过 / Hash verification passed");
        }

        // 6. 解压ZIP（可选）
        if (Config.CLIENT.autoExtract.get()) {
            TIPUAMod.LOGGER.info("开始解压ZIP / Extracting ZIP");
            if (!ZipHandler.extractZip(downloadedZip.toFile())) {
                TIPUAMod.LOGGER.error("解压失败 / Extraction failed");
                showToast("更新失败 / Update failed", "解压整合包失败 / Failed to extract modpack");
                return;
            }
        }

        // 7. 保存版本标识
        VersionManager.saveLocalVersion(serverVersion);

        // 8. 清理ZIP文件
        try {
            Files.delete(downloadedZip);
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理ZIP文件失败 / Failed to clean ZIP file", e);
        }

        // 9. 显示完成通知
        showToast("更新完成 / Update completed", "请重启游戏以使更改生效 / Please restart to apply changes");
        TIPUAMod.LOGGER.info("更新完成 / Update completed");
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
     * 从服务器下载ZIP
     */
    private static Path downloadZip(String serverUrl) {
        try {
            URL url = new URL(serverUrl + "/modpack.zip");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
            connection.setReadTimeout(Config.CLIENT.downloadTimeoutSeconds.get() * 1000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("下载ZIP失败: HTTP {} / Download failed: HTTP {}", connection.getResponseCode(), connection.getResponseCode());
                return null;
            }

            Path tempZip = FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip");

            try (InputStream is = connection.getInputStream();
                 OutputStream os = Files.newOutputStream(tempZip)) {

                byte[] buffer = new byte[8192];
                int read;
                long totalRead = 0;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                    totalRead += read;
                }

                TIPUAMod.LOGGER.info("ZIP下载完成: {} bytes / ZIP downloaded: {} bytes", totalRead, totalRead);
            }

            return tempZip;
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("下载ZIP失败 / Failed to download ZIP", e);
            return null;
        }
    }

    /**
     * 显示通知日志
     */
    private static void showToast(String title, String message) {
        TIPUAMod.LOGGER.info("[通知/Notification] {} - {}", title, message);
    }
}