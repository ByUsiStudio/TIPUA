package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 模组文件去重和优化工具
 * 检测和移除重复的模组文件，优化整合包大小
 */
public class ModFileOptimizer {
    
    /**
     * 模组信息
     */
    public static class ModInfo {
        private final String fileName;
        private final String filePath;
        private final String modId;
        private final String version;
        private final long fileSize;
        private final String fileHash;
        private final List<String> dependencies;
        private final boolean isDuplicate;
        private final String duplicateWith;
        
        public ModInfo(String fileName, String filePath, String modId, String version, long fileSize, String fileHash, 
                      List<String> dependencies, boolean isDuplicate, String duplicateWith) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.modId = modId;
            this.version = version;
            this.fileSize = fileSize;
            this.fileHash = fileHash;
            this.dependencies = dependencies;
            this.isDuplicate = isDuplicate;
            this.duplicateWith = duplicateWith;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getModId() {
            return modId;
        }
        
        public String getVersion() {
            return version;
        }
        
        public long getFileSize() {
            return fileSize;
        }
        
        public String getFileHash() {
            return fileHash;
        }
        
        public List<String> getDependencies() {
            return dependencies;
        }
        
        public boolean isDuplicate() {
            return isDuplicate;
        }
        
        public String getDuplicateWith() {
            return duplicateWith;
        }
    }
    
    /**
     * 优化结果
     */
    public static class OptimizationResult {
        private final List<ModInfo> allMods;
        private final List<ModInfo> duplicateMods;
        private final List<ModInfo> obsoleteMods;
        private final long totalSizeBefore;
        private final long totalSizeAfter;
        private final long spaceSaved;
        private final int duplicateCount;
        private final int obsoleteCount;
        
        public OptimizationResult(List<ModInfo> allMods, List<ModInfo> duplicateMods, List<ModInfo> obsoleteMods,
                                 long totalSizeBefore, long totalSizeAfter, long spaceSaved, 
                                 int duplicateCount, int obsoleteCount) {
            this.allMods = allMods;
            this.duplicateMods = duplicateMods;
            this.obsoleteMods = obsoleteMods;
            this.totalSizeBefore = totalSizeBefore;
            this.totalSizeAfter = totalSizeAfter;
            this.spaceSaved = spaceSaved;
            this.duplicateCount = duplicateCount;
            this.obsoleteCount = obsoleteCount;
        }
        
        public List<ModInfo> getAllMods() {
            return allMods;
        }
        
        public List<ModInfo> getDuplicateMods() {
            return duplicateMods;
        }
        
        public List<ModInfo> getObsoleteMods() {
            return obsoleteMods;
        }
        
        public long getTotalSizeBefore() {
            return totalSizeBefore;
        }
        
        public long getTotalSizeAfter() {
            return totalSizeAfter;
        }
        
        public long getSpaceSaved() {
            return spaceSaved;
        }
        
        public int getDuplicateCount() {
            return duplicateCount;
        }
        
        public int getObsoleteCount() {
            return obsoleteCount;
        }
        
        public double getOptimizationPercentage() {
            return totalSizeBefore > 0 ? (spaceSaved * 100.0 / totalSizeBefore) : 0.0;
        }
    }
    
    /**
     * 分析并优化模组文件
     */
    public static OptimizationResult analyzeAndOptimizeMods() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path modsDir = gameDir.resolve("mods");
        
        if (!Files.exists(modsDir)) {
            TIPUAMod.LOGGER.warn("模组目录不存在 / Mods directory does not exist: {}", modsDir);
            return new OptimizationResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0, 0, 0, 0);
        }
        
        try {
            // 扫描所有模组文件
            List<Path> modFiles = Files.walk(modsDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar") || path.toString().endsWith(".zip"))
                .collect(Collectors.toList());
            
            // 分析每个模组文件
            List<ModInfo> allMods = new ArrayList<>();
            Map<String, List<ModInfo>> modsByModId = new HashMap<>();
            
            for (Path modFile : modFiles) {
                ModInfo modInfo = analyzeModFile(modFile);
                if (modInfo != null) {
                    allMods.add(modInfo);
                    
                    if (modInfo.getModId() != null && !modInfo.getModId().isEmpty()) {
                        modsByModId.computeIfAbsent(modInfo.getModId(), k -> new ArrayList<>()).add(modInfo);
                    }
                }
            }
            
            // 检测重复模组
            List<ModInfo> duplicateMods = findDuplicateMods(modsByModId);
            
            // 检测过时模组（通过版本号比较）
            List<ModInfo> obsoleteMods = findObsoleteMods(modsByModId);
            
            // 计算优化结果
            long totalSizeBefore = allMods.stream().mapToLong(ModInfo::getFileSize).sum();
            long duplicateSize = duplicateMods.stream().mapToLong(ModInfo::getFileSize).sum();
            long obsoleteSize = obsoleteMods.stream().mapToLong(ModInfo::getFileSize).sum();
            long totalSizeAfter = totalSizeBefore - duplicateSize - obsoleteSize;
            long spaceSaved = duplicateSize + obsoleteSize;
            
            OptimizationResult result = new OptimizationResult(
                allMods, duplicateMods, obsoleteMods,
                totalSizeBefore, totalSizeAfter, spaceSaved,
                duplicateMods.size(), obsoleteMods.size()
            );
            
            TIPUAMod.LOGGER.info("模组优化分析完成 / Mod optimization analysis completed");
            TIPUAMod.LOGGER.info("总模组数: {} / Total mods: {}", allMods.size(), allMods.size());
            TIPUAMod.LOGGER.info("重复模组: {} / Duplicate mods: {}", duplicateMods.size(), duplicateMods.size());
            TIPUAMod.LOGGER.info("过时模组: {} / Obsolete mods: {}", obsoleteMods.size(), obsoleteMods.size());
            TIPUAMod.LOGGER.info("节省空间: {} / Space saved: {}", formatBytes(spaceSaved), formatBytes(spaceSaved));
            TIPUAMod.LOGGER.info("优化比例: {:.2f}% / Optimization percentage: {:.2f}%", result.getOptimizationPercentage(), result.getOptimizationPercentage());
            
            return result;
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("分析模组文件失败 / Failed to analyze mod files", e);
            return new OptimizationResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0, 0, 0, 0);
        }
    }
    
    /**
     * 分析单个模组文件
     */
    private static ModInfo analyzeModFile(Path modFile) {
        try {
            String fileName = modFile.getFileName().toString();
            long fileSize = Files.size(modFile);
            String fileHash = calculateFileHash(modFile);
            
            // 尝试从JAR文件中提取模组信息
            String modId = null;
            String version = null;
            List<String> dependencies = new ArrayList<>();
            
            if (fileName.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(modFile.toFile())) {
                    // 查找mcmod.info或fabric.mod.json文件
                    JarEntry mcmodEntry = jarFile.getJarEntry("mcmod.info");
                    JarEntry fabricModEntry = jarFile.getJarEntry("fabric.mod.json");
                    
                    if (mcmodEntry != null) {
                        // 解析Forge模组信息
                        String mcmodContent = readJarEntry(jarFile, mcmodEntry);
                        modId = extractModIdFromMcmod(mcmodContent);
                        version = extractVersionFromMcmod(mcmodContent);
                    } else if (fabricModEntry != null) {
                        // 解析Fabric模组信息
                        String fabricModContent = readJarEntry(jarFile, fabricModEntry);
                        modId = extractModIdFromFabric(fabricModContent);
                        version = extractVersionFromFabric(fabricModContent);
                    }
                }
            }
            
            // 如果无法从JAR中提取信息，从文件名推断
            if (modId == null || modId.isEmpty()) {
                modId = extractModIdFromFileName(fileName);
                version = extractVersionFromFileName(fileName);
            }
            
            return new ModInfo(fileName, modFile.toString(), modId, version, fileSize, fileHash, dependencies, false, null);
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("分析模组文件失败: {} / Failed to analyze mod file: {}", modFile, modFile, e);
            return null;
        }
    }
    
    /**
     * 查找重复模组
     */
    private static List<ModInfo> findDuplicateMods(Map<String, List<ModInfo>> modsByModId) {
        List<ModInfo> duplicateMods = new ArrayList<>();
        
        for (Map.Entry<String, List<ModInfo>> entry : modsByModId.entrySet()) {
            List<ModInfo> mods = entry.getValue();
            
            if (mods.size() > 1) {
                // 按版本排序，保留最新版本
                mods.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion()));
                
                // 第一个是最新版本，其余的都是重复或过时的
                ModInfo latestMod = mods.get(0);
                
                for (int i = 1; i < mods.size(); i++) {
                    ModInfo duplicateMod = mods.get(i);
                    
                    // 检查是否真的是重复（文件哈希相同）
                    if (duplicateMod.getFileHash().equals(latestMod.getFileHash())) {
                        // 完全相同的文件
                        duplicateMods.add(new ModInfo(
                            duplicateMod.getFileName(), duplicateMod.getFilePath(),
                            duplicateMod.getModId(), duplicateMod.getVersion(),
                            duplicateMod.getFileSize(), duplicateMod.getFileHash(),
                            duplicateMod.getDependencies(), true, latestMod.getFileName()
                        ));
                    } else {
                        // 相同模组但不同版本，可能是过时版本
                        duplicateMods.add(new ModInfo(
                            duplicateMod.getFileName(), duplicateMod.getFilePath(),
                            duplicateMod.getModId(), duplicateMod.getVersion(),
                            duplicateMod.getFileSize(), duplicateMod.getFileHash(),
                            duplicateMod.getDependencies(), false, latestMod.getFileName()
                        ));
                    }
                }
            }
        }
        
        return duplicateMods;
    }
    
    /**
     * 查找过时模组
     */
    private static List<ModInfo> findObsoleteMods(Map<String, List<ModInfo>> modsByModId) {
        List<ModInfo> obsoleteMods = new ArrayList<>();
        
        for (Map.Entry<String, List<ModInfo>> entry : modsByModId.entrySet()) {
            List<ModInfo> mods = entry.getValue();
            
            if (mods.size() > 1) {
                // 按版本排序，保留最新版本
                mods.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion()));
                
                // 第一个是最新版本，其余的都是过时的
                for (int i = 1; i < mods.size(); i++) {
                    ModInfo obsoleteMod = mods.get(i);
                    
                    // 只有版本较低的才是过时的
                    if (compareVersions(obsoleteMod.getVersion(), mods.get(0).getVersion()) < 0) {
                        obsoleteMods.add(obsoleteMod);
                    }
                }
            }
        }
        
        return obsoleteMods;
    }
    
    /**
     * 删除重复和过时的模组文件
     */
    public static boolean removeDuplicateAndObsoleteMods(OptimizationResult result) {
        if (result == null) {
            return false;
        }
        
        int removedCount = 0;
        long freedSpace = 0;
        
        try {
            // 删除重复模组
            for (ModInfo duplicateMod : result.getDuplicateMods()) {
                if (duplicateMod.isDuplicate()) {
                    Path modFile = Path.of(duplicateMod.getFilePath());
                    if (Files.exists(modFile)) {
                        long fileSize = Files.size(modFile);
                        Files.delete(modFile);
                        freedSpace += fileSize;
                        removedCount++;
                        TIPUAMod.LOGGER.info("删除重复模组: {} / Deleted duplicate mod: {}", duplicateMod.getFileName(), duplicateMod.getFileName());
                    }
                }
            }
            
            // 删除过时模组
            for (ModInfo obsoleteMod : result.getObsoleteMods()) {
                Path modFile = Path.of(obsoleteMod.getFilePath());
                if (Files.exists(modFile)) {
                    long fileSize = Files.size(modFile);
                    Files.delete(modFile);
                    freedSpace += fileSize;
                    removedCount++;
                    TIPUAMod.LOGGER.info("删除过时模组: {} / Deleted obsolete mod: {}", obsoleteMod.getFileName(), obsoleteMod.getFileName());
                }
            }
            
            TIPUAMod.LOGGER.info("模组优化完成 / Mod optimization completed");
            TIPUAMod.LOGGER.info("删除文件数: {} / Removed files: {}", removedCount, removedCount);
            TIPUAMod.LOGGER.info("释放空间: {} / Freed space: {}", formatBytes(freedSpace), formatBytes(freedSpace));
            
            return true;
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("删除模组文件失败 / Failed to remove mod files", e);
            return false;
        }
    }
    
    /**
     * 计算文件哈希
     */
    private static String calculateFileHash(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        
        try (InputStream is = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
    
    /**
     * 读取JAR条目内容
     */
    private static String readJarEntry(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            return new String(is.readAllBytes());
        }
    }
    
    /**
     * 从mcmod.info提取模组ID
     */
    private static String extractModIdFromMcmod(String content) {
        // 简化的JSON解析
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"modid\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * 从mcmod.info提取版本号
     */
    private static String extractVersionFromMcmod(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * 从fabric.mod.json提取模组ID
     */
    private static String extractModIdFromFabric(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * 从fabric.mod.json提取版本号
     */
    private static String extractVersionFromFabric(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    /**
     * 从文件名提取模组ID
     */
    private static String extractModIdFromFileName(String fileName) {
        // 移除文件扩展名
        String nameWithoutExt = fileName.replaceAll("\\.(jar|zip)$", "");
        
        // 移除版本号（常见格式：-1.0.0, -1.0.0-beta, etc）
        nameWithoutExt = nameWithoutExt.replaceAll("-\\d+\\.\\d+\\.\\d+.*$", "");
        nameWithoutExt = nameWithoutExt.replaceAll("-\\d+\\.\\d+.*$", "");
        
        return nameWithoutExt;
    }
    
    /**
     * 从文件名提取版本号
     */
    private static String extractVersionFromFileName(String fileName) {
        // 查找版本号模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.?\\d*[^.]*)");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "unknown";
    }
    
    /**
     * 比较版本号
     */
    private static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;
        
        try {
            String[] parts1 = version1.split("[^0-9]+");
            String[] parts2 = version2.split("[^0-9]+");
            
            int maxLength = Math.max(parts1.length, parts2.length);
            
            for (int i = 0; i < maxLength; i++) {
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                
                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            }
            
            return 0;
        } catch (NumberFormatException e) {
            // 如果版本号格式不正确，按字符串比较
            return version1.compareTo(version2);
        }
    }
    
    /**
     * 格式化字节大小
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}