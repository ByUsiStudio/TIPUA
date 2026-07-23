package miao.byusi.mc.fabric.tipua;

import miao.byusi.mc.fabric.tipua.config.ServerConfig;
import miao.byusi.mc.fabric.tipua.util.VersionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class TIPUAMod implements ModInitializer {
    public static final String MOD_ID = "tipua";
    public static final String MOD_NAME = "TIPUA";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), MOD_ID);
    public static final File MODPACK_DIR = new File(FabricLoader.getInstance().getGameDir().toFile(), "modpacks");

    @Override
    public void onInitialize() {
        LOGGER.info("TIPUA loaded - The Integration Package Updates Automatically");

        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
            LOGGER.info("Config directory created: {}", CONFIG_DIR.getAbsolutePath());
        }
        if (!MODPACK_DIR.exists()) {
            MODPACK_DIR.mkdirs();
            LOGGER.info("Modpack directory created: {}", MODPACK_DIR.getAbsolutePath());
        }

        String localVersion = VersionManager.getLocalVersion();
        LOGGER.info("Local version: {}", localVersion.isEmpty() ? "none" : localVersion);

        ServerConfig.load();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Server starting, initializing HTTP server");
            miao.byusi.mc.fabric.tipua.server.ServerHttpManager.initialize();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Server stopping, closing HTTP server");
            miao.byusi.mc.fabric.tipua.server.ServerHttpManager.stop();
        });
    }
}