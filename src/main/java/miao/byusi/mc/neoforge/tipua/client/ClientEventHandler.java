package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端事件处理器
 * 在进入主菜单后触发更新检查
 */
public class ClientEventHandler {

    private static boolean hasTriggeredCheck = false;

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientTick);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (hasTriggeredCheck) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null) {
            String screenName = minecraft.screen.getClass().getSimpleName();
            if (screenName.equals("TitleScreen")) {
                hasTriggeredCheck = true;
                TIPUAMod.LOGGER.info("进入主菜单，开始检查更新 / Entered main menu, starting update check");
                ClientUpdateManager.checkUpdateOnMainMenu();
            }
        }
    }
}