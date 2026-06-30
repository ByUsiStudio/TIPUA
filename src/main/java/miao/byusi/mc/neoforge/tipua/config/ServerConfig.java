package miao.byusi.mc.neoforge.tipua.config;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.*;
import java.nio.file.*;

/**
 * TIPUA 服务端配置文件
 * 仅在服务端生成
 */
@EventBusSubscriber(modid = TIPUAMod.MOD_ID, value = Dist.DEDICATED_SERVER)
public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    private static ModConfigSpec.IntValue httpPortValue;
    private static ModConfigSpec.ConfigValue<String> serverVersionValue;
    private static ModConfigSpec.ConfigValue<String> modpackDownloadUrlValue;
    
    private static final ModConfigSpec SPEC;
    
    static {
        BUILDER.comment("TIPUA 服务端配置").push("tipua");
        
        httpPortValue = BUILDER
                .comment("HTTP服务器端口")
                .defineInRange("httpPort", 25566, 1024, 65535);
        
        serverVersionValue = BUILDER
                .comment("当前整合包版本号")
                .define("serverVersion", "1.0.0");
        
        modpackDownloadUrlValue = BUILDER
                .comment("整合包直链下载地址")
                .define("modpackDownloadUrl", "");
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    
    public static ModConfigSpec getSpec() {
        return SPEC;
    }
    
    public static int getHttpPort() {
        return httpPortValue.get();
    }
    
    public static String getServerVersion() {
        return serverVersionValue.get();
    }
    
    public static String getModpackDownloadUrl() {
        return modpackDownloadUrlValue.get();
    }
    
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            TIPUAMod.LOGGER.info("TIPUA 服务端配置已加载");
            migrateFromOldConfig();
        }
    }
    
    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            TIPUAMod.LOGGER.info("TIPUA 服务端配置已重载");
            miao.byusi.mc.neoforge.tipua.server.ServerHttpManager.reload();
        }
    }
    
    private static void migrateFromOldConfig() {
        Path oldConfigFile = Paths.get("config", "tipua-common.toml");
        Path newConfigFile = Paths.get("config", "tipua-server.toml");
        
        if (Files.exists(newConfigFile)) {
            return;
        }
        
        if (Files.exists(oldConfigFile)) {
            TIPUAMod.LOGGER.info("检测到旧配置文件，正在迁移...");
            
            try {
                String content = new String(Files.readAllBytes(oldConfigFile));
                
                StringBuilder newContent = new StringBuilder();
                newContent.append("# TIPUA 服务端配置文件\n");
                newContent.append("# 此文件由旧版 tipua-common.toml 迁移生成\n\n");
                newContent.append("# 如果您想恢复默认配置，请删除此文件\n\n");
                newContent.append("[default]\n");
                newContent.append("# HTTP服务器端口\n");
                newContent.append(getValueFromToml(content, "httpPort", "25566"));
                newContent.append("\n\n");
                newContent.append("# 当前整合包版本号\n");
                newContent.append(getValueFromToml(content, "serverVersion", "1.0.0"));
                newContent.append("\n\n");
                newContent.append("# 整合包直链下载地址\n");
                newContent.append(getValueFromToml(content, "modpackDownloadUrl", ""));
                
                Files.write(newConfigFile, newContent.toString().getBytes());
                
                Path backupFile = Paths.get("config", "tipua-common.toml.bak");
                Files.move(oldConfigFile, backupFile);
                
                TIPUAMod.LOGGER.info("配置文件迁移完成");
                
            } catch (IOException e) {
                TIPUAMod.LOGGER.error("配置文件迁移失败", e);
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
                return key + "=\"" + matcher.group(1) + "\"";
            }
        }
        
        return key + "=\"" + defaultValue + "\"";
    }
}