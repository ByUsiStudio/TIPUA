package miao.byusi.mc.fabric.tipua.config;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class ServerConfig {
    private static Properties properties = new Properties();
    private static File configFile;

    private static int httpPort = 25566;
    private static String serverVersion = "1.0.0";

    public static void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "tipua-server.toml");
        
        if (configFile.exists()) {
            loadFromFile();
        } else {
            migrateFromOldConfig();
            save();
        }

        TIPUAMod.LOGGER.info("TIPUA server config loaded");
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
            TIPUAMod.LOGGER.error("Failed to load server config", e);
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
                    case "httpPort":
                        try {
                            httpPort = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            httpPort = 25566;
                        }
                        break;
                    case "serverVersion":
                        serverVersion = value;
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
                
                httpPort = Integer.parseInt(getValueFromToml(content, "httpPort", "25566").replace("\"", ""));
                serverVersion = getValueFromToml(content, "serverVersion", "1.0.0").replace("\"", "");
                
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
            writer.write("# TIPUA Server Configuration\n");
            writer.write("# HTTP server port for version query and file distribution\n");
            writer.write("httpPort=" + httpPort + "\n\n");
            writer.write("# Current modpack version\n");
            writer.write("serverVersion=\"" + serverVersion + "\"\n");
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to save server config", e);
        }
    }

    public static void reload() {
        load();
        miao.byusi.mc.fabric.tipua.server.ServerHttpManager.reload();
        TIPUAMod.LOGGER.info("TIPUA server config reloaded");
    }

    public static int getHttpPort() {
        return httpPort;
    }

    public static String getServerVersion() {
        return serverVersion;
    }

    public static void setServerVersion(String version) {
        serverVersion = version;
        save();
    }
}