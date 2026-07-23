package miao.byusi.mc.fabric.tipua.client;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class ClientEventHandler {
    private static boolean hasTriggeredCheck = false;

    public static void register() {
        ClientNetworkManager.initialize();

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            onClientTick();
        });
    }

    private static void onClientTick() {
        if (hasTriggeredCheck) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null) {
            String screenName = minecraft.screen.getClass().getSimpleName();
            if (screenName.equals("TitleScreen")) {
                hasTriggeredCheck = true;
                TIPUAMod.LOGGER.info("Entered main menu, starting update check");
                ClientUpdateManager.checkUpdateOnMainMenu();
            }
        }
    }
}
