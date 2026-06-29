package miao.byusi.mc.neoforge.tipua.network;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ModpackUpdatePayload(String message, int progress, boolean completed, boolean success) implements CustomPacketPayload {
    public static final Type<ModpackUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TIPUAMod.MOD_ID, "update"));
    public static final StreamCodec<FriendlyByteBuf, ModpackUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModpackUpdatePayload::message,
            ByteBufCodecs.VAR_INT, ModpackUpdatePayload::progress,
            ByteBufCodecs.BOOL, ModpackUpdatePayload::completed,
            ByteBufCodecs.BOOL, ModpackUpdatePayload::success,
            ModpackUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModpackUpdatePayload payload, Context context) {
        context.enqueueWork(() -> {
            TIPUAMod.LOGGER.info("Modpack update: {} ({}%, completed={}, success={})",
                    payload.message, payload.progress, payload.completed, payload.success);

            miao.byusi.mc.neoforge.tipua.client.ModpackClient.onUpdate(payload);
        });
        context.setPacketHandled(true);
    }
}