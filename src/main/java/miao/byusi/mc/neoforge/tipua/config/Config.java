package miao.byusi.mc.neoforge.tipua.config;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.server.ServerHttpManager;
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
        public final ModConfigSpec.ConfigValue<String> serverVersion;
        public final ModConfigSpec.ConfigValue<String> modpackDownloadUrl;

        ServerConfig(ModConfigSpec.Builder builder) {
            httpPort = builder
                    .comment("HTTP服务器端口（提供版本查询和直链地址） / HTTP server port (provides version query and direct URL)")
                    .defineInRange("httpPort", 25566, 1024, 65535);

            serverVersion = builder
                    .comment("当前整合包版本号（如：1.2.1） / Current modpack version number (e.g., 1.2.1)")
                    .define("serverVersion", "1.0.0");

            modpackDownloadUrl = builder
                    .comment("整合包直链下载地址（必须配置，客户端将从这里下载） / Modpack direct download URL (must be configured, clients download from this URL)")
                    .define("modpackDownloadUrl", "");
        }
    }

    public static class ClientConfig {
        public final ModConfigSpec.ConfigValue<String> serverAddress;
        public final ModConfigSpec.IntValue httpPort;
        public final ModConfigSpec.BooleanValue autoUpdate;
        public final ModConfigSpec.BooleanValue autoExtract;
        public final ModConfigSpec.IntValue downloadTimeoutSeconds;
        public final ModConfigSpec.BooleanValue showUpdateNotification;
        public final ModConfigSpec.ConfigValue<String> modpackDownloadUrl;

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

            downloadTimeoutSeconds = builder
                    .comment("下载超时（秒） / Download timeout in seconds")
                    .defineInRange("downloadTimeoutSeconds", 300, 30, 3600);

            showUpdateNotification = builder
                    .comment("显示更新通知 / Show update notifications")
                    .define("showUpdateNotification", true);

            modpackDownloadUrl = builder
                    .comment("整合包下载地址（高级选项：覆盖服务端提供的直链，留空则从服务端获取） / Modpack download URL (advanced: override server-provided direct URL, leave empty to get from server)")
                    .define("modpackDownloadUrl", "");
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        TIPUAMod.LOGGER.info("TIPUA配置已加载 / TIPUA config loaded");
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        TIPUAMod.LOGGER.info("TIPUA配置已重新加载 / TIPUA config reloaded");
        // 通知服务端重载HTTP服务器配置
        ServerHttpManager.reload();
    }
}