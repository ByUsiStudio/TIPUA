package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModFileOptimizer {
    
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
        
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getModId() { return modId; }
        public String getVersion() { return version; }
        public long getFileSize() { return fileSize; }
        public String getFileHash() { return fileHash; }
        public List<String> getDependencies() { return dependencies; }
        public boolean isDuplicate() { return isDuplicate; }
        public String getDuplicateWith() { return duplicateWith; }
    }
    
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
        
        public List<ModInfo> getAllMods() { return allMods; }
        public List<ModInfo> getDuplicateMods() { return duplicateMods; }
        public List<ModInfo> getObsoleteMods() { return obsoleteMods; }
        public long getTotalSizeBefore() { return totalSizeBefore; }
        public long getTotalSizeAfter() { return totalSizeAfter; }
        public long getSpaceSaved() { return spaceSaved; }
        public int getDuplicateCount() { return duplicateCount; }
        public int getObsoleteCount() { return obsoleteCount; }
        
        public double getOptimizationPercentage() {
            return totalSizeBefore > 0 ? (spaceSaved * 100.0 / totalSizeBefore) : 0.0;
        }
    }
    
    public static OptimizationResult analyzeAndOptimizeMods() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path modsDir = gameDir.resolve("mods");
        
        if (!Files.exists(modsDir)) {
            TIPUAMod.LOGGER.warn("模组目录不存在 / Mods directory does not exist: {}", modsDir);
            return new OptimizationResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0, 0, 0, 0, 0);
        }
        
        try {
            List<Path> modFiles = Files.walk(modsDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar") || path.toString().endsWith(".zip"))
                .collect(Collectors.toList());
            
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
            
            List<ModInfo> duplicateMods = findDuplicateMods(modsByModId);
            List<ModInfo> obsoleteMods = findObsoleteMods(modsByModId);
            
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
    
    private static ModInfo analyzeModFile(Path modFile) {
        try {
            String fileName = modFile.getFileName().toString();
            long fileSize = Files.size(modFile);
            String fileHash = calculateFileHash(modFile);
            
            String modId = null;
            String version = null;
            List<String> dependencies = new ArrayList<>();
            
            if (fileName.endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(modFile.toFile())) {
                    JarEntry mcmodEntry = jarFile.getJarEntry("mcmod.info");
                    JarEntry fabricModEntry = jarFile.getJarEntry("fabric.mod.json");
                    
                    if (mcmodEntry != null) {
                        String mcmodContent = readJarEntry(jarFile, mcmodEntry);
                        modId = extractModIdFromMcmod(mcmodContent);
                        version = extractVersionFromMcmod(mcmodContent);
                    } else if (fabricModEntry != null) {
                        String fabricModContent = readJarEntry(jarFile, fabricModEntry);
                        modId = extractModIdFromFabric(fabricModContent);
                        version = extractVersionFromFabric(fabricModContent);
                    }
                }
            }
            
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
    
    private static List<ModInfo> findDuplicateMods(Map<String, List<ModInfo>> modsByModId) {
        List<ModInfo> duplicateMods = new ArrayList<>();
        
        for (Map.Entry<String, List<ModInfo>> entry : modsByModId.entrySet()) {
            List<ModInfo> mods = entry.getValue();
            
            if (mods.size() > 1) {
                mods.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion()));
                ModInfo latestMod = mods.get(0);
                
                for (int i = 1; i < mods.size(); i++) {
                    ModInfo duplicateMod = mods.get(i);
                    
                    if (duplicateMod.getFileHash().equals(latestMod.getFileHash())) {
                        duplicateMods.add(new ModInfo(
                            duplicateMod.getFileName(), duplicateMod.getFilePath(),
                            duplicateMod.getModId(), duplicateMod.getVersion(),
                            duplicateMod.getFileSize(), duplicateMod.getFileHash(),
                            duplicateMod.getDependencies(), true, latestMod.getFileName()
                        ));
                    } else {
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
    
    private static List<ModInfo> findObsoleteMods(Map<String, List<ModInfo>> modsByModId) {
        List<ModInfo> obsoleteMods = new ArrayList<>();
        
        for (Map.Entry<String, List<ModInfo>> entry : modsByModId.entrySet()) {
            List<ModInfo> mods = entry.getValue();
            
            if (mods.size() > 1) {
                mods.sort((a, b) -> compareVersions(b.getVersion(), a.getVersion()));
                
                for (int i = 1; i < mods.size(); i++) {
                    ModInfo obsoleteMod = mods.get(i);
                    
                    if (compareVersions(obsoleteMod.getVersion(), mods.get(0).getVersion()) < 0) {
                        obsoleteMods.add(obsoleteMod);
                    }
                }
            }
        }
        
        return obsoleteMods;
    }
    
    public static boolean removeDuplicateAndObsoleteMods(OptimizationResult result) {
        if (result == null) {
            return false;
        }
        
        int removedCount = 0;
        long freedSpace = 0;
        
        try {
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
    
    private static String readJarEntry(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            return new String(is.readAllBytes());
        }
    }
    
    private static String extractModIdFromMcmod(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"modid\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static String extractVersionFromMcmod(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static String extractModIdFromFabric(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static String extractVersionFromFabric(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static String extractModIdFromFileName(String fileName) {
        String nameWithoutExt = fileName.replaceAll("\\.(jar|zip)$", "");
        nameWithoutExt = nameWithoutExt.replaceAll("-\\d+\\.\\d+\\.\\d+.*$", "");
        nameWithoutExt = nameWithoutExt.replaceAll("-\\d+\\.\\d+.*$", "");
        return nameWithoutExt;
    }
    
    private static String extractVersionFromFileName(String fileName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+\\.?\\d*[^.]*)");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "unknown";
    }
    
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
            return version1.compareTo(version2);
        }
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}