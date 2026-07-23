package miao.byusi.mc.fabric.tipua.config;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public class ClientConfig {
    private static File configFile;

    private static String serverAddress = "localhost";
    private static int httpPort = 25566;
    private static boolean autoUpdate = true;
    private static boolean autoExtract = true;
    private static int downloadTimeoutSeconds = 300;
    private static boolean showUpdateNotification = true;
    private static int maxRetryAttempts = 3;
    private static int retryDelaySeconds = 5;
    private static boolean autoRollback = true;
    private static String scheduledUpdateTime = "";
    private static boolean autoUpdateOnSchedule = true;

    public static void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tipua-client.toml");
        
        if (configFile.exists()) {
            loadFromFile();
        } else {
            migrateFromOldConfig();
            save();
        }

        TIPUAMod.LOGGER.info("TIPUA client config loaded");
    }

    private static void loadFromFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            parseToml(content.toString());
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to load client config", e);
        }
    }

    private static void parseToml(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIndex = line.indexOf('=');
            if (eqIndex > 0) {
                String key = line.substring(0, eqIndex).trim();
                String value = line.substring(eqIndex + 1).trim();
                value = value.replace("\"", "").replace("'", "");
                
                switch (key) {
                    case "serverAddress":
                        serverAddress = value;
                        break;
                    case "httpPort":
                        try {
                            httpPort = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            httpPort = 25566;
                        }
                        break;
                    case "autoUpdate":
                        autoUpdate = Boolean.parseBoolean(value);
                        break;
                    case "autoExtract":
                        autoExtract = Boolean.parseBoolean(value);
                        break;
                    case "downloadTimeoutSeconds":
                        try {
                            downloadTimeoutSeconds = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            downloadTimeoutSeconds = 300;
                        }
                        break;
                    case "showUpdateNotification":
                        showUpdateNotification = Boolean.parseBoolean(value);
                        break;
                    case "maxRetryAttempts":
                        try {
                            maxRetryAttempts = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            maxRetryAttempts = 3;
                        }
                        break;
                    case "retryDelaySeconds":
                        try {
                            retryDelaySeconds = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            retryDelaySeconds = 5;
                        }
                        break;
                    case "autoRollback":
                        autoRollback = Boolean.parseBoolean(value);
                        break;
                    case "scheduledUpdateTime":
                        scheduledUpdateTime = value;
                        break;
                    case "autoUpdateOnSchedule":
                        autoUpdateOnSchedule = Boolean.parseBoolean(value);
                        break;
                }
            }
        }
    }

    private static void migrateFromOldConfig() {
        Path oldConfigFile = Paths.get("config", "tipua-common.toml");
        Path newConfigFile = configFile.toPath();
        
        if (Files.exists(newConfigFile)) {
            return;
        }
        
        if (Files.exists(oldConfigFile)) {
            TIPUAMod.LOGGER.info("Detected old config file, migrating...");
            
            try {
                String content = new String(Files.readAllBytes(oldConfigFile));
                
                serverAddress = getValueFromToml(content, "serverAddress", "localhost").replace("\"", "");
                httpPort = Integer.parseInt(getValueFromToml(content, "httpPort", "25566").replace("\"", ""));
                autoUpdate = Boolean.parseBoolean(getValueFromToml(content, "autoUpdate", "true").replace("\"", ""));
                autoExtract = Boolean.parseBoolean(getValueFromToml(content, "autoExtract", "true").replace("\"", ""));
                downloadTimeoutSeconds = Integer.parseInt(getValueFromToml(content, "downloadTimeoutSeconds", "300").replace("\"", ""));
                showUpdateNotification = Boolean.parseBoolean(getValueFromToml(content, "showUpdateNotification", "true").replace("\"", ""));
                
                Path backupFile = Paths.get("config", "tipua-common.toml.bak");
                Files.move(oldConfigFile, backupFile);
                
                TIPUAMod.LOGGER.info("Config file migration completed");
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("Config file migration failed", e);
            }
        }
    }

    private static String getValueFromToml(String content, String key, String defaultValue) {
        String[] patterns = {
            key + "\\s*=\\s*\"([^\"]*)\"",
            key + "\\s*=\\s*'([^']*)'",
            key + "\\s*=\\s*(\\d+)",
            key + "\\s*=\\s*(true|false)"
        };
        
        for (String pattern : patterns) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return defaultValue;
    }

    public static void save() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# TIPUA Client Configuration\n\n");
            
            writer.write("# Server HTTP address\n");
            writer.write("serverAddress=\"" + serverAddress + "\"\n\n");
            
            writer.write("# Server HTTP port\n");
            writer.write("httpPort=" + httpPort + "\n\n");
            
            writer.write("# Auto-check for updates\n");
            writer.write("autoUpdate=" + autoUpdate + "\n\n");
            
            writer.write("# Auto-extract ZIP files\n");
            writer.write("autoExtract=" + autoExtract + "\n\n");
            
            writer.write("# Download timeout (seconds)\n");
            writer.write("downloadTimeoutSeconds=" + downloadTimeoutSeconds + "\n\n");
            
            writer.write("# Show update notifications\n");
            writer.write("showUpdateNotification=" + showUpdateNotification + "\n\n");
            
            writer.write("# Maximum retry attempts on download failure\n");
            writer.write("maxRetryAttempts=" + maxRetryAttempts + "\n\n");
            
            writer.write("# Delay between retries (seconds)\n");
            writer.write("retryDelaySeconds=" + retryDelaySeconds + "\n\n");
            
            writer.write("# Auto-rollback to previous version on update failure\n");
            writer.write("autoRollback=" + autoRollback + "\n\n");
            
            writer.write("# Scheduled update time (HH:mm format, e.g., 14:30), leave empty to disable\n");
            writer.write("scheduledUpdateTime=\"" + scheduledUpdateTime + "\"\n\n");
            
            writer.write("# Auto-update at scheduled time\n");
            writer.write("autoUpdateOnSchedule=" + autoUpdateOnSchedule + "\n");
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to save client config", e);
        }
    }

    public static void reload() {
        load();
        TIPUAMod.LOGGER.info("TIPUA client config reloaded");
    }

    public static String getServerAddress() {
        return serverAddress;
    }

    public static int getHttpPort() {
        return httpPort;
    }

    public static boolean isAutoUpdate() {
        return autoUpdate;
    }

    public static boolean isAutoExtract() {
        return autoExtract;
    }

    public static int getDownloadTimeoutSeconds() {
        return downloadTimeoutSeconds;
    }

    public static boolean isShowUpdateNotification() {
        return showUpdateNotification;
    }

    public static int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public static int getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public static boolean isAutoRollback() {
        return autoRollback;
    }

    public static String getScheduledUpdateTime() {
        return scheduledUpdateTime;
    }

    public static boolean isAutoUpdateOnSchedule() {
        return autoUpdateOnSchedule;
    }

    public static boolean isScheduledUpdateEnabled() {
        String time = getScheduledUpdateTime();
        return time != null && !time.trim().isEmpty();
    }
}