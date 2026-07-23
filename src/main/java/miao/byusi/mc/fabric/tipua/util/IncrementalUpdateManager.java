package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class IncrementalUpdateManager {
    
    private static final String MANIFEST_FILE = ".tipua_incremental_manifest.json";
    private static final String DELTA_FILE = ".tipua_delta.zip";
    private static final int CHUNK_SIZE = 1024 * 1024;
    
    public enum FileChangeType {
        NEW("新增", "New"),
        MODIFIED("修改", "Modified"),
        DELETED("删除", "Deleted"),
        UNCHANGED("未变更", "Unchanged");
        
        private final String chineseName;
        private final String englishName;
        
        FileChangeType(String chineseName, String englishName) {
            this.chineseName = chineseName;
            this.englishName = englishName;
        }
        
        public String getChineseName() { return chineseName; }
        public String getEnglishName() { return englishName; }
    }
    
    public static class FileChangeRecord {
        private final String filePath;
        private final FileChangeType changeType;
        private final String oldHash;
        private final String newHash;
        private final long oldSize;
        private final long newSize;
        
        public FileChangeRecord(String filePath, FileChangeType changeType, String oldHash, String newHash, long oldSize, long newSize) {
            this.filePath = filePath;
            this.changeType = changeType;
            this.oldHash = oldHash;
            this.newHash = newHash;
            this.oldSize = oldSize;
            this.newSize = newSize;
        }
        
        public String getFilePath() { return filePath; }
        public FileChangeType getChangeType() { return changeType; }
        public String getOldHash() { return oldHash; }
        public String getNewHash() { return newHash; }
        public long getOldSize() { return oldSize; }
        public long getNewSize() { return newSize; }
        public long getSizeDifference() { return newSize - oldSize; }
    }
    
    public static class IncrementalUpdateResult {
        private final List<FileChangeRecord> changedFiles;
        private final long totalSizeSaved;
        private final long totalSizeToDownload;
        private final int newFilesCount;
        private final int modifiedFilesCount;
        private final int deletedFilesCount;
        private final int unchangedFilesCount;
        
        public IncrementalUpdateResult(List<FileChangeRecord> changedFiles, long totalSizeSaved, long totalSizeToDownload,
                                     int newFilesCount, int modifiedFilesCount, int deletedFilesCount, int unchangedFilesCount) {
            this.changedFiles = changedFiles;
            this.totalSizeSaved = totalSizeSaved;
            this.totalSizeToDownload = totalSizeToDownload;
            this.newFilesCount = newFilesCount;
            this.modifiedFilesCount = modifiedFilesCount;
            this.deletedFilesCount = deletedFilesCount;
            this.unchangedFilesCount = unchangedFilesCount;
        }
        
        public List<FileChangeRecord> getChangedFiles() { return changedFiles; }
        public long getTotalSizeSaved() { return totalSizeSaved; }
        public long getTotalSizeToDownload() { return totalSizeToDownload; }
        public int getNewFilesCount() { return newFilesCount; }
        public int getModifiedFilesCount() { return modifiedFilesCount; }
        public int getDeletedFilesCount() { return deletedFilesCount; }
        public int getUnchangedFilesCount() { return unchangedFilesCount; }
        public int getTotalChangedFiles() { return newFilesCount + modifiedFilesCount + deletedFilesCount; }
        
        public double getSavingPercentage() {
            long totalSize = totalSizeSaved + totalSizeToDownload;
            return totalSize > 0 ? (totalSizeSaved * 100.0 / totalSize) : 0.0;
        }
    }
    
    public static IncrementalUpdateResult analyzeChanges(File newZipFile) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path manifestFile = gameDir.resolve(MANIFEST_FILE);
        
        try {
            Map<String, String> oldManifest = readManifest(manifestFile);
            Map<String, String> newManifest = analyzeZipFile(newZipFile);
            
            List<FileChangeRecord> changes = new ArrayList<>();
            long totalSizeSaved = 0;
            long totalSizeToDownload = 0;
            
            for (Map.Entry<String, String> entry : newManifest.entrySet()) {
                String filePath = entry.getKey();
                String newHash = entry.getValue();
                String oldHash = oldManifest.get(filePath);
                
                if (oldHash == null) {
                    long fileSize = getFileSizeFromZip(newZipFile, filePath);
                    changes.add(new FileChangeRecord(filePath, FileChangeType.NEW, "", newHash, 0, fileSize));
                    totalSizeToDownload += fileSize;
                } else if (!oldHash.equals(newHash)) {
                    long oldSize = getFileSizeFromManifest(oldManifest, filePath);
                    long newSize = getFileSizeFromZip(newZipFile, filePath);
                    changes.add(new FileChangeRecord(filePath, FileChangeType.MODIFIED, oldHash, newHash, oldSize, newSize));
                    totalSizeToDownload += newSize;
                    totalSizeSaved += oldSize;
                }
            }
            
            for (Map.Entry<String, String> entry : oldManifest.entrySet()) {
                String filePath = entry.getKey();
                if (!newManifest.containsKey(filePath)) {
                    long fileSize = getFileSizeFromManifest(oldManifest, filePath);
                    changes.add(new FileChangeRecord(filePath, FileChangeType.DELETED, entry.getValue(), "", fileSize, 0));
                    totalSizeSaved += fileSize;
                }
            }
            
            int newFilesCount = (int) changes.stream().filter(c -> c.getChangeType() == FileChangeType.NEW).count();
            int modifiedFilesCount = (int) changes.stream().filter(c -> c.getChangeType() == FileChangeType.MODIFIED).count();
            int deletedFilesCount = (int) changes.stream().filter(c -> c.getChangeType() == FileChangeType.DELETED).count();
            int unchangedFilesCount = newManifest.size() - newFilesCount - modifiedFilesCount;
            
            IncrementalUpdateResult result = new IncrementalUpdateResult(
                changes, totalSizeSaved, totalSizeToDownload,
                newFilesCount, modifiedFilesCount, deletedFilesCount, unchangedFilesCount
            );
            
            TIPUAMod.LOGGER.info("增量更新分析完成 / Incremental update analysis completed");
            TIPUAMod.LOGGER.info("新增文件: {} / New files: {}", newFilesCount, newFilesCount);
            TIPUAMod.LOGGER.info("修改文件: {} / Modified files: {}", modifiedFilesCount, modifiedFilesCount);
            TIPUAMod.LOGGER.info("删除文件: {} / Deleted files: {}", deletedFilesCount, deletedFilesCount);
            TIPUAMod.LOGGER.info("节省空间: {} / Space saved: {}", formatBytes(totalSizeSaved), formatBytes(totalSizeSaved));
            TIPUAMod.LOGGER.info("需要下载: {} / To download: {}", formatBytes(totalSizeToDownload), formatBytes(totalSizeToDownload));
            TIPUAMod.LOGGER.info("节省比例: {:.2f}% / Saving percentage: {:.2f}%", result.getSavingPercentage(), result.getSavingPercentage());
            
            return result;
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("分析文件变更失败 / Failed to analyze file changes", e);
            return new IncrementalUpdateResult(new ArrayList<>(), 0, 0, 0, 0, 0, 0);
        }
    }
    
    public static File createDeltaPackage(File newZipFile, IncrementalUpdateResult changes) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path deltaFile = gameDir.resolve(DELTA_FILE);
        
        try {
            if (Files.exists(deltaFile)) {
                Files.delete(deltaFile);
            }
            
            try (ZipFile zipFile = new ZipFile(newZipFile);
                 ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(deltaFile))) {
                
                for (FileChangeRecord change : changes.getChangedFiles()) {
                    if (change.getChangeType() == FileChangeType.NEW || 
                        change.getChangeType() == FileChangeType.MODIFIED) {
                        
                        ZipEntry entry = zipFile.getEntry(change.getFilePath());
                        if (entry != null) {
                            try (InputStream is = zipFile.getInputStream(entry)) {
                                ZipEntry newEntry = new ZipEntry(change.getFilePath());
                                zipOut.putNextEntry(newEntry);
                                
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1) {
                                    zipOut.write(buffer, 0, bytesRead);
                                }
                                
                                zipOut.closeEntry();
                            }
                        }
                    }
                }
                
                ZipEntry manifestEntry = new ZipEntry("changes.json");
                zipOut.putNextEntry(manifestEntry);
                
                String manifestJson = createChangesManifest(changes);
                zipOut.write(manifestJson.getBytes());
                zipOut.closeEntry();
                
            }
            
            TIPUAMod.LOGGER.info("增量更新包创建完成: {} / Delta package created: {}", deltaFile, deltaFile);
            return deltaFile.toFile();
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("创建增量更新包失败 / Failed to create delta package", e);
            return null;
        }
    }
    
    public static void saveManifest(File zipFile) {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path manifestFile = gameDir.resolve(MANIFEST_FILE);
        
        try {
            Map<String, String> manifest = analyzeZipFile(zipFile);
            
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"version\": \"").append(VersionManager.getLocalVersion()).append("\",\n");
            json.append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
            json.append("  \"files\": {\n");
            
            List<String> paths = new ArrayList<>(manifest.keySet());
            Collections.sort(paths);
            
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                String hash = manifest.get(path);
                json.append("    \"").append(path).append("\": \"").append(hash).append("\"");
                if (i < paths.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            
            json.append("  }\n");
            json.append("}");
            
            Files.write(manifestFile, json.toString().getBytes());
            TIPUAMod.LOGGER.info("文件清单已保存 / File manifest saved");
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("保存文件清单失败 / Failed to save file manifest", e);
        }
    }
    
    private static Map<String, String> readManifest(Path manifestFile) {
        Map<String, String> manifest = new HashMap<>();
        
        if (!Files.exists(manifestFile)) {
            return manifest;
        }
        
        try {
            String content = new String(Files.readAllBytes(manifestFile));
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\":\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(content);
            
            while (matcher.find()) {
                manifest.put(matcher.group(1), matcher.group(2));
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("读取文件清单失败 / Failed to read file manifest", e);
        }
        
        return manifest;
    }
    
    private static Map<String, String> analyzeZipFile(File zipFile) {
        Map<String, String> manifest = new HashMap<>();
        
        try (ZipFile zipFileObj = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zipFileObj.entries();
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    try (InputStream is = zipFileObj.getInputStream(entry)) {
                        String hash = calculateHash(is);
                        manifest.put(entry.getName(), hash);
                    }
                }
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("分析ZIP文件失败 / Failed to analyze ZIP file", e);
        }
        
        return manifest;
    }
    
    private static String calculateHash(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }
        
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        
        return sb.toString();
    }
    
    private static long getFileSizeFromZip(File zipFile, String filePath) {
        try (ZipFile zipFileObj = new ZipFile(zipFile)) {
            ZipEntry entry = zipFileObj.getEntry(filePath);
            return entry != null ? entry.getSize() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private static long getFileSizeFromManifest(Map<String, String> manifest, String filePath) {
        return 0;
    }
    
    private static String createChangesManifest(IncrementalUpdateResult changes) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        json.append("  \"summary\": {\n");
        json.append("    \"totalChanges\": ").append(changes.getTotalChangedFiles()).append(",\n");
        json.append("    \"newFiles\": ").append(changes.getNewFilesCount()).append(",\n");
        json.append("    \"modifiedFiles\": ").append(changes.getModifiedFilesCount()).append(",\n");
        json.append("    \"deletedFiles\": ").append(changes.getDeletedFilesCount()).append(",\n");
        json.append("    \"sizeSaved\": ").append(changes.getTotalSizeSaved()).append(",\n");
        json.append("    \"sizeToDownload\": ").append(changes.getTotalSizeToDownload()).append("\n");
        json.append("  },\n");
        json.append("  \"changes\": [\n");
        
        List<FileChangeRecord> changeRecords = changes.getChangedFiles();
        for (int i = 0; i < changeRecords.size(); i++) {
            FileChangeRecord change = changeRecords.get(i);
            json.append("    {\n");
            json.append("      \"file\": \"").append(change.getFilePath()).append("\",\n");
            json.append("      \"type\": \"").append(change.getChangeType().name()).append("\",\n");
            json.append("      \"oldHash\": \"").append(change.getOldHash()).append("\",\n");
            json.append("      \"newHash\": \"").append(change.getNewHash()).append("\",\n");
            json.append("      \"oldSize\": ").append(change.getOldSize()).append(",\n");
            json.append("      \"newSize\": ").append(change.getNewSize()).append("\n");
            json.append("    }");
            if (i < changeRecords.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}