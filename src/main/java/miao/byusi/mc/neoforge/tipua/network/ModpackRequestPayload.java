package miao.byusi.mc.neoforge.tipua.network;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModpackRequestPayload(String clientHash) implements CustomPacketPayload {
    public static final Type<ModpackRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TIPUAMod.MOD_ID, "request"));
    public static final StreamCodec<FriendlyByteBuf, ModpackRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModpackRequestPayload::clientHash,
            ModpackRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModpackRequestPayload payload, Context context) {
        context.enqueueWork(() -> {
            TIPUAMod.LOGGER.info("Client requested modpack update, client hash: {}", payload.clientHash);

            miao.byusi.mc.neoforge.tipua.server.ModpackServer.onRequest(payload, context);
        });
        context.setPacketHandled(true);
    }
}