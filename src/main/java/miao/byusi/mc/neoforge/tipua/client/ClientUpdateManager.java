package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.neoforge.tipua.client.gui.UpdatePreviewScreen;
import miao.byusi.mc.neoforge.tipua.config.ClientConfig;
import miao.byusi.mc.neoforge.tipua.util.ModpackManifest;
import miao.byusi.mc.neoforge.tipua.util.RollbackManager;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import miao.byusi.mc.neoforge.tipua.util.ZipHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private static String pendingDownloadUrl;
    private static String pendingServerVersion;

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
        
        String downloadUrl = null;
        String clientUrl = ClientConfig.getModpackDownloadUrl();
        
        if (clientUrl != null && !clientUrl.isEmpty()) {
            downloadUrl = clientUrl;
            TIPUAMod.LOGGER.info("使用客户端配置的下载地址 / Using client-configured download URL");
        } else {
            downloadUrl = fetchDownloadUrl(serverUrl);
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                TIPUAMod.LOGGER.error("无法获取下载地址 / Failed to get download URL");
                showToast("更新失败 / Update failed", "无法获取下载地址 / Failed to get download URL");
                return;
            }
            TIPUAMod.LOGGER.info("从服务端获取下载地址: {} / Download URL from server: {}", downloadUrl, downloadUrl);
        }
        
        pendingDownloadUrl = downloadUrl;
        
        Path tempZip = FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip");
        if (Files.exists(tempZip)) {
            try {
                Files.delete(tempZip);
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("清理临时文件失败 / Failed to clean temp file", e);
            }
        }
        
        String encodedUrl = encodeUrlWithChinese(downloadUrl);
        try {
            TIPUAMod.LOGGER.info("预下载ZIP以获取manifest / Pre-downloading ZIP to get manifest");
            tempZip = downloadZipWithResume(new URL(encodedUrl), serverVersion);
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("预下载失败 / Pre-download failed", e);
            showToast("更新失败 / Update failed", "下载整合包失败 / Failed to download modpack");
            return;
        }
        
        ModpackManifest manifest = ModpackManifest.fromZip(tempZip.toFile());
        
        final String finalLocalVersion = localVersion;
        final Path finalTempZip = tempZip;
        
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen != null) {
                UpdatePreviewScreen previewScreen = new UpdatePreviewScreen(
                        minecraft.screen, 
                        serverVersion, 
                        finalLocalVersion, 
                        manifest,
                        () -> startDownload(finalTempZip),
                        () -> cancelUpdate(finalTempZip)
                );
                minecraft.setScreen(previewScreen);
            }
        });
    }
    
    private static void startDownload(Path tempZip) {
        isDownloading = true;
        final String finalServerVersion = pendingServerVersion;
        
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen != null) {
                downloadScreen = new ModpackDownloadScreen(minecraft.screen, finalServerVersion, () -> {
                    cancelDownload();
                });
                minecraft.setScreen(downloadScreen);
            }
        });
        
        if (ClientConfig.isAutoExtract()) {
            TIPUAMod.LOGGER.info("开始解压ZIP / Extracting ZIP");
            
            RollbackManager.backupBeforeUpdate(FMLPaths.GAMEDIR.get(), finalServerVersion);
            
            boolean extractionSuccess = ZipHandler.extractZip(tempZip.toFile(), new ZipHandler.ExtractionProgressCallback() {
                @Override
                public void onFileExtracting(String relativePath, long current, long total) {
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.updateExtractionProgress(relativePath, current, total);
                        }
                    });
                }

                @Override
                public void onComplete() {
                    TIPUAMod.LOGGER.info("解压完成 / Extraction completed");
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.setExtractionComplete();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    TIPUAMod.LOGGER.error("解压失败 / Extraction failed: {}", error);
                    showToast("更新失败 / Update failed", "解压失败: " + error);
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.setError("解压失败: " + error);
                        }
                    });
                }
            });

            if (!extractionSuccess) {
                isDownloading = false;
                return;
            }
        }

        VersionManager.saveLocalVersion(finalServerVersion);

        try {
            Files.delete(tempZip);
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理ZIP文件失败 / Failed to clean ZIP file", e);
        }

        isDownloading = false;

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.showRestartButton();
            }
        });

        showToast("更新完成 / Update completed", "请重启游戏以使更改生效 / Please restart to apply changes");
        TIPUAMod.LOGGER.info("更新完成 / Update completed");
    }
    
    private static void cancelUpdate(Path tempZip) {
        try {
            if (tempZip != null && Files.exists(tempZip)) {
                Files.delete(tempZip);
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理临时文件失败 / Failed to clean temp file", e);
        }
        TIPUAMod.LOGGER.info("更新已取消 / Update cancelled");
    }

    /**
     * 支持断点续传的下载方法
     */
    private static Path downloadZipWithResume(URL url, String version) throws IOException {
        Path tempZip = FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip");
        long existingBytes = 0;
        
        if (Files.exists(tempZip)) {
            existingBytes = Files.size(tempZip);
            TIPUAMod.LOGGER.info("检测到已下载 {} 字节，尝试续传 / Detected {} bytes already downloaded, attempting resume", existingBytes, existingBytes);
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(ClientConfig.getDownloadTimeoutSeconds() * 1000);
        connection.setReadTimeout(ClientConfig.getDownloadTimeoutSeconds() * 1000);
        connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");

        if (existingBytes > 0) {
            connection.setRequestProperty("Range", "bytes=" + existingBytes + "-");
        }

        int responseCode = connection.getResponseCode();
        TIPUAMod.LOGGER.info("HTTP响应码: {} / HTTP response code: {}", responseCode, responseCode);

        boolean isResume = responseCode == HttpURLConnection.HTTP_PARTIAL;
        if (!isResume && responseCode != HttpURLConnection.HTTP_OK) {
            if (existingBytes > 0) {
                TIPUAMod.LOGGER.warn("续传失败，重新开始下载 / Resume failed, restarting download");
                Files.deleteIfExists(tempZip);
                connection.disconnect();
                return downloadZipWithResume(url, version);
            }
            handleDownloadError("下载失败: HTTP " + responseCode);
            throw new IOException("下载ZIP失败: HTTP " + responseCode + " / Download failed: HTTP " + responseCode);
        }

        int contentLength = connection.getContentLength();
        long totalSize = contentLength;
        if (isResume) {
            totalSize += existingBytes;
        }

        OutputStream os;
        if (isResume) {
            os = Files.newOutputStream(tempZip, StandardOpenOption.APPEND);
        } else {
            os = Files.newOutputStream(tempZip);
        }

        try (InputStream is = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = existingBytes;
            long lastUpdateTime = System.currentTimeMillis();
            
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                totalRead += read;
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime > 500) {
                    final long finalTotalRead = totalRead;
                    final long finalTotalSize = totalSize;
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.updateDownloadProgress(finalTotalRead, finalTotalSize);
                        }
                    });
                    lastUpdateTime = currentTime;
                }
                
                if (!isDownloading && updateExecutor != null && !updateExecutor.isShutdown()) {
                    handleDownloadError("下载已取消 / Download cancelled");
                    throw new IOException("下载已取消 / Download cancelled");
                }
            }

            TIPUAMod.LOGGER.info("ZIP下载完成: {} bytes / ZIP downloaded: {} bytes", totalRead, totalRead);
        } catch (IOException e) {
            handleDownloadError(e.getMessage());
            throw e;
        } finally {
            os.close();
            connection.disconnect();
        }

        return tempZip;
    }
    
    /**
     * 处理下载错误 - 显示错误并显示退出按钮
     */
    private static void handleDownloadError(String errorMessage) {
        isDownloading = false;
        TIPUAMod.LOGGER.error("下载错误: {} / Download error: {}", errorMessage, errorMessage);
        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setError(errorMessage);
                downloadScreen.showExitButton();
            }
        });
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

    private static void cancelDownload() {
        isDownloading = false;
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        TIPUAMod.LOGGER.info("下载已取消 / Download cancelled");
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

    private static String encodeUrlWithChinese(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            String path = uri.getPath();
            
            if (path != null && !path.isEmpty()) {
                String[] segments = path.split("/");
                StringBuilder encodedPath = new StringBuilder();
                for (String segment : segments) {
                    if (!segment.isEmpty()) {
                        String encodedSegment = java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
                            .replace("+", "%20");
                        encodedPath.append("/").append(encodedSegment);
                    }
                }
                path = encodedPath.toString();
            }
            
            String query = uri.getQuery();
            String fragment = uri.getFragment();
            
            return new URI(scheme, authority, path, query, fragment).toString();
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("URL编码失败，使用原始URL / Failed to encode URL, using original", e);
            return url;
        }
    }
}