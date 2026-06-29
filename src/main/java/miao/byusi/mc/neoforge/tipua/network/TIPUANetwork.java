package miao.byusi.mc.neoforge.tipua.network;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class TIPUANetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TIPUAMod.MOD_ID);

        registrar.playToClient(ModpackHandshakePayload.TYPE,
                ModpackHandshakePayload.STREAM_CODEC,
                ModpackHandshakePayload::handle);

        registrar.playToServer(ModpackRequestPayload.TYPE,
                ModpackRequestPayload.STREAM_CODEC,
                ModpackRequestPayload::handle);

        registrar.playToClient(ModpackManifestPayload.TYPE,
                ModpackManifestPayload.STREAM_CODEC,
                ModpackManifestPayload::handle);

        registrar.playToClient(ModpackUpdatePayload.TYPE,
                ModpackUpdatePayload.STREAM_CODEC,
                ModpackUpdatePayload::handle);

        TIPUAMod.LOGGER.info("TIPUA network payloads registered");
    }
}