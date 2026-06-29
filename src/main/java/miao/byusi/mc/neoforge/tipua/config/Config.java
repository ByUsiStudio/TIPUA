package miao.byusi.mc.neoforge.tipua.config;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@EventBusSubscriber(modid = TIPUAMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ServerConfig SERVER;
    public static final ClientConfig CLIENT;

    static {
        COMMON_BUILDER.comment("TIPUA Server Configuration").push("server");
        SERVER = new ServerConfig(COMMON_BUILDER);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.comment("TIPUA Client Configuration").push("client");
        CLIENT = new ClientConfig(COMMON_BUILDER);
        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    public static class ServerConfig {
        public final ModConfigSpec.IntValue httpPort;
        public final ModConfigSpec.StringValue modpackPath;
        public final ModConfigSpec.IntValue checkIntervalSeconds;
        public final ModConfigSpec.BooleanValue enableFileServer;
        public final ModConfigSpec.BooleanValue enableHashVerification;
        public final ModConfigSpec.StringValue defaultModpackFormat;

        ServerConfig(ModConfigSpec.Builder builder) {
            httpPort = builder
                    .comment("Port for the HTTP file server")
                    .defineInRange("httpPort", 25566, 1024, 65535);

            modpackPath = builder
                    .comment("Path to the modpack file or directory")
                    .define("modpackPath", "modpacks/default.zip");

            checkIntervalSeconds = builder
                    .comment("Interval in seconds to check for modpack changes")
                    .defineInRange("checkIntervalSeconds", 60, 10, 3600);

            enableFileServer = builder
                    .comment("Enable the built-in HTTP file server")
                    .define("enableFileServer", true);

            enableHashVerification = builder
                    .comment("Enable SHA-256 hash verification for downloads")
                    .define("enableHashVerification", true);

            defaultModpackFormat = builder
                    .comment("Default modpack format: zip, folder, curseforge, modrinth")
                    .define("defaultModpackFormat", "zip");
        }
    }

    public static class ClientConfig {
        public final ModConfigSpec.BooleanValue autoUpdate;
        public final ModConfigSpec.BooleanValue autoDownloadMods;
        public final ModConfigSpec.BooleanValue enableHashVerification;
        public final ModConfigSpec.IntValue downloadTimeoutSeconds;
        public final ModConfigSpec.IntValue maxConcurrentDownloads;
        public final ModConfigSpec.BooleanValue showUpdateNotification;
        public final ModConfigSpec.BooleanValue askBeforeInstall;
        public final ModConfigSpec.ListValue ignoredMods;

        ClientConfig(ModConfigSpec.Builder builder) {
            autoUpdate = builder
                    .comment("Automatically check for updates when joining a server")
                    .define("autoUpdate", true);

            autoDownloadMods = builder
                    .comment("Automatically download required mods")
                    .define("autoDownloadMods", true);

            enableHashVerification = builder
                    .comment("Enable SHA-256 hash verification for downloads")
                    .define("enableHashVerification", true);

            downloadTimeoutSeconds = builder
                    .comment("Timeout in seconds for download operations")
                    .defineInRange("downloadTimeoutSeconds", 300, 30, 3600);

            maxConcurrentDownloads = builder
                    .comment("Maximum number of concurrent downloads")
                    .defineInRange("maxConcurrentDownloads", 3, 1, 10);

            showUpdateNotification = builder
                    .comment("Show notification when updates are available")
                    .define("showUpdateNotification", true);

            askBeforeInstall = builder
                    .comment("Ask for confirmation before installing mods")
                    .define("askBeforeInstall", true);

            ignoredMods = builder
                    .comment("List of mod IDs to ignore during updates")
                    .defineList("ignoredMods", List.of(), o -> o instanceof String);
        }
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading event) {
        TIPUAMod.LOGGER.info("TIPUA config loaded");
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading event) {
        TIPUAMod.LOGGER.info("TIPUA config reloaded");
    }
}