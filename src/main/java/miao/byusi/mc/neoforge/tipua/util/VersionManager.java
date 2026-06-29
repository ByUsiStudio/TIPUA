package miao.byusi.mc.neoforge.tipua.util;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 版本管理器 - 管理版本标识文件
 * 本地自动创建版本标识文件
 */
public class VersionManager {

    private static final String VERSION_FILE_NAME = "tipua_version.txt";
    private static final String FIRST_RUN_FLAG_NAME = "tipua_first_run.flag";

    /**
     * 获取版本标识文件路径
     */
    public static Path getVersionFilePath() {
        return TIPUAMod.CONFIG_DIR.toPath().resolve(VERSION_FILE_NAME);
    }

    /**
     * 获取首次运行标记文件路径
     */
    public static Path getFirstRunFlagPath() {
        return TIPUAMod.CONFIG_DIR.toPath().resolve(FIRST_RUN_FLAG_NAME);
    }

    /**
     * 检查是否是首次运行
     * 首次启动不检查更新
     */
    public static boolean isFirstRun() {
        return !Files.exists(getFirstRunFlagPath());
    }

    /**
     * 标记已运行（非首次）
     */
    public static void markAsRun() {
        try {
            Path flagPath = getFirstRunFlagPath();
            if (!Files.exists(flagPath)) {
                Files.writeString(flagPath, "This file marks that TIPUA has been run at least once.\n此文件标记TIPUA已至少运行一次。");
                TIPUAMod.LOGGER.info("首次运行标记已创建 / First run flag created");
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("创建首次运行标记失败 / Failed to create first run flag", e);
        }
    }

    /**
     * 获取本地版本标识
     * 如果不存在，自动创建空版本标识文件
     */
    public static String getLocalVersion() {
        Path versionPath = getVersionFilePath();

        // 自动创建版本标识文件
        if (!Files.exists(versionPath)) {
            try {
                Files.createDirectories(versionPath.getParent());
                Files.writeString(versionPath, "");
                TIPUAMod.LOGGER.info("版本标识文件已创建 / Version file created");
                return "";
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("创建版本标识文件失败 / Failed to create version file", e);
                return "";
            }
        }

        try {
            return Files.readString(versionPath).trim();
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("读取版本标识失败 / Failed to read version", e);
            return "";
        }
    }

    /**
     * 保存本地版本标识
     */
    public static void saveLocalVersion(String version) {
        try {
            Path versionPath = getVersionFilePath();
            Files.createDirectories(versionPath.getParent());
            Files.writeString(versionPath, version);
            TIPUAMod.LOGGER.info("版本标识已保存: {} / Version saved: {}", version, version);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("保存版本标识失败 / Failed to save version", e);
        }
    }

    /**
     * 比较两个版本号
     * @return 负数表示 version1 < version2，0表示相等，正数表示 version1 > version2
     */
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

    /**
     * 安全解析整数，失败返回0
     */
    private static int parseIntSafe(String str) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 检查版本号格式是否有效（如：1.2.1）
     */
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