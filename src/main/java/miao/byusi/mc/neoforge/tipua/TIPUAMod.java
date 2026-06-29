package miao.byusi.mc.neoforge.tipua;

import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.network.TIPUANetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod(TIPUAMod.MOD_ID)
public class TIPUAMod {
    public static final String MOD_ID = "tipua";
    public static final String MOD_NAME = "TIPUA";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final File CONFIG_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), MOD_ID);
    public static final File MODPACK_DIR = new File(FMLPaths.GAMEDIR.get().toFile(), "modpacks");

    public TIPUAMod(IEventBus bus) {
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerNetwork);

        net.neoforged.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }
            if (!MODPACK_DIR.exists()) {
                MODPACK_DIR.mkdirs();
            }
        });
    }

    private void registerNetwork(final RegisterPayloadHandlersEvent event) {
        TIPUANetwork.register(event);
    }
}