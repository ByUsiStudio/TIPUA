package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP处理器 - 处理ZIP压缩包的解压操作
 * 必须包含config和mods目录，其他文件默认替换
 */
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

    /**
     * 解压ZIP文件到游戏目录（带进度回调和冲突处理）
     * @param zipFile ZIP文件
     * @param callback 进度回调
     * @param conflictResolution 冲突解决策略
     * @return 是否成功解压
     */
    public static boolean extractZip(File zipFile, ExtractionProgressCallback callback, ConflictResolution conflictResolution) {
        if (!zipFile.exists()) {
            DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetailById("file_not_found");
            TIPUAMod.LOGGER.error("ZIP文件不存在: {}", zipFile.getAbsolutePath());
            if (callback != null) {
                callback.onError("ZIP文件不存在");
                callback.onDetailedError(errorDetail);
            }
            return false;
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        Path tempDir = gameDir.resolve(".tipua_temp");

        try {
            if (Files.exists(tempDir)) {
                deleteDirectory(tempDir);
            }
            Files.createDirectories(tempDir);

            long totalEntries = countZipEntries(zipFile);
            long processedEntries = 0;
            TIPUAMod.LOGGER.info("ZIP文件包含 {} 个条目 / ZIP file contains {} entries", totalEntries, totalEntries);

            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = tempDir.resolve(entry.getName());

                    if (!entryPath.normalize().startsWith(tempDir.normalize())) {
                        TIPUAMod.LOGGER.warn("跳过可疑路径: {}", entry.getName());
                        zis.closeEntry();
                        continue;
                    }

                    if (!entry.isDirectory()) {
                        processedEntries++;
                        if (callback != null) {
                            callback.onFileExtracting(entry.getName(), processedEntries, totalEntries);
                        }
                        TIPUAMod.LOGGER.debug("解压文件: {} / Extracting: {}", entry.getName(), entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        
                        // 检查文件冲突
                        Path targetPath = gameDir.resolve(entry.getName());
                        if (Files.exists(targetPath) && conflictResolution != ConflictResolution.OVERWRITE) {
                            ConflictResolution resolution = resolveConflict(entry.getName(), targetPath, entryPath, conflictResolution, callback);
                            if (resolution == ConflictResolution.ABORT) {
                                throw new IOException("解压因文件冲突而中止 / Extraction aborted due to file conflict");
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
            TIPUAMod.LOGGER.info("ZIP解压完成 / ZIP extraction completed");
            return true;

        } catch (Exception e) {
            DetailedErrorHandler.ErrorDetail errorDetail = DetailedErrorHandler.getErrorDetail(e);
            TIPUAMod.LOGGER.error("ZIP解压失败 / ZIP extraction failed", e);
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

    /**
     * 解压ZIP文件到游戏目录（带进度回调，默认冲突策略为覆盖）
     * @param zipFile ZIP文件
     * @param callback 进度回调
     * @return 是否成功解压
     */
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

    /**
     * 智能解决文件冲突
     */
    private static ConflictResolution resolveConflict(String fileName, Path existingFile, Path newFile, 
                                                      ConflictResolution defaultResolution, ExtractionProgressCallback callback) throws IOException {
        // 使用默认策略
        ConflictResolution resolution = defaultResolution;
        
        // 通知回调有冲突发生
        if (callback != null) {
            callback.onConflictDetected(fileName, resolution);
        }
        
        // 根据策略处理冲突
        switch (resolution) {
            case BACKUP:
                // 备份现有文件
                Path backupPath = existingFile.resolveSibling(existingFile.getFileName() + ".tipua_backup");
                Files.move(existingFile, backupPath, StandardCopyOption.REPLACE_EXISTING);
                TIPUAMod.LOGGER.info("备份冲突文件: {} -> {} / Backup conflict file: {} -> {}", 
                    existingFile, backupPath, existingFile, backupPath);
                break;
                
            case RENAME:
                // 重命名新文件
                String baseName = newFile.getFileName().toString();
                String nameWithoutExt = baseName.contains(".") ? 
                    baseName.substring(0, baseName.lastIndexOf('.')) : baseName;
                String ext = baseName.contains(".") ? 
                    baseName.substring(baseName.lastIndexOf('.')) : "";
                
                Path renamedFile = newFile.resolveSibling(nameWithoutExt + "_tipua_new" + ext);
                Files.move(newFile, renamedFile);
                TIPUAMod.LOGGER.info("重命名冲突文件: {} -> {} / Rename conflict file: {} -> {}", 
                    newFile, renamedFile, newFile, renamedFile);
                break;
                
            case SKIP:
                TIPUAMod.LOGGER.info("跳过冲突文件: {} / Skip conflict file: {}", existingFile, existingFile);
                break;
                
            case ABORT:
                TIPUAMod.LOGGER.warn("中止解压，因冲突文件: {} / Abort extraction due to conflict file: {}", 
                    existingFile, existingFile);
                break;
                
            case OVERWRITE:
            default:
                TIPUAMod.LOGGER.debug("覆盖冲突文件: {} / Overwrite conflict file: {}", existingFile, existingFile);
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
                            throw new RuntimeException("合并config目录失败: " + source, e);
                        }
                    } else if (name.equals("mods")) {
                        if (Files.exists(target)) {
                            TIPUAMod.LOGGER.info("清空mods文件夹 / Clearing mods folder: {}", target);
                            deleteDirectory(target);
                        }
                        Files.createDirectories(target);
                        copyDirectory(source, target);
                        TIPUAMod.LOGGER.info("完成mods文件夹更新 / Completed mods folder update");
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
                    TIPUAMod.LOGGER.error("合并文件失败: {}", source.getFileName(), e);
                    if (callback != null) {
                        callback.onError("合并文件失败: " + source.getFileName() + " / Failed to merge file: " + source.getFileName());
                    }
                }
            });
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("遍历临时目录失败: {}", tempDir, e);
            if (callback != null) {
                callback.onError("遍历临时目录失败: " + tempDir + " / Failed to iterate temp directory: " + tempDir);
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
                            throw new IOException("解压因文件冲突而中止 / Extraction aborted due to file conflict");
                        } else if (resolution == ConflictResolution.SKIP) {
                            return;
                        }
                    }
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("合并文件失败: {}", sourcePath, e);
                if (callback != null) {
                    callback.onError("合并文件失败: " + sourcePath + " / Failed to merge file: " + sourcePath);
                }
            }
        });
    }
    
    /**
     * 保持向后兼容的mergeToGameDirectory方法
     */
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
                            TIPUAMod.LOGGER.error("删除失败: {}", path, e);
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
                TIPUAMod.LOGGER.error("复制文件失败: {}", sourcePath, e);
                throw new RuntimeException("复制文件失败: " + sourcePath, e);
            }
        });
    }

    /**
     * 验证ZIP结构是否包含必要的目录
     * @param tempDir 临时解压目录
     * @return 是否有效
     */
    private static boolean validateZipStructure(Path tempDir) {
        Path configDir = tempDir.resolve("config");
        Path modsDir = tempDir.resolve("mods");

        boolean hasConfig = Files.exists(configDir) && Files.isDirectory(configDir);
        boolean hasMods = Files.exists(modsDir) && Files.isDirectory(modsDir);

        TIPUAMod.LOGGER.info("ZIP结构验证: config={}, mods={} / ZIP structure validation: config={}, mods={}", 
                hasConfig, hasMods, hasConfig, hasMods);

        return hasConfig && hasMods;
    }
}
