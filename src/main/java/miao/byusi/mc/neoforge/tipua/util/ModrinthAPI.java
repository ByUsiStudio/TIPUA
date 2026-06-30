package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Modrinth API 集成工具类
 * 用于从Modrinth获取项目版本信息和更新日志
 */
public class ModrinthAPI {
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2";
    private static final String MODRINTH_PROJECT_ID = "DGoslH8K";
    
    /**
     * Modrinth版本信息
     */
    public static class VersionInfo {
        public String id;
        public String versionNumber;
        public String changelog;
        public String datePublished;
        public List<String> gameVersions;
        public List<String> loaders;
        public List<FileInfo> files;
        
        public VersionInfo() {
            this.gameVersions = new ArrayList<>();
            this.loaders = new ArrayList<>();
            this.files = new ArrayList<>();
        }
    }
    
    /**
     * 文件信息
     */
    public static class FileInfo {
        public String fileName;
        public long size;
        public String url;
        
        public FileInfo(String fileName, long size, String url) {
            this.fileName = fileName;
            this.size = size;
            this.url = url;
        }
    }
    
    /**
     * 获取项目的版本列表
     * @param projectId Modrinth项目ID
     * @return 版本信息列表，失败返回null
     */
    public static List<VersionInfo> getProjectVersions(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            TIPUAMod.LOGGER.warn("Modrinth项目ID未配置 / Modrinth project ID not configured");
            return null;
        }
        
        try {
            URL url = new URL(MODRINTH_API_URL + "/project/" + projectId + "/version");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("获取Modrinth版本列表失败: HTTP {} / Failed to get Modrinth versions: HTTP {}", 
                        connection.getResponseCode(), connection.getResponseCode());
                return null;
            }
            
            try (InputStream is = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                return parseVersionsJson(sb.toString());
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("获取Modrinth版本列表异常 / Exception getting Modrinth versions", e);
            return null;
        }
    }
    
    /**
     * 获取最新版本信息
     */
    public static VersionInfo getLatestVersion(String projectId) {
        List<VersionInfo> versions = getProjectVersions(projectId);
        if (versions == null || versions.isEmpty()) {
            return null;
        }
        
        return versions.get(0);
    }
    
    /**
     * 获取指定版本信息
     */
    public static VersionInfo getVersion(String versionId) {
        try {
            URL url = new URL(MODRINTH_API_URL + "/version/" + versionId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "TIPUA/1.0.0");
            
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                TIPUAMod.LOGGER.error("获取Modrinth版本失败: HTTP {} / Failed to get Modrinth version: HTTP {}", 
                        connection.getResponseCode(), connection.getResponseCode());
                return null;
            }
            
            try (InputStream is = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                
                return parseVersionJson(sb.toString());
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("获取Modrinth版本异常 / Exception getting Modrinth version", e);
            return null;
        }
    }
    
    /**
     * 解析版本列表JSON
     */
    private static List<VersionInfo> parseVersionsJson(String json) {
        List<VersionInfo> versions = new ArrayList<>();
        
        try {
            java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher matcher = versionPattern.matcher(json);
            
            while (matcher.find()) {
                String versionObj = matcher.group(1);
                VersionInfo version = parseVersionJsonFromObject(versionObj);
                if (version != null && version.versionNumber != null && !version.versionNumber.isEmpty()) {
                    versions.add(version);
                }
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("解析Modrinth版本列表失败 / Failed to parse Modrinth versions", e);
        }
        
        return versions;
    }
    
    private static VersionInfo parseVersionJson(String json) {
        try {
            java.util.regex.Pattern objPattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
            java.util.regex.Matcher matcher = objPattern.matcher(json);
            
            if (matcher.find()) {
                return parseVersionJsonFromObject(matcher.group(1));
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("解析Modrinth版本失败 / Failed to parse Modrinth version", e);
        }
        
        return null;
    }
    
    private static VersionInfo parseVersionJsonFromObject(String jsonObj) {
        VersionInfo version = new VersionInfo();
        
        try {
            version.id = extractJsonValue(jsonObj, "id");
            version.versionNumber = extractJsonValue(jsonObj, "version_number");
            version.changelog = extractJsonValue(jsonObj, "changelog");
            version.datePublished = extractJsonValue(jsonObj, "date_published");
            
            version.gameVersions = extractJsonArray(jsonObj, "game_versions");
            version.loaders = extractJsonArray(jsonObj, "loaders");
            
            String filesJson = extractJsonArrayString(jsonObj, "files");
            if (!filesJson.isEmpty()) {
                java.util.regex.Pattern filePattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
                java.util.regex.Matcher fileMatcher = filePattern.matcher(filesJson);
                
                while (fileMatcher.find()) {
                    String fileObj = fileMatcher.group(1);
                    String fileName = extractJsonValue(fileObj, "filename");
                    long size = parseLong(extractJsonValue(fileObj, "size"));
                    String url = extractJsonValue(fileObj, "url");
                    
                    if (!fileName.isEmpty()) {
                        version.files.add(new FileInfo(fileName, size, url));
                    }
                }
            }
            
        } catch (Exception e) {
            TIPUAMod.LOGGER.warn("解析版本对象失败 / Failed to parse version object", e);
        }
        
        return version;
    }
    
    private static String extractJsonValue(String jsonObj, String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(jsonObj);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
    
    private static List<String> extractJsonArray(String jsonObj, String key) {
        List<String> result = new ArrayList<>();
        String arrayStr = extractJsonArrayString(jsonObj, key);
        
        if (!arrayStr.isEmpty()) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
            java.util.regex.Matcher matcher = pattern.matcher(arrayStr);
            
            while (matcher.find()) {
                result.add(matcher.group(1));
            }
        }
        
        return result;
    }
    
    private static String extractJsonArrayString(String jsonObj, String key) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]+)\\]");
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
    
    /**
     * 获取Modrinth项目ID（硬编码）
     */
    public static String getProjectId() {
        return MODRINTH_PROJECT_ID;
    }
    
    /**
     * 检查Modrinth集成是否可用
     */
    public static boolean isAvailable() {
        return MODRINTH_PROJECT_ID != null && !MODRINTH_PROJECT_ID.isEmpty();
    }
    
    /**
     * 获取版本更新日志摘要（前200字符）
     */
    public static String getChangelogSummary(String changelog) {
        if (changelog == null || changelog.isEmpty()) {
            return "";
        }
        
        String plainText = changelog.replaceAll("<[^>]*>", "");
        
        if (plainText.length() <= 200) {
            return plainText;
        }
        
        return plainText.substring(0, 200) + "...";
    }
}