package miao.byusi.mc.fabric.tipua.util;

import miao.byusi.mc.fabric.tipua.TIPUAMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionManager {

    private static final String VERSION_FILE_NAME = "tipua_version.txt";
    private static final String FIRST_RUN_FLAG_NAME = "tipua_first_run.flag";

    public static Path getVersionFilePath() {
        return TIPUAMod.CONFIG_DIR.toPath().resolve(VERSION_FILE_NAME);
    }

    public static Path getFirstRunFlagPath() {
        return TIPUAMod.CONFIG_DIR.toPath().resolve(FIRST_RUN_FLAG_NAME);
    }

    public static boolean isFirstRun() {
        return !Files.exists(getFirstRunFlagPath());
    }

    public static void markAsRun() {
        try {
            Path flagPath = getFirstRunFlagPath();
            if (!Files.exists(flagPath)) {
                Files.writeString(flagPath, "This file marks that TIPUA has been run at least once.\n此文件标记TIPUA已至少运行一次。");
                TIPUAMod.LOGGER.info("First run flag created");
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to create first run flag", e);
        }
    }

    public static String getLocalVersion() {
        Path versionPath = getVersionFilePath();

        if (!Files.exists(versionPath)) {
            try {
                Files.createDirectories(versionPath.getParent());
                Files.writeString(versionPath, "");
                TIPUAMod.LOGGER.info("Version file created");
                return "";
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Failed to create version file", e);
                return "";
            }
        }

        try {
            return Files.readString(versionPath).trim();
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to read version", e);
            return "";
        }
    }

    public static void saveLocalVersion(String version) {
        try {
            Path versionPath = getVersionFilePath();
            Files.createDirectories(versionPath.getParent());
            Files.writeString(versionPath, version);
            TIPUAMod.LOGGER.info("Version saved: {}", version);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to save version", e);
        }
    }

    public static int compareVersions(String version1, String version2) {
        if (version1 == null || version1.isEmpty()) return -1;
        if (version2 == null || version2.isEmpty()) return 1;

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = (i < parts1.length) ? parseIntSafe(parts1[i]) : 0;
            int num2 = (i < parts2.length) ? parseIntSafe(parts2[i]) : 0;

            if (num1 != num2) {
                return num1 - num2;
            }
        }

        return 0;
    }

    private static int parseIntSafe(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isValidVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }

        String[] parts = version.split("\\.");
        if (parts.length == 0 || parts.length > 4) {
            return false;
        }

        for (String part : parts) {
            if (part.isEmpty() || !part.matches("\\d+")) {
                return false;
            }
        }

        return true;
    }
}