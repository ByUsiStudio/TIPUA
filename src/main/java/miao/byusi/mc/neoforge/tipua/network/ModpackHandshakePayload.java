package miao.byusi.mc.neoforge.tipua.network;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModpackHandshakePayload(String modpackHash, String serverAddress, int httpPort) implements CustomPacketPayload {
    public static final Type<ModpackHandshakePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TIPUAMod.MOD_ID, "handshake"));
    public static final StreamCodec<FriendlyByteBuf, ModpackHandshakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModpackHandshakePayload::modpackHash,
            ByteBufCodecs.STRING_UTF8, ModpackHandshakePayload::serverAddress,
            ByteBufCodecs.VAR_INT, ModpackHandshakePayload::httpPort,
            ModpackHandshakePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModpackHandshakePayload payload, Context context) {
        context.enqueueWork(() -> {
            TIPUAMod.LOGGER.info("Received modpack handshake: hash={}, server={}:{}",
                    payload.modpackHash, payload.serverAddress, payload.httpPort);

            miao.byusi.mc.neoforge.tipua.client.ModpackClient.onHandshake(payload);
        });
        context.setPacketHandled(true);
    }
}