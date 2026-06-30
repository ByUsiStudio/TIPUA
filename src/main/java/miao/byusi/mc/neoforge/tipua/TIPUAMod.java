package miao.byusi.mc.neoforge.tipua;

import miao.byusi.mc.neoforge.tipua.config.ClientConfig;
import miao.byusi.mc.neoforge.tipua.config.ServerConfig;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * TIPUA主模组类
 * 整合包自动更新工具 - The Integration Package Updates Automatically
 */
@Mod(TIPUAMod.MOD_ID)
public class TIPUAMod {
    public static final String MOD_ID = "tipua";
    public static final String MOD_NAME = "TIPUA";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final File CONFIG_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), MOD_ID);
    public static final File MODPACK_DIR = new File(FMLPaths.GAMEDIR.get().toFile(), "modpacks");

    public TIPUAMod(IEventBus bus, ModContainer container) {
        bus.addListener(this::commonSetup);

        // 注册配置 - 根据运行端注册不同的配置文件
        Dist dist = FMLLoader.getDist();
        if (dist.isClient()) {
            container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.getSpec(), "tipua-client.toml");
        } else {
            container.registerConfig(ModConfig.Type.SERVER, ServerConfig.getSpec(), "tipua-server.toml");
        }

        LOGGER.info("TIPUA已加载 - 整合包自动更新工具 / TIPUA loaded - The Integration Package Updates Automatically");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 创建必要目录
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
                LOGGER.info("配置目录已创建: {} / Config directory created: {}", CONFIG_DIR.getAbsolutePath(), CONFIG_DIR.getAbsolutePath());
            }
            if (!MODPACK_DIR.exists()) {
                MODPACK_DIR.mkdirs();
                LOGGER.info("整合包目录已创建: {} / Modpack directory created: {}", MODPACK_DIR.getAbsolutePath(), MODPACK_DIR.getAbsolutePath());
            }

            // 初始化版本管理器（确保版本文件存在）
            String localVersion = VersionManager.getLocalVersion();
            LOGGER.info("本地版本标识: {} / Local version: {}", localVersion.isEmpty() ? "无" : localVersion, localVersion.isEmpty() ? "none" : localVersion);

            // 客户端侧注册事件处理器
            if (FMLLoader.getDist().isClient()) {
                registerClientEvents();
            }
        });
    }

    private void registerClientEvents() {
        try {
            Class<?> clientHandler = Class.forName("miao.byusi.mc.neoforge.tipua.client.ClientEventHandler");
            java.lang.reflect.Method registerMethod = clientHandler.getMethod("register");
            registerMethod.invoke(null);
            LOGGER.info("客户端事件处理器已注册 / Client event handler registered");
        } catch (Exception e) {
            LOGGER.warn("客户端事件处理器注册失败 / Failed to register client event handler", e);
        }
    }
}