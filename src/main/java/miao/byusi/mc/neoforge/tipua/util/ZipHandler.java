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
    }

    /**
     * 解压ZIP文件到游戏目录（带进度回调）
     * @param zipFile ZIP文件
     * @param callback 进度回调
     * @return 是否成功解压
     */
    public static boolean extractZip(File zipFile, ExtractionProgressCallback callback) {
        if (!zipFile.exists()) {
            TIPUAMod.LOGGER.error("ZIP文件不存在: {}", zipFile.getAbsolutePath());
            if (callback != null) callback.onError("ZIP文件不存在");
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
                        Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            if (!validateZipStructure(tempDir)) {
                String error = "ZIP结构无效：必须包含config和mods目录";
                TIPUAMod.LOGGER.error(error);
                if (callback != null) callback.onError(error);
                deleteDirectory(tempDir);
                return false;
            }

            mergeToGameDirectory(tempDir, gameDir);

            deleteDirectory(tempDir);

            if (callback != null) callback.onComplete();
            TIPUAMod.LOGGER.info("ZIP解压完成 / ZIP extraction completed");
            return true;

        } catch (Exception e) {
            TIPUAMod.LOGGER.error("ZIP解压失败 / ZIP extraction failed", e);
            if (callback != null) callback.onError(e.getMessage());
            try {
                deleteDirectory(tempDir);
            } catch (Exception ignored) {
            }
            return false;
        }
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

    private static void mergeToGameDirectory(Path tempDir, Path gameDir) throws IOException {
        Files.list(tempDir).forEach(source -> {
            try {
                String name = source.getFileName().toString();
                Path target = gameDir.resolve(name);

                if (name.equals("config") || name.equals("mods")) {
                    mergeDirectory(source, target);
                } else {
                    if (Files.exists(target)) {
                        if (Files.isDirectory(target)) {
                            deleteDirectory(target);
                        } else {
                            Files.delete(target);
                        }
                    }
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("合并文件失败: {}", source.getFileName(), e);
            }
        });
    }

    private static void mergeDirectory(Path source, Path target) throws IOException {
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
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("合并文件失败: {}", sourcePath, e);
            }
        });
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
