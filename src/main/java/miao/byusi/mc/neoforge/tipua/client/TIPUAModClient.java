package miao.byusi.mc.neoforge.tipua.client;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = TIPUAMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TIPUAModClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TIPUAMod.LOGGER.info("TIPUA client setup completed");
    }
}