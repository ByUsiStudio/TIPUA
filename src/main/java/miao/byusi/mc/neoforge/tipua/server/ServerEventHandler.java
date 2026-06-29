package miao.byusi.mc.neoforge.tipua.server;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * 服务端事件处理器
 */
@EventBusSubscriber(modid = TIPUAMod.MOD_ID, value = Dist.DEDICATED_SERVER)
public class ServerEventHandler {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TIPUAMod.LOGGER.info("服务端启动，初始化HTTP服务器 / Server starting, initializing HTTP server");
        ServerHttpManager.initialize();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TIPUAMod.LOGGER.info("服务端停止，关闭HTTP服务器 / Server stopping, closing HTTP server");
        ServerHttpManager.stop();
    }
}