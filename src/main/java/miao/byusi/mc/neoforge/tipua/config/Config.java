package miao.byusi.mc.neoforge.tipua.config;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TIPUAMod.MOD_ID)
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ServerConfig SERVER;
    public static final ClientConfig CLIENT;

    static {
        COMMON_BUILDER.comment("TIPUA服务端配置 / TIPUA Server Configuration").push("server");
        SERVER = new ServerConfig(COMMON_BUILDER);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.comment("TIPUA客户端配置 / TIPUA Client Configuration").push("client");
        CLIENT = new ClientConfig(COMMON_BUILDER);
        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    public static class ServerConfig {
        public final ModConfigSpec.IntValue httpPort;
        public final ModConfigSpec.ConfigValue<String> zipPath;
        public final ModConfigSpec.IntValue checkIntervalSeconds;
        public final ModConfigSpec.BooleanValue enableFileServer;
        public final ModConfigSpec.BooleanValue enableHashVerification;

        ServerConfig(ModConfigSpec.Builder builder) {
            httpPort = builder
                    .comment("HTTP文件服务器端口 / HTTP file server port")
                    .defineInRange("httpPort", 25566, 1024, 65535);

            zipPath = builder
                    .comment("ZIP压缩包路径（必须包含config和mods目录） / ZIP archive path (must contain config and mods directories)")
                    .define("zipPath", "modpacks/default.zip");

            checkIntervalSeconds = builder
                    .comment("ZIP检查间隔（秒） / ZIP check interval in seconds")
                    .defineInRange("checkIntervalSeconds", 60, 10, 3600);

            enableFileServer = builder
                    .comment("启用HTTP文件服务器 / Enable HTTP file server")
                    .define("enableFileServer", true);

            enableHashVerification = builder
                    .comment("启用SHA-256哈希验证 / Enable SHA-256 hash verification")
                    .define("enableHashVerification", true);
        }
    }

    public static class ClientConfig {
        public final ModConfigSpec.ConfigValue<String> serverAddress;
        public final ModConfigSpec.IntValue httpPort;
        public final ModConfigSpec.BooleanValue autoUpdate;
        public final ModConfigSpec.BooleanValue autoExtract;
        public final ModConfigSpec.BooleanValue enableHashVerification;
        public final ModConfigSpec.IntValue downloadTimeoutSeconds;
        public final ModConfigSpec.BooleanValue showUpdateNotification;

        ClientConfig(ModConfigSpec.Builder builder) {
            serverAddress = builder
                    .comment("服务器HTTP地址（如：192.168.1.100） / Server HTTP address (e.g., 192.168.1.100)")
                    .define("serverAddress", "localhost");

            httpPort = builder
                    .comment("服务器HTTP端口 / Server HTTP port")
                    .defineInRange("httpPort", 25566, 1024, 65535);

            autoUpdate = builder
                    .comment("自动检查更新（进入主菜单后） / Auto-check for updates (after entering main menu)")
                    .define("autoUpdate", true);

            autoExtract = builder
                    .comment("自动解压ZIP压缩包 / Auto-extract ZIP archives")
                    .define("autoExtract", true);

            enableHashVerification = builder
                    .comment("启用SHA-256哈希验证 / Enable SHA-256 hash verification")
                    .define("enableHashVerification", true);

            downloadTimeoutSeconds = builder
                    .comment("下载超时（秒） / Download timeout in seconds")
                    .defineInRange("downloadTimeoutSeconds", 300, 30, 3600);

            showUpdateNotification = builder
                    .comment("显示更新通知 / Show update notifications")
                    .define("showUpdateNotification", true);
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        TIPUAMod.LOGGER.info("TIPUA配置已加载 / TIPUA config loaded");
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        TIPUAMod.LOGGER.info("TIPUA配置已重新加载 / TIPUA config reloaded");
    }
}