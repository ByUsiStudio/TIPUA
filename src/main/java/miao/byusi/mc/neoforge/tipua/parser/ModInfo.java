package miao.byusi.mc.neoforge.tipua.parser;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ModInfo(String id, String name, String version, String fileName, String fileHash, String downloadUrl, boolean required) {
    public static final StreamCodec<FriendlyByteBuf, ModInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ModInfo::id,
            ByteBufCodecs.STRING_UTF8, ModInfo::name,
            ByteBufCodecs.STRING_UTF8, ModInfo::version,
            ByteBufCodecs.STRING_UTF8, ModInfo::fileName,
            ByteBufCodecs.STRING_UTF8, ModInfo::fileHash,
            ByteBufCodecs.STRING_UTF8, ModInfo::downloadUrl,
            ByteBufCodecs.BOOL, ModInfo::required,
            ModInfo::new
    );

    @Override
    public String toString() {
        return "ModInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", fileName='" + fileName + '\'' +
                '}';
    }
}