package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModrinthIndex {

    private String game;
    private int formatVersion;
    private String versionId;
    private String name;
    private List<FileEntry> files = new ArrayList<>();
    private Map<String, String> dependencies = new HashMap<>();

    public static class FileEntry {
        public String path;
        public Map<String, String> hashes = new HashMap<>();
        public List<String> downloads = new ArrayList<>();
        public long fileSize;

        public String getSha1() {
            return hashes.get("sha1");
        }

        public String getSha512() {
            return hashes.get("sha512");
        }
    }

    public String getGame() {
        return game;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getName() {
        return name;
    }

    public List<FileEntry> getFiles() {
        return files;
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public long getTotalSize() {
        return files.stream().mapToLong(f -> f.fileSize).sum();
    }

    public int getModCount() {
        return (int) files.stream().filter(f -> f.path.startsWith("mods/")).count();
    }

    public int getResourceCount() {
        return (int) files.stream().filter(f -> !f.path.startsWith("mods/")).count();
    }

    public static ModrinthIndex fromJson(String json) {
        ModrinthIndex index = new ModrinthIndex();

        try {
            index.game = extractStringValue(json, "\"game\"", "\"");
            index.formatVersion = extractIntValue(json, "\"formatVersion\"");
            index.versionId = extractStringValue(json, "\"versionId\"", "\"");
            index.name = extractStringValue(json, "\"name\"", "\"");

            String filesSection = extractSection(json, "\"files\"", "[");
            if (!filesSection.isEmpty()) {
                List<String> fileObjs = extractObjects(filesSection);
                for (String fileObj : fileObjs) {
                    FileEntry entry = new FileEntry();
                    entry.path = extractStringValue(fileObj, "\"path\"", "\"");
                    entry.fileSize = extractLongValue(fileObj, "\"fileSize\"");

                    String hashesSection = extractSection(fileObj, "\"hashes\"", "{");
                    if (!hashesSection.isEmpty()) {
                        entry.hashes.put("sha1", extractStringValue(hashesSection, "\"sha1\"", "\""));
                        entry.hashes.put("sha512", extractStringValue(hashesSection, "\"sha512\"", "\""));
                    }

                    String downloadsSection = extractSection(fileObj, "\"downloads\"", "[");
                    if (!downloadsSection.isEmpty()) {
                        List<String> urls = extractStringArray(downloadsSection);
                        entry.downloads.addAll(urls);
                    }

                    if (!entry.path.isEmpty()) {
                        index.files.add(entry);
                    }
                }
            }

            String dependenciesSection = extractSection(json, "\"dependencies\"", "{");
            if (!dependenciesSection.isEmpty()) {
                index.dependencies.put("minecraft", extractStringValue(dependenciesSection, "\"minecraft\"", "\""));
                index.dependencies.put("fabric", extractStringValue(dependenciesSection, "\"fabric\"", "\""));
            }

        } catch (Exception e) {
            TIPUAMod.LOGGER.error("Failed to parse modrinth.index.json", e);
        }

        return index;
    }

    public static ModrinthIndex fromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }
        String json = Files.readString(filePath);
        return fromJson(json);
    }

    private static String extractStringValue(String json, String key, String delimiter) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) {
            return "";
        }

        int valueStart = json.indexOf(delimiter, keyIndex + key.length());
        if (valueStart == -1) {
            return "";
        }
        valueStart++;

        int valueEnd;
        if (delimiter.equals("\"")) {
            valueEnd = valueStart;
            while (valueEnd < json.length()) {
                if (json.charAt(valueEnd) == '\\' && valueEnd + 1 < json.length()) {
                    valueEnd += 2;
                    continue;
                }
                if (json.charAt(valueEnd) == '"') {
                    break;
                }
                valueEnd++;
            }
        } else {
            valueEnd = json.indexOf(delimiter, valueStart);
            if (valueEnd == -1) {
                valueEnd = json.length();
            }
        }

        return json.substring(valueStart, valueEnd);
    }

    private static int extractIntValue(String json, String key) {
        String valueStr = extractStringValue(json, key, ",");
        valueStr = valueStr.trim();
        if (valueStr.contains("}")) {
            valueStr = valueStr.substring(0, valueStr.indexOf("}")).trim();
        }
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long extractLongValue(String json, String key) {
        String valueStr = extractStringValue(json, key, ",");
        valueStr = valueStr.trim();
        if (valueStr.contains("}")) {
            valueStr = valueStr.substring(0, valueStr.indexOf("}")).trim();
        }
        try {
            return Long.parseLong(valueStr);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractSection(String json, String key, String startDelimiter) {
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) {
            return "";
        }

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) {
            return "";
        }

        int startIndex = json.indexOf(startDelimiter, colonIndex);
        if (startIndex == -1) {
            return "";
        }

        char endDelimiter = startDelimiter.equals("[") ? ']' : '}';
        int depth = 1;
        int endIndex = startIndex + 1;

        while (endIndex < json.length() && depth > 0) {
            char c = json.charAt(endIndex);
            if (c == '\\' && endIndex + 1 < json.length()) {
                endIndex += 2;
                continue;
            }
            if (c == '"') {
                endIndex++;
                while (endIndex < json.length()) {
                    if (json.charAt(endIndex) == '\\' && endIndex + 1 < json.length()) {
                        endIndex += 2;
                        continue;
                    }
                    if (json.charAt(endIndex) == '"') {
                        break;
                    }
                    endIndex++;
                }
                endIndex++;
                continue;
            }
            if (c == startDelimiter.charAt(0)) {
                depth++;
            } else if (c == endDelimiter) {
                depth--;
            }
            endIndex++;
        }

        if (depth == 0) {
            return json.substring(startIndex, endIndex);
        }

        return "";
    }

    private static List<String> extractObjects(String arrayJson) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;

        for (int i = 0; i < arrayJson.length(); i++) {
            char c = arrayJson.charAt(i);

            if (c == '"') {
                i++;
                while (i < arrayJson.length()) {
                    if (arrayJson.charAt(i) == '\\' && i + 1 < arrayJson.length()) {
                        i += 2;
                        continue;
                    }
                    if (arrayJson.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }

            if (c == '{') {
                depth++;
                if (depth == 1) {
                    start = i;
                }
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    objects.add(arrayJson.substring(start, i + 1));
                    start = -1;
                }
            }
        }

        return objects;
    }

    private static List<String> extractStringArray(String arrayJson) {
        List<String> strings = new ArrayList<>();
        boolean inString = false;
        int start = -1;

        for (int i = 0; i < arrayJson.length(); i++) {
            char c = arrayJson.charAt(i);

            if (c == '\\' && inString && i + 1 < arrayJson.length()) {
                i++;
                continue;
            }

            if (c == '"') {
                if (inString) {
                    if (start != -1) {
                        strings.add(arrayJson.substring(start, i));
                    }
                    inString = false;
                    start = -1;
                } else {
                    inString = true;
                    start = i + 1;
                }
            }
        }

        return strings;
    }
}