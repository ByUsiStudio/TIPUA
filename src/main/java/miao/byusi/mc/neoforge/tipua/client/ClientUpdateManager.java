package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.neoforge.tipua.client.gui.UpdatePreviewScreen;
import miao.byusi.mc.neoforge.tipua.config.ClientConfig;
import miao.byusi.mc.neoforge.tipua.util.DetailedErrorHandler;
import miao.byusi.mc.neoforge.tipua.util.ModpackManifest;
import miao.byusi.mc.neoforge.tipua.util.MultiThreadDownloader;
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
    private static boolean updateInProgress = false;  // 标记更新是否正在进行
    private static String previousVersion = "0.0.0";  // 记录更新前的版本

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
        
        isDownloading = true;
        
        // 在预下载前先显示下载界面，让用户能看到下载进度
        final String serverVerForScreen = serverVersion;
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.screen != null) {
                downloadScreen = new ModpackDownloadScreen(minecraft.screen, serverVerForScreen, () -> {
                    cancelDownload();
                }, () -> {
                    performAutoRollback();
                });
                minecraft.setScreen(downloadScreen);
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "正在预下载整合包以获取更新预览... / Pre-downloading modpack for preview...");
                }
            }
        });
        
        String encodedUrl = encodeUrlWithChinese(downloadUrl);
        try {
            TIPUAMod.LOGGER.info("预下载ZIP以获取manifest / Pre-downloading ZIP to get manifest");
            
            // 使用增强的多线程下载器，支持重试
            miao.byusi.mc.neoforge.tipua.util.EnhancedMultiThreadDownloader enhancedDownloader = 
                new miao.byusi.mc.neoforge.tipua.util.EnhancedMultiThreadDownloader(
                    new URL(encodedUrl), 
                    tempZip, 
                    ClientConfig.getDownloadTimeoutSeconds(),
                    4,  // 线程数
                    ClientConfig.getMaxRetryAttempts(),  // 最大重试次数
                    ClientConfig.getRetryDelaySeconds(),  // 重试延迟
                    (downloaded, total, speed) -> {
                        // 预下载时的进度回调 - 推送到下载界面
                        executeOnMainThread(() -> {
                            if (downloadScreen != null) {
                                downloadScreen.updateDownloadProgress(downloaded, total);
                            }
                        });
                    },
                    status -> {
                        // 状态变化回调
                        TIPUAMod.LOGGER.info("下载状态: {} / Download status: {}", status.getChineseStatus(), status.getEnglishStatus());
                        executeOnMainThread(() -> {
                            if (downloadScreen != null) {
                                downloadScreen.addLogEntry("info", "[" + status.getChineseStatus() + " / " + status.getEnglishStatus() + "]");
                            }
                        });
                    }
                );
            
            enhancedDownloader.addStatusListener(status -> {
                // 可以添加更多的状态监听逻辑
            });
            
            tempZip = enhancedDownloader.downloadWithRetry();
            
        } catch (Exception e) {
            isDownloading = false;
            
            // 获取详细的错误信息
            DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetail(e);
            TIPUAMod.LOGGER.error("预下载失败 / Pre-download failed: {}", errorDetail.getErrorMessage(), e);
            
            showToast("更新失败 / Update failed", errorDetail.getErrorMessage());
            
            // 显示详细的错误信息和解决方案
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.setError(errorDetail.getFormattedError());
                    downloadScreen.showExitButton();
                    
                    // 如果错误详情支持回滚，显示回滚按钮
                    if (errorDetail.canRollback() && RollbackManager.hasRollbackBackup(FMLPaths.GAMEDIR.get())) {
                        downloadScreen.showRollbackButton();
                    }
                }
            });
            return;
        }
        
        isDownloading = false;
        
        ModpackManifest manifest = ModpackManifest.fromZip(tempZip.toFile());
        
        final String finalLocalVersion = localVersion;
        final Path finalTempZip = tempZip;
        
        // 预下载完成后，关闭下载界面，显示更新预览界面
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
        updateInProgress = true;
        final String finalServerVersion = pendingServerVersion;
        
        // 记录更新前的版本
        previousVersion = VersionManager.getLocalVersion();
        TIPUAMod.LOGGER.info("记录更新前的版本: {} / Recording version before update: {}", previousVersion, previousVersion);
        
        // 由于 ModpackDownloadScreen 已在预下载阶段创建，这里只需将界面切回它即可
        executeOnMainThread(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && downloadScreen != null) {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "用户已确认更新，开始解压 / User confirmed, starting extraction");
                }
                minecraft.setScreen(downloadScreen);
            } else if (minecraft != null && minecraft.screen != null) {
                // 兜底：若界面因异常丢失，则重新创建
                downloadScreen = new ModpackDownloadScreen(minecraft.screen, finalServerVersion, () -> {
                    cancelDownload();
                }, () -> {
                    performAutoRollback();
                });
                minecraft.setScreen(downloadScreen);
            }
        });
        
        if (ClientConfig.isAutoExtract()) {
            TIPUAMod.LOGGER.info("开始解压ZIP / Extracting ZIP");
            
            // 在解压前创建备份
            boolean backupSuccess = RollbackManager.backupBeforeUpdate(FMLPaths.GAMEDIR.get(), finalServerVersion);
            if (!backupSuccess) {
                TIPUAMod.LOGGER.warn("创建回滚备份失败，更新失败时将无法自动回滚 / Failed to create rollback backup, auto-rollback will not be available on update failure");
            }
            
            // 使用智能冲突检测的解压
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
                    updateInProgress = false;
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.setExtractionComplete();
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    TIPUAMod.LOGGER.error("解压失败 / Extraction failed: {}", error);
                    updateInProgress = false;
                    handleUpdateFailure("解压失败: " + error, tempZip);
                }

                @Override
                public void onConflictDetected(String conflictFile, ZipHandler.ConflictResolution resolution) {
                    TIPUAMod.LOGGER.info("检测到文件冲突: {}, 解决方案: {} / File conflict detected: {}, resolution: {}", 
                        conflictFile, resolution.getChineseName(), conflictFile, resolution.getChineseName());
                    
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.addLogEntry("warn", String.format("[冲突/Conflict] %s -> %s", conflictFile, resolution.getChineseName()));
                        }
                    });
                }

                @Override
                public void onDetailedError(DetailedErrorHandler.ErrorDetail errorDetail) {
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.setError(errorDetail.getFormattedError());
                            
                            // 如果错误详情支持回滚且配置了自动回滚
                            if (errorDetail.canRollback() && RollbackManager.hasRollbackBackup(FMLPaths.GAMEDIR.get())) {
                                downloadScreen.showRollbackButton();
                                
                                // 可选：自动回滚（根据配置决定）
                                if (ClientConfig.isAutoRollback()) {
                                    TIPUAMod.LOGGER.info("执行自动回滚 / Performing automatic rollback");
                                    performAutoRollback();
                                }
                            } else {
                                downloadScreen.showExitButton();
                            }
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
    
    /**
     * 处理更新失败
     */
    private static void handleUpdateFailure(String errorMessage, Path tempZip) {
        isDownloading = false;
        updateInProgress = false;
        
        TIPUAMod.LOGGER.error("更新失败: {} / Update failed: {}", errorMessage, errorMessage);
        
        // 获取详细的错误信息
        DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetailById("extraction_failed");
        
        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setError(errorDetail.getFormattedError());
                
                // 检查是否可以回滚
                if (errorDetail.canRollback() && RollbackManager.hasRollbackBackup(FMLPaths.GAMEDIR.get())) {
                    downloadScreen.showRollbackButton();
                    
                    // 如果配置了自动回滚，执行自动回滚
                    if (ClientConfig.isAutoRollback()) {
                        performAutoRollback();
                    }
                } else {
                    downloadScreen.showExitButton();
                }
            }
        });
        
        showToast("更新失败 / Update failed", errorMessage);
        
        // 清理临时文件
        try {
            if (tempZip != null && Files.exists(tempZip)) {
                Files.delete(tempZip);
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理临时文件失败 / Failed to clean temp file", e);
        }
    }
    
    /**
     * 执行自动回滚
     */
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
                    downloadScreen.addLogEntry("error", "[回滚失败/Rollback Failed] 自动回滚失败，请手动执行 /tipua rollback");
                    downloadScreen.showExitButton();
                }
            });
            
            showToast("自动回滚失败 / Auto-rollback failed", "请手动执行 /tipua rollback");
        }
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

    private static MultiThreadDownloader currentDownloader;

    /**
     * 使用多线程下载ZIP文件
     */
    private static Path downloadZipWithResume(URL url, String version) throws IOException {
        Path tempZip = FMLPaths.GAMEDIR.get().resolve(".tipua_temp_download.zip");
        
        if (Files.exists(tempZip)) {
            Files.delete(tempZip);
        }

        currentDownloader = new MultiThreadDownloader(url, tempZip, 
                ClientConfig.getDownloadTimeoutSeconds(), 4, 
                ClientConfig.getMaxRetryAttempts(),
                ClientConfig.getRetryDelaySeconds(),
                (downloaded, total) -> {
                    executeOnMainThread(() -> {
                        if (downloadScreen != null) {
                            downloadScreen.updateDownloadProgress(downloaded, total);
                        }
                    });
                });

        try {
            return currentDownloader.download();
        } finally {
            currentDownloader = null;
        }
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
        if (currentDownloader != null) {
            currentDownloader.cancel();
        }
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