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
 * TIPUA 客户端配置文件
 * 仅在客户端生成
 */
@EventBusSubscriber(modid = TIPUAMod.MOD_ID, value = Dist.CLIENT)
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    private static ModConfigSpec.ConfigValue<String> serverAddressValue;
    private static ModConfigSpec.IntValue httpPortValue;
    private static ModConfigSpec.BooleanValue autoUpdateValue;
    private static ModConfigSpec.BooleanValue autoExtractValue;
    private static ModConfigSpec.IntValue downloadTimeoutSecondsValue;
    private static ModConfigSpec.BooleanValue showUpdateNotificationValue;
    private static ModConfigSpec.IntValue maxRetryAttemptsValue;
    private static ModConfigSpec.IntValue retryDelaySecondsValue;
    private static ModConfigSpec.BooleanValue autoRollbackValue;
    private static ModConfigSpec.ConfigValue<String> scheduledUpdateTimeValue;
    private static ModConfigSpec.BooleanValue autoUpdateOnScheduleValue;
    
    private static final ModConfigSpec SPEC;
    
    static {
        BUILDER.comment("TIPUA 客户端配置").push("tipua");
        
        serverAddressValue = BUILDER
                .comment("服务器HTTP地址")
                .define("serverAddress", "localhost");
        
        httpPortValue = BUILDER
                .comment("服务器HTTP端口")
                .defineInRange("httpPort", 25566, 1024, 65535);
        
        autoUpdateValue = BUILDER
                .comment("自动检查更新")
                .define("autoUpdate", true);
        
        autoExtractValue = BUILDER
                .comment("自动解压ZIP压缩包")
                .define("autoExtract", true);
        
        downloadTimeoutSecondsValue = BUILDER
                .comment("下载超时（秒）")
                .defineInRange("downloadTimeoutSeconds", 300, 30, 3600);
        
        showUpdateNotificationValue = BUILDER
                .comment("显示更新通知")
                .define("showUpdateNotification", true);
        
        maxRetryAttemptsValue = BUILDER
                .comment("下载失败时的最大重试次数")
                .defineInRange("maxRetryAttempts", 3, 0, 10);
        
        retryDelaySecondsValue = BUILDER
                .comment("重试之间的延迟时间（秒）")
                .defineInRange("retryDelaySeconds", 5, 1, 60);
        
        autoRollbackValue = BUILDER
                .comment("更新失败时自动回滚到之前版本")
                .define("autoRollback", true);
        
        scheduledUpdateTimeValue = BUILDER
                .comment("定时更新时间（HH:mm格式，如 14:30），留空则禁用定时更新")
                .define("scheduledUpdateTime", "");
        
        autoUpdateOnScheduleValue = BUILDER
                .comment("是否在定时更新时间自动更新")
                .define("autoUpdateOnSchedule", true);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    
    public static ModConfigSpec getSpec() {
        return SPEC;
    }
    
    public static String getServerAddress() {
        return serverAddressValue.get();
    }
    
    public static int getHttpPort() {
        return httpPortValue.get();
    }
    
    public static boolean isAutoUpdate() {
        return autoUpdateValue.get();
    }
    
    public static boolean isAutoExtract() {
        return autoExtractValue.get();
    }
    
    public static int getDownloadTimeoutSeconds() {
        return downloadTimeoutSecondsValue.get();
    }
    
    public static boolean isShowUpdateNotification() {
        return showUpdateNotificationValue.get();
    }
    
    public static int getMaxRetryAttempts() {
        return maxRetryAttemptsValue.get();
    }
    
    public static int getRetryDelaySeconds() {
        return retryDelaySecondsValue.get();
    }
    
    public static boolean isAutoRollback() {
        return autoRollbackValue.get();
    }
    
    public static String getScheduledUpdateTime() {
        return scheduledUpdateTimeValue.get();
    }
    
    public static boolean isAutoUpdateOnSchedule() {
        return autoUpdateOnScheduleValue.get();
    }
    
    public static boolean isScheduledUpdateEnabled() {
        String time = getScheduledUpdateTime();
        return time != null && !time.trim().isEmpty();
    }
    
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            TIPUAMod.LOGGER.info("TIPUA 客户端配置已加载");
            migrateFromOldConfig();
        }
    }
    
    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            TIPUAMod.LOGGER.info("TIPUA 客户端配置已重载");
        }
    }
    
    private static void migrateFromOldConfig() {
        Path oldConfigFile = Paths.get("config", "tipua-common.toml");
        Path newConfigFile = Paths.get("config", "tipua-client.toml");
        
        if (Files.exists(newConfigFile)) {
            return;
        }
        
        if (Files.exists(oldConfigFile)) {
            TIPUAMod.LOGGER.info("检测到旧配置文件，正在迁移...");
            
            try {
                String content = new String(Files.readAllBytes(oldConfigFile));
                
                StringBuilder newContent = new StringBuilder();
                newContent.append("# TIPUA 客户端配置文件\n");
                newContent.append("# 此文件由旧版 tipua-common.toml 迁移生成\n\n");
                newContent.append("# 如果您想恢复默认配置，请删除此文件\n\n");
                newContent.append("[default]\n");
                newContent.append("# 服务器HTTP地址\n");
                newContent.append(getValueFromToml(content, "serverAddress", "localhost"));
                newContent.append("\n\n");
                newContent.append("# 服务器HTTP端口\n");
                newContent.append(getValueFromToml(content, "httpPort", "25566"));
                newContent.append("\n\n");
                newContent.append("# 自动检查更新\n");
                newContent.append(getValueFromToml(content, "autoUpdate", "true"));
                newContent.append("\n\n");
                newContent.append("# 自动解压ZIP\n");
                newContent.append(getValueFromToml(content, "autoExtract", "true"));
                newContent.append("\n\n");
                newContent.append("# 下载超时(秒)\n");
                newContent.append(getValueFromToml(content, "downloadTimeoutSeconds", "300"));
                newContent.append("\n\n");
                newContent.append("# 显示更新通知\n");
                newContent.append(getValueFromToml(content, "showUpdateNotification", "true"));
                
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