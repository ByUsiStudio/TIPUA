package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * 回滚管理工具类
 * 在更新前备份文件清单，支持一键回滚到上一版本
 */
public class RollbackManager {
    private static final String ROLLBACK_DIR = ".tipua_rollback";
    private static final String FILE_MANIFEST = "file_manifest.json";
    private static final String VERSION_FILE = "rollback_version.txt";
    
    /**
     * 在解压前备份当前文件清单
     * @param gameDir 游戏目录
     * @param targetVersion 目标版本（即将更新到的版本）
     * @return 是否备份成功
     */
    public static boolean backupBeforeUpdate(Path gameDir, String targetVersion) {
        Path rollbackDir = gameDir.resolve(ROLLBACK_DIR);
        
        try {
            if (!Files.exists(rollbackDir)) {
                Files.createDirectories(rollbackDir);
            }
            
            String currentVersion = VersionManager.getLocalVersion();
            if ("0.0.0".equals(currentVersion)) {
                TIPUAMod.LOGGER.info("首次更新，无需备份 / First update, no backup needed");
                return true;
            }
            
            Map<String, String> fileManifest = scanFiles(gameDir);
            
            Path manifestFile = rollbackDir.resolve(FILE_MANIFEST);
            try (BufferedWriter writer = Files.newBufferedWriter(manifestFile)) {
                StringBuilder json = new StringBuilder();
                json.append("{\n");
                json.append("  \"version\": \"").append(currentVersion).append("\",\n");
                json.append("  \"targetVersion\": \"").append(targetVersion).append("\",\n");
                json.append("  \"backupTime\": ").append(System.currentTimeMillis()).append(",\n");
                json.append("  \"files\": {\n");
                
                List<String> paths = new ArrayList<>(fileManifest.keySet());
                Collections.sort(paths);
                
                for (int i = 0; i < paths.size(); i++) {
                    String path = paths.get(i);
                    String hash = fileManifest.get(path);
                    json.append("    \"").append(path).append("\": \"").append(hash).append("\"");
                    if (i < paths.size() - 1) {
                        json.append(",");
                    }
                    json.append("\n");
                }
                
                json.append("  }\n");
                json.append("}");
                
                writer.write(json.toString());
            }
            
            Path versionFile = rollbackDir.resolve(VERSION_FILE);
            Files.write(versionFile, Collections.singletonList(currentVersion));
            
            TIPUAMod.LOGGER.info("回滚备份已创建，当前版本: {} / Rollback backup created, current version: {}", currentVersion, currentVersion);
            return true;
            
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("创建回滚备份失败 / Failed to create rollback backup", e);
            return false;
        }
    }
    
    /**
     * 扫描游戏目录中的关键文件（config和mods）
     */
    private static Map<String, String> scanFiles(Path gameDir) {
        Map<String, String> manifest = new HashMap<>();
        
        try {
            Path configDir = gameDir.resolve("config");
            Path modsDir = gameDir.resolve("mods");
            
            if (Files.exists(configDir)) {
                scanDirectory(configDir, gameDir, manifest);
            }
            
            if (Files.exists(modsDir)) {
                scanDirectory(modsDir, gameDir, manifest);
            }
            
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("扫描文件失败 / Failed to scan files", e);
        }
        
        return manifest;
    }
    
    private static void scanDirectory(Path dir, Path baseDir, Map<String, String> manifest) throws IOException {
        if (!Files.exists(dir)) return;
        
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relativePath = baseDir.relativize(path).toString().replace("\\", "/");
                        String hash = calculateSimpleHash(path);
                        manifest.put(relativePath, hash);
                    } catch (IOException e) {
                        TIPUAMod.LOGGER.warn("无法读取文件: {} / Cannot read file: {}", path, path);
                    }
                });
        }
    }
    
    /**
     * 计算简单的文件哈希（用于检测变化）
     */
    private static String calculateSimpleHash(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 检查是否有可用的回滚备份
     */
    public static boolean hasRollbackBackup(Path gameDir) {
        Path rollbackDir = gameDir.resolve(ROLLBACK_DIR);
        Path versionFile = rollbackDir.resolve(VERSION_FILE);
        return Files.exists(rollbackDir) && Files.exists(versionFile);
    }
    
    /**
     * 获取回滚版本信息
     */
    public static String getRollbackVersion(Path gameDir) {
        Path rollbackDir = gameDir.resolve(ROLLBACK_DIR);
        Path versionFile = rollbackDir.resolve(VERSION_FILE);
        
        try {
            if (Files.exists(versionFile)) {
                List<String> lines = Files.readAllLines(versionFile);
                if (!lines.isEmpty()) {
                    return lines.get(0).trim();
                }
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("读取回滚版本失败 / Failed to read rollback version", e);
        }
        
        return null;
    }
    
    /**
     * 执行回滚操作
     * @param gameDir 游戏目录
     * @return 是否回滚成功
     */
    public static boolean performRollback(Path gameDir) {
        Path rollbackDir = gameDir.resolve(ROLLBACK_DIR);
        Path manifestFile = rollbackDir.resolve(FILE_MANIFEST);
        Path versionFile = rollbackDir.resolve(VERSION_FILE);
        
        if (!Files.exists(manifestFile) || !Files.exists(versionFile)) {
            TIPUAMod.LOGGER.error("回滚备份不存在 / Rollback backup does not exist");
            return false;
        }
        
        try {
            String rollbackVersion = Files.readAllLines(versionFile).get(0).trim();
            
            String manifestContent = new String(Files.readAllBytes(manifestFile));
            
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\":\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(manifestContent);
            if (!matcher.find()) {
                TIPUAMod.LOGGER.error("无法解析回滚版本 / Cannot parse rollback version");
                return false;
            }
            String targetVersion = matcher.group(1);
            
            TIPUAMod.LOGGER.info("开始回滚到版本: {} / Starting rollback to version: {}", targetVersion, targetVersion);
            
            Set<String> rollbackFiles = new HashSet<>();
            pattern = java.util.regex.Pattern.compile("\"files\":\\s*\\{(.*?)\\}", java.util.regex.Pattern.DOTALL);
            matcher = pattern.matcher(manifestContent);
            if (matcher.find()) {
                String filesSection = matcher.group(1);
                java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile("\"([^\"]+)\":");
                java.util.regex.Matcher fileMatcher = filePattern.matcher(filesSection);
                while (fileMatcher.find()) {
                    rollbackFiles.add(fileMatcher.group(1));
                }
            }
            
            deleteNonRollbackFiles(gameDir, rollbackFiles);
            
            VersionManager.saveLocalVersion(rollbackVersion);
            
            cleanRollbackDir(rollbackDir);
            
            TIPUAMod.LOGGER.info("回滚完成，当前版本: {} / Rollback completed, current version: {}", rollbackVersion, rollbackVersion);
            return true;
            
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("回滚失败 / Failed to perform rollback", e);
            return false;
        }
    }
    
    /**
     * 删除不在回滚清单中的文件
     */
    private static void deleteNonRollbackFiles(Path gameDir, Set<String> rollbackFiles) {
        try {
            Path configDir = gameDir.resolve("config");
            Path modsDir = gameDir.resolve("mods");
            
            if (Files.exists(configDir)) {
                deleteNonRollbackFilesInDir(configDir, gameDir, rollbackFiles);
            }
            
            if (Files.exists(modsDir)) {
                deleteNonRollbackFilesInDir(modsDir, gameDir, rollbackFiles);
            }
            
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("删除文件失败 / Failed to delete files", e);
        }
    }
    
    private static void deleteNonRollbackFilesInDir(Path dir, Path baseDir, Set<String> rollbackFiles) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relativePath = baseDir.relativize(path).toString().replace("\\", "/");
                        if (!rollbackFiles.contains(relativePath)) {
                            Files.delete(path);
                            TIPUAMod.LOGGER.info("删除文件: {} / Deleted file: {}", relativePath, relativePath);
                        }
                    } catch (IOException e) {
                        TIPUAMod.LOGGER.warn("无法删除文件: {} / Cannot delete file: {}", path, path);
                    }
                });
        }
    }
    
    /**
     * 清理回滚目录
     */
    private static void cleanRollbackDir(Path rollbackDir) {
        try {
            if (Files.exists(rollbackDir)) {
                try (Stream<Path> walk = Files.walk(rollbackDir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // 忽略删除失败
                            }
                        });
                }
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("清理回滚目录失败 / Failed to clean rollback directory", e);
        }
    }
}