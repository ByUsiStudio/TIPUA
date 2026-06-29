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

    /**
     * 解压ZIP文件到游戏目录
     * @param zipFile ZIP文件
     * @return 是否成功解压
     */
    public static boolean extractZip(File zipFile) {
        if (!zipFile.exists()) {
            TIPUAMod.LOGGER.error("ZIP文件不存在: {}", zipFile.getAbsolutePath());
            return false;
        }

        Path gameDir = FMLPaths.GAMEDIR.get();
        Path tempDir = gameDir.resolve(".tipua_temp");

        try {
            // 创建临时目录
            if (Files.exists(tempDir)) {
                deleteDirectory(tempDir);
            }
            Files.createDirectories(tempDir);

            // 解压到临时目录
            unzipToDirectory(zipFile, tempDir);

            // 验证ZIP结构
            if (!validateZipStructure(tempDir)) {
                TIPUAMod.LOGGER.error("ZIP结构无效：必须包含config和mods目录");
                deleteDirectory(tempDir);
                return false;
            }

            // 合并到游戏目录
            mergeToGameDirectory(tempDir, gameDir);

            // 清理临时目录
            deleteDirectory(tempDir);

            TIPUAMod.LOGGER.info("ZIP解压完成 / ZIP extraction completed");
            return true;

        } catch (Exception e) {
            TIPUAMod.LOGGER.error("ZIP解压失败 / ZIP extraction failed", e);
            try {
                deleteDirectory(tempDir);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    /**
     * 解压ZIP到指定目录
     */
    private static void unzipToDirectory(File zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                // 防止路径遍历攻击
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
        }
    }

    /**
     * 验证ZIP结构：必须包含config和mods目录
     */
    private static boolean validateZipStructure(Path tempDir) {
        boolean hasConfig = Files.exists(tempDir.resolve("config")) && Files.isDirectory(tempDir.resolve("config"));
        boolean hasMods = Files.exists(tempDir.resolve("mods")) && Files.isDirectory(tempDir.resolve("mods"));

        return hasConfig && hasMods;
    }

    /**
     * 合并临时目录到游戏目录
     * config和mods目录：合并（保留现有文件，添加新文件）
     * 其他文件：替换
     */
    private static void mergeToGameDirectory(Path tempDir, Path gameDir) throws IOException {
        Files.list(tempDir).forEach(source -> {
            try {
                String name = source.getFileName().toString();
                Path target = gameDir.resolve(name);

                // config和mods目录：合并
                if (name.equals("config") || name.equals("mods")) {
                    mergeDirectory(source, target);
                } else {
                    // 其他文件：替换
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

    /**
     * 合并目录（保留现有文件，添加新文件，更新同名文件）
     */
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

    /**
     * 删除目录及其内容
     */
    private static void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a)) // 先删除文件，再删除目录
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            TIPUAMod.LOGGER.error("删除失败: {}", path, e);
                        }
                    });
        }
    }
}