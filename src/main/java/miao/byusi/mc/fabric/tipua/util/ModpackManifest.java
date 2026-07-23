package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ModpackManifest {
    
    private String version;
    private String name;
    private String description;
    private List<FileEntry> files;
    private ModrinthInfo modrinthInfo;
    
    public static class FileEntry {
        public String path;
        public long size;
        public String hash;
        public String type;
        
        public FileEntry(String path, long size, String hash, String type) {
            this.path = path;
            this.size = size;
            this.hash = hash;
            this.type = type;
        }
    }
    
    public static class ModrinthInfo {
        public String projectId;
        public String versionId;
        public String changelog;
        
        public ModrinthInfo(String projectId, String versionId, String changelog) {
            this.projectId = projectId;
            this.versionId = versionId;
            this.changelog = changelog;
        }
    }
    
    public ModpackManifest() {
        this.files = new ArrayList<>();
    }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public List<FileEntry> getFiles() { return files; }
    public void setFiles(List<FileEntry> files) { this.files = files; }
    
    public ModrinthInfo getModrinthInfo() { return modrinthInfo; }
    public void setModrinthInfo(ModrinthInfo modrinthInfo) { this.modrinthInfo = modrinthInfo; }
    
    public static ModpackManifest fromZip(File zipFile) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            java.util.zip.ZipEntry entry = zf.getEntry("manifest.json");
            if (entry == null) {
                TIPUAMod.LOGGER.warn("ZIP包中未找到manifest.json / manifest.json not found in ZIP");
                return createDefaultManifest(zipFile);
            }
            
            try (InputStream is = zf.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                return parseJson(sb.toString());
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("读取manifest.json失败 / Failed to read manifest.json", e);
            return createDefaultManifest(zipFile);
        }
    }
    
    private static ModpackManifest createDefaultManifest(File zipFile) {
        ModpackManifest manifest = new ModpackManifest();
        manifest.setName("Unknown Modpack");
        manifest.setVersion("1.0.0");
        manifest.setDescription("Auto-generated manifest");
        
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    String type = detectFileType(entry.getName());
                    manifest.files.add(new FileEntry(
                        entry.getName(),
                        entry.getSize(),
                        "",
                        type
                    ));
                }
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.warn("创建默认manifest失败 / Failed to create default manifest", e);
        }
        
        return manifest;
    }
    
    private static ModpackManifest parseJson(String json) {
        ModpackManifest manifest = new ModpackManifest();
        
        try {
            java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = versionPattern.matcher(json);
            if (matcher.find()) {
                manifest.setVersion(matcher.group(1));
            }
            
            java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
            matcher = namePattern.matcher(json);
            if (matcher.find()) {
                manifest.setName(matcher.group(1));
            }
            
            java.util.regex.Pattern descPattern = java.util.regex.Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");
            matcher = descPattern.matcher(json);
            if (matcher.find()) {
                manifest.setDescription(matcher.group(1));
            }
            
            java.util.regex.Pattern filesPattern = java.util.regex.Pattern.compile("\"files\"\\s*:\\s*\\[([^\\]]+)\\]");
            matcher = filesPattern.matcher(json);
            if (matcher.find()) {
                String filesArray = matcher.group(1);
                java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
                java.util.regex.Matcher fileMatcher = filePattern.matcher(filesArray);
                
                while (fileMatcher.find()) {
                    String fileObj = fileMatcher.group(1);
                    String path = extractValue(fileObj, "path");
                    long size = parseLong(extractValue(fileObj, "size"));
                    String hash = extractValue(fileObj, "hash");
                    String type = extractValue(fileObj, "type");
                    
                    if (!path.isEmpty()) {
                        if (type.isEmpty()) {
                            type = detectFileType(path);
                        }
                        manifest.files.add(new FileEntry(path, size, hash, type));
                    }
                }
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("解析manifest.json失败 / Failed to parse manifest.json", e);
        }
        
        return manifest;
    }
    
    private static String extractValue(String jsonObj, String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(jsonObj);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static String detectFileType(String path) {
        if (path.startsWith("mods/") && path.endsWith(".jar")) {
            return "mod";
        } else if (path.startsWith("config/")) {
            return "config";
        } else if (path.endsWith(".json") || path.endsWith(".toml") || path.endsWith(".cfg")) {
            return "config";
        } else {
            return "resource";
        }
    }
    
    public List<FileEntry> getModList() {
        return files.stream()
            .filter(f -> "mod".equals(f.type))
            .collect(Collectors.toList());
    }
    
    public List<FileEntry> getConfigList() {
        return files.stream()
            .filter(f -> "config".equals(f.type))
            .collect(Collectors.toList());
    }
    
    public List<FileEntry> getResourceList() {
        return files.stream()
            .filter(f -> "resource".equals(f.type))
            .collect(Collectors.toList());
    }
    
    public long getTotalSize() {
        return files.stream().mapToLong(f -> f.size).sum();
    }
    
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}