package miao.byusi.mc.fabric.tipua.client;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import miao.byusi.mc.fabric.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.fabric.tipua.config.ClientConfig;
import miao.byusi.mc.fabric.tipua.util.HashUtil;
import miao.byusi.mc.fabric.tipua.util.ModrinthIndex;
import miao.byusi.mc.fabric.tipua.util.RollbackManager;
import miao.byusi.mc.fabric.tipua.util.VersionManager;
import miao.byusi.mc.fabric.tipua.client.ClientNetworkManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
            TIPUAMod.LOGGER.info("First run, skipping update check");
            VersionManager.markAsRun();
            return;
        }

        if (!ClientConfig.isAutoUpdate()) {
            TIPUAMod.LOGGER.info("Auto update disabled");
            return;
        }

        startUpdateCheck();
    }

    private static boolean isConnectedToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null;
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
                TIPUAMod.LOGGER.error("Update check failed", e);
                showToast("Update failed", "Error checking for updates");
                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.setError(e.getMessage());
                    }
                });
            }
        });
    }

    private static void doUpdateCheck() {
        // Use Fabric channel-based transfer: require an active server connection
        if (!isConnectedToServer()) {
            TIPUAMod.LOGGER.info("Not connected to a server, skipping update check");
            return;
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path modsDir = gameDir.resolve("mods");

        boolean modsExists = Files.exists(modsDir) && Files.isDirectory(modsDir);
        TIPUAMod.LOGGER.info("mods directory exists: {}", modsExists);

        byte[] indexBytes = ClientNetworkManager.requestIndex();
        String indexJson = (indexBytes == null) ? "" : new String(indexBytes);
        if (indexJson == null || indexJson.isEmpty()) {
            TIPUAMod.LOGGER.error("Failed to get index file from server");
            showToast("Update failed", "Failed to get index file");
            return;
        }
        TIPUAMod.LOGGER.info("Index file fetched from server");

        ModrinthIndex index = ModrinthIndex.fromJson(indexJson);
        if (index.getFiles().isEmpty()) {
            TIPUAMod.LOGGER.error("No files in index");
            showToast("Update failed", "Invalid index file");
            return;
        }

        if (modsExists) {
            TIPUAMod.LOGGER.info("mods directory exists, will clean extra files after download");
        } else {
            TIPUAMod.LOGGER.info("mods directory not found, will create and download all files");
        }

        previousVersion = VersionManager.getLocalVersion();

        // Prefer Konkrete mod UI if present
        if (FabricLoader.getInstance().isModLoaded("konkrete")) {
            TIPUAMod.LOGGER.info("Konkrete mod detected — UI handled by Konkrete");
        } else {
            showDownloadScreen(index.getVersionId());
        }

        startIndexDownload(index);
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
                downloadScreen.addLogEntry("info", "Starting modpack download...");
            }
        });
    }

    private static void startIndexDownload(ModrinthIndex index) {
        isDownloading = true;
        updateInProgress = true;

        Path gameDir = FabricLoader.getInstance().getGameDir();
        boolean backupSuccess = RollbackManager.backupBeforeUpdate(gameDir, pendingServerVersion);
        if (!backupSuccess) {
            TIPUAMod.LOGGER.warn("Failed to create rollback backup");
        }

        List<ModrinthIndex.FileEntry> files = index.getFiles();
        long totalSize = index.getTotalSize();
        long downloadedSize = 0;

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setFileProgress(0, files.size(), "");
            }
        });

        for (int i = 0; i < files.size(); i++) {
            if (!isDownloading) {
                TIPUAMod.LOGGER.info("Download cancelled");
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
                }
            });

            Path targetPath = gameDir.resolve(entry.path);

            try {
                if (Files.exists(targetPath)) {
                    boolean verified = HashUtil.verifyAll(targetPath.toFile(), entry.getSha1(), entry.getSha512());
                    if (verified) {
                        executeOnMainThread(() -> {
                            if (downloadScreen != null) {
                                downloadScreen.addLogEntry("info", "Skipping: " + currentFileName + " (already exists with matching hash)");
                            }
                        });
                        TIPUAMod.LOGGER.info("Skipping: {} (already exists with matching hash)", fileName);
                        downloadedSize += entry.fileSize;
                        continue;
                    } else {
                        executeOnMainThread(() -> {
                            if (downloadScreen != null) {
                                downloadScreen.addLogEntry("info", "Hash mismatch, re-downloading: " + currentFileName);
                            }
                        });
                        TIPUAMod.LOGGER.info("Hash mismatch, re-downloading: {}", fileName);
                    }
                }

                Files.createDirectories(targetPath.getParent());

                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.addLogEntry("info", "Downloading: " + currentFileName);
                    }
                });

                boolean downloaded = downloadFileWithRetry(entry, targetPath);
                if (!downloaded) {
                    handleUpdateFailure("Download failed: " + fileName, null);
                    return;
                }

                boolean verified = HashUtil.verifyAll(targetPath.toFile(), entry.getSha1(), entry.getSha512());
                if (!verified) {
                    handleUpdateFailure("Hash verification failed: " + fileName, null);
                    return;
                }

                downloadedSize += entry.fileSize;
                final long currentDownloadedSize = downloadedSize;
                final long totalDownloadSize = totalSize;
                final String completedFileName = fileName;

                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.addLogEntry("info", "Download completed: " + completedFileName);
                        downloadScreen.resetCurrentFileProgress();
                    }
                });

                TIPUAMod.LOGGER.info("File downloaded: {}", fileName);

            } catch (IOException e) {
                handleUpdateFailure("Download error: " + fileName, null);
                return;
            }
        }

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.addLogEntry("info", "All files downloaded, downloading data.zip if available");
            }
        });

        Path dataZipPath = gameDir.resolve(".tipua_data.zip");

        byte[] dataZipBytes = ClientNetworkManager.requestDataZip();
        if (dataZipBytes != null && dataZipBytes.length > 0) {
            try {
                Files.write(dataZipPath, dataZipBytes);
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Failed to write data.zip", e);
            }
        }

        if (Files.exists(dataZipPath)) {
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "Extracting data.zip...");
                }
            });
            if (!extractDataZip(dataZipPath, gameDir)) {
                handleUpdateFailure("data.zip extraction failed", dataZipPath);
                return;
            }
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "data.zip extraction completed");
                }
            });

            try {
                Files.delete(dataZipPath);
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("Failed to clean data.zip temp file");
            }
        } else {
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("info", "Server did not provide data.zip, skipping");
                }
            });
        }

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.addLogEntry("info", "Cleaning extra files...");
            }
        });
        cleanExtraFiles(gameDir, index.getFiles());

        // Save index's version id if present
        if (index.getVersionId() != null && !index.getVersionId().isEmpty()) {
            VersionManager.saveLocalVersion(index.getVersionId());
        }

        isDownloading = false;
        updateInProgress = false;

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.showRestartButton();
            }
        });

        showToast("Update completed", "Please restart to apply changes");
        TIPUAMod.LOGGER.info("Update completed");
    }

    private static boolean downloadFileWithRetry(ModrinthIndex.FileEntry entry, Path targetPath) {
        int maxRetries = ClientConfig.getMaxRetryAttempts();
        int retryDelay = ClientConfig.getRetryDelaySeconds();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                byte[] data = ClientNetworkManager.requestFile(entry.path);
                if (data != null && data.length > 0) {
                    Files.write(targetPath, data);
                    return true;
                }
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("Download failed for {}: {}", entry.path, e.getMessage());
            }

            if (attempt < maxRetries) {
                TIPUAMod.LOGGER.info("Retrying download: {}", attempt + 1);
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

    // HTTP-based helpers removed; data and files are transferred via Fabric networking (ClientNetworkManager)

    private static boolean extractDataZip(Path zipPath, Path targetDir) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                Path targetDirNormalized = targetDir.normalize();

                if (!entryPath.startsWith(targetDirNormalized)) {
                    TIPUAMod.LOGGER.warn("Skipping suspicious path: {}", entry.getName());
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            TIPUAMod.LOGGER.info("data.zip extraction completed");
            return true;

        } catch (IOException e) {
            TIPUAMod.LOGGER.error("data.zip extraction failed", e);
            return false;
        }
    }

    private static void handleUpdateFailure(String errorMessage, Path tempPath) {
        isDownloading = false;
        updateInProgress = false;

        TIPUAMod.LOGGER.error("Update failed: {}", errorMessage);

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.setError(errorMessage);

                Path gameDir = FabricLoader.getInstance().getGameDir();
                if (RollbackManager.hasRollbackBackup(gameDir)) {
                    downloadScreen.showRollbackButton();

                    if (ClientConfig.isAutoRollback()) {
                        performAutoRollback();
                    }
                } else {
                    downloadScreen.showExitButton();
                }
            }
        });

        showToast("Update failed", errorMessage);

        if (tempPath != null && Files.exists(tempPath)) {
            try {
                Files.delete(tempPath);
            } catch (IOException e) {
                TIPUAMod.LOGGER.warn("Failed to clean temp file", e);
            }
        }
    }

    private static void performAutoRollback() {
        TIPUAMod.LOGGER.info("Starting automatic rollback to version: {}", previousVersion);

        executeOnMainThread(() -> {
            if (downloadScreen != null) {
                downloadScreen.addLogEntry("info", String.format("[Auto Rollback] Rolling back to version %s", previousVersion));
            }
        });

        Path gameDir = FabricLoader.getInstance().getGameDir();
        boolean rollbackSuccess = RollbackManager.performRollback(gameDir);

        if (rollbackSuccess) {
            TIPUAMod.LOGGER.info("Automatic rollback successful");
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("success", String.format("[Rollback Success] Rolled back to version %s", previousVersion));
                    downloadScreen.showRestartButton();
                }
            });

            showToast("Auto-rollback successful", "Please restart to apply changes");
        } else {
            TIPUAMod.LOGGER.error("Automatic rollback failed");
            executeOnMainThread(() -> {
                if (downloadScreen != null) {
                    downloadScreen.addLogEntry("error", "[Rollback Failed] Automatic rollback failed");
                    downloadScreen.showExitButton();
                }
            });

            showToast("Auto-rollback failed", "Please manually execute /tipua rollback");
        }
    }

    private static void cancelDownload() {
        isDownloading = false;
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        TIPUAMod.LOGGER.info("Download cancelled");
    }

    // HTTP helper methods removed.

    private static void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                TIPUAMod.LOGGER.error("Failed to delete: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to delete directory: {}", directory, e);
        }
    }

    private static void cleanExtraFiles(Path gameDir, java.util.List<ModrinthIndex.FileEntry> expectedFiles) {
        java.util.Set<String> expectedPaths = new java.util.HashSet<>();
        for (ModrinthIndex.FileEntry entry : expectedFiles) {
            expectedPaths.add(entry.path);
        }

        Path modsDir = gameDir.resolve("mods");
        if (Files.exists(modsDir) && Files.isDirectory(modsDir)) {
            try {
                Files.walk(modsDir)
                        .filter(Files::isRegularFile)
                        .forEach(file -> {
                            String relativePath = gameDir.relativize(file).toString().replace("\\", "/");
                            if (!expectedPaths.contains(relativePath)) {
                                try {
                                    Files.delete(file);
                                    TIPUAMod.LOGGER.info("Deleted extra file: {}", relativePath);
                                    executeOnMainThread(() -> {
                                        if (downloadScreen != null) {
                                            downloadScreen.addLogEntry("info", "Deleted extra file: " + relativePath);
                                        }
                                    });
                                } catch (IOException e) {
                                    TIPUAMod.LOGGER.error("Failed to delete extra file: {}", relativePath, e);
                                }
                            }
                        });
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Failed to clean extra files", e);
            }
        }
    }

    private static void executeOnMainThread(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(runnable);
        }
    }

    private static void showToast(String title, String message) {
        TIPUAMod.LOGGER.info("[Notification] {} - {}", title, message);
    }
}