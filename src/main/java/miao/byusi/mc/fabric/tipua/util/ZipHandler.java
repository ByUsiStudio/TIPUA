package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipHandler {

    public interface ExtractionProgressCallback {
        void onFileExtracting(String relativePath, long current, long total);
        void onComplete();
        void onError(String error);
        void onConflictDetected(String conflictFile, ConflictResolution resolution);
        void onDetailedError(DetailedErrorHandler.ErrorDetail errorDetail);
    }
    
    public enum ConflictResolution {
        OVERWRITE("覆盖", "Overwrite"),
        SKIP("跳过", "Skip"),
        RENAME("重命名", "Rename"),
        BACKUP("备份并覆盖", "Backup and overwrite"),
        ABORT("中止解压", "Abort extraction");
        
        private final String chineseName;
        private final String englishName;
        
        ConflictResolution(String chineseName, String englishName) {
            this.chineseName = chineseName;
            this.englishName = englishName;
        }
        
        public String getChineseName() {
            return chineseName;
        }
        
        public String getEnglishName() {
            return englishName;
        }
    }

    public static boolean extractZip(File zipFile, ExtractionProgressCallback callback, ConflictResolution conflictResolution) {
        if (!zipFile.exists()) {
            DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetailById("file_not_found");
            TIPUAMod.LOGGER.error("ZIP file not found: {}", zipFile.getAbsolutePath());
            if (callback != null) {
                callback.onError("ZIP文件不存在");
                callback.onDetailedError(errorDetail);
            }
            return false;
        }

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path tempDir = gameDir.resolve(".tipua_temp");

        try {
            if (Files.exists(tempDir)) {
                deleteDirectory(tempDir);
            }
            Files.createDirectories(tempDir);

            long totalEntries = countZipEntries(zipFile);
            long processedEntries = 0;
            TIPUAMod.LOGGER.info("ZIP file contains {} entries", totalEntries);

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = tempDir.resolve(entry.getName());

                    if (!entryPath.normalize().startsWith(tempDir.normalize())) {
                        TIPUAMod.LOGGER.warn("Skipping suspicious path: {}", entry.getName());
                        zis.closeEntry();
                        continue;
                    }

                    if (!entry.isDirectory()) {
                        processedEntries++;
                        if (callback != null) {
                            callback.onFileExtracting(entry.getName(), processedEntries, totalEntries);
                        }
                        TIPUAMod.LOGGER.debug("Extracting: {}", entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        
                        Path targetPath = gameDir.resolve(entry.getName());
                        if (Files.exists(targetPath) && conflictResolution != ConflictResolution.OVERWRITE) {
                            ConflictResolution resolution = resolveConflict(entry.getName(), targetPath, entryPath, conflictResolution, callback);
                            if (resolution == ConflictResolution.ABORT) {
                                throw new IOException("Extraction aborted due to file conflict");
                            } else if (resolution == ConflictResolution.SKIP) {
                                zis.closeEntry();
                                continue;
                            }
                        }
                        
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            if (!validateZipStructure(tempDir)) {
                DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.analyzeZipError(
                    Files.exists(tempDir.resolve("config")), 
                    Files.exists(tempDir.resolve("mods"))
                );
                TIPUAMod.LOGGER.error(errorDetail.getErrorMessage());
                if (callback != null) {
                    callback.onError(errorDetail.getErrorMessage());
                    callback.onDetailedError(errorDetail);
                }
                deleteDirectory(tempDir);
                return false;
            }

            mergeToGameDirectory(tempDir, gameDir, conflictResolution, callback);

            deleteDirectory(tempDir);

            if (callback != null) callback.onComplete();
            TIPUAMod.LOGGER.info("ZIP extraction completed");
            return true;

        } catch (Exception e) {
            DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetail(e);
            TIPUAMod.LOGGER.error("ZIP extraction failed", e);
            if (callback != null) {
                callback.onError(e.getMessage());
                callback.onDetailedError(errorDetail);
            }
            try {
                deleteDirectory(tempDir);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    public static boolean extractZip(File zipFile, ExtractionProgressCallback callback) {
        return extractZip(zipFile, callback, ConflictResolution.OVERWRITE);
    }

    private static long countZipEntries(File zipFile) throws IOException {
        long count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            while (zis.getNextEntry() != null) {
                count++;
                zis.closeEntry();
            }
        }
        return count;
    }

    private static ConflictResolution resolveConflict(String fileName, Path existingFile, Path newFile, 
                                                      ConflictResolution defaultResolution, ExtractionProgressCallback callback) throws IOException {
        ConflictResolution resolution = defaultResolution;
        
        if (callback != null) {
            callback.onConflictDetected(fileName, resolution);
        }
        
        switch (resolution) {
            case BACKUP:
                Path backupPath = existingFile.resolveSibling(existingFile.getFileName() + ".tipua_backup");
                Files.move(existingFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
                TIPUAMod.LOGGER.info("Backup conflict file: {} -> {}", existingFile, backupPath);
                break;
                
            case RENAME:
                String baseName = newFile.getFileName().toString();
                String nameWithoutExt = baseName.contains(".") ? 
                    baseName.substring(0, baseName.lastIndexOf('.')) : baseName;
                String ext = baseName.contains(".") ? 
                    baseName.substring(baseName.lastIndexOf('.')) : "";
                
                Path renamedFile = newFile.resolveSibling(nameWithoutExt + "_tipua_new" + ext);
                Files.move(newFile, renamedFile);
                TIPUAMod.LOGGER.info("Rename conflict file: {} -> {}", newFile, renamedFile);
                break;
                
            case SKIP:
                TIPUAMod.LOGGER.info("Skip conflict file: {}", existingFile);
                break;
                
            case ABORT:
                TIPUAMod.LOGGER.warn("Abort extraction due to conflict file: {}", existingFile);
                break;
                
            case OVERWRITE:
            default:
                TIPUAMod.LOGGER.debug("Overwrite conflict file: {}", existingFile);
                break;
        }
        
        return resolution;
    }

    private static void mergeToGameDirectory(Path tempDir, Path gameDir, ConflictResolution conflictResolution, ExtractionProgressCallback callback) {
        try {
            Files.list(tempDir).forEach(source -> {
                try {
                    String name = source.getFileName().toString();
                    Path target = gameDir.resolve(name);

                    if (name.equals("config")) {
                        try {
                            mergeDirectory(source, target, conflictResolution, callback);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to merge config directory: " + source, e);
                        }
                    } else if (name.equals("mods")) {
                        if (Files.exists(target)) {
                            TIPUAMod.LOGGER.info("Clearing mods folder: {}", target);
                            deleteDirectory(target);
                        }
                        Files.createDirectories(target);
                        copyDirectory(source, target);
                        TIPUAMod.LOGGER.info("Completed mods folder update");
                    } else {
                        if (Files.exists(target)) {
                            if (Files.isDirectory(target)) {
                                deleteDirectory(target);
                            } else {
                                resolveConflict(name, target, source, conflictResolution, callback);
                            }
                        }
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    TIPUAMod.LOGGER.error("Failed to merge file: {}", source.getFileName(), e);
                    if (callback != null) {
                        callback.onError("Failed to merge file: " + source.getFileName());
                    }
                }
            });
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to iterate temp directory: {}", tempDir, e);
            if (callback != null) {
                callback.onError("Failed to iterate temp directory: " + tempDir);
            }
        }
    }

    private static void mergeDirectory(Path source, Path target, ConflictResolution conflictResolution, ExtractionProgressCallback callback) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        Files.walk(source).forEach(sourcePath -> {
            try {
                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath);

                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    if (Files.exists(targetPath) && conflictResolution != ConflictResolution.OVERWRITE) {
                        ConflictResolution resolution = resolveConflict(
                            targetPath.getFileName().toString(), 
                            targetPath, 
                            sourcePath, 
                            conflictResolution, 
                            callback
                        );
                        
                        if (resolution == ConflictResolution.ABORT) {
                            throw new IOException("Extraction aborted due to file conflict");
                        } else if (resolution == ConflictResolution.SKIP) {
                            return;
                        }
                    }
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("Failed to merge file: {}", sourcePath, e);
                if (callback != null) {
                    callback.onError("Failed to merge file: " + sourcePath);
                }
            }
        });
    }
    
    private static void mergeToGameDirectory(Path tempDir, Path gameDir) throws IOException {
        mergeToGameDirectory(tempDir, gameDir, ConflictResolution.OVERWRITE, null);
    }

    private static void deleteDirectory(Path directory) throws IOException {
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
    }
    
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path relativePath = source.relativize(sourcePath);
                Path targetPath = target.resolve(relativePath);
                
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Failed to copy file: {}", sourcePath, e);
                throw new RuntimeException("Failed to copy file: " + sourcePath, e);
            }
        });
    }

    private static boolean validateZipStructure(Path tempDir) {
        Path configDir = tempDir.resolve("config");
        Path modsDir = tempDir.resolve("mods");

        boolean hasConfig = Files.exists(configDir) && Files.isDirectory(configDir);
        boolean hasMods = Files.exists(modsDir) && Files.isDirectory(modsDir);

        TIPUAMod.LOGGER.info("ZIP structure validation: config={}, mods={}", hasConfig, hasMods);

        return hasConfig && hasMods;
    }
}