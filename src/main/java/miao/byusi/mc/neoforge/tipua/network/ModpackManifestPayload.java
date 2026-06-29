package miao.byusi.mc.neoforge.tipua.network;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.parser.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ModpackManifestPayload(List<ModInfo> mods, String modpackHash, boolean requiresUpdate) implements CustomPacketPayload {
    public static final Type<ModpackManifestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TIPUAMod.MOD_ID, "manifest"));
    public static final StreamCodec<FriendlyByteBuf, ModpackManifestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.list(ModInfo.STREAM_CODEC), ModpackManifestPayload::mods,
            ByteBufCodecs.STRING_UTF8, ModpackManifestPayload::modpackHash,
            ByteBufCodecs.BOOL, ModpackManifestPayload::requiresUpdate,
            ModpackManifestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ModpackManifestPayload payload, Context context) {
        context.enqueueWork(() -> {
            TIPUAMod.LOGGER.info("Received modpack manifest: {} mods, requires update: {}",
                    payload.mods.size(), payload.requiresUpdate);

            miao.byusi.mc.neoforge.tipua.client.ModpackClient.onManifest(payload);
        });
        context.setPacketHandled(true);
    }
}