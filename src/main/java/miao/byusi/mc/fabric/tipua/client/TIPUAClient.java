package miao.byusi.mc.fabric.tipua.client;

import miao.byusi.mc.fabric.tipua.config.ClientConfig;
import net.fabricmc.api.ClientModInitializer;

public class TIPUAClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        ClientEventHandler.register();
    }
}