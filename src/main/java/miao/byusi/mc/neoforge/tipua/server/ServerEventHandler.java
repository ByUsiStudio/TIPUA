package miao.byusi.mc.neoforge.tipua.server;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = TIPUAMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class ServerEventHandler {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        TIPUAMod.LOGGER.info("TIPUA server starting");
        ModpackServer.initialize();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TIPUAMod.LOGGER.info("TIPUA server stopping");
        ModpackServer.stop();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ModpackServer.onPlayerJoin(event);
    }
}