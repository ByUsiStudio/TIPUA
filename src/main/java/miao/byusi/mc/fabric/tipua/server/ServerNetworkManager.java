package miao.byusi.mc.fabric.tipua.server;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerNetworkManager {
    public static final Identifier INDEX_REQUEST = new Identifier(TIPUAMod.MOD_ID, "index_request");
    public static final Identifier INDEX_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "index_response");
    public static final Identifier FILE_REQUEST = new Identifier(TIPUAMod.MOD_ID, "file_request");
    public static final Identifier FILE_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "file_response");
    public static final Identifier DATAZIP_REQUEST = new Identifier(TIPUAMod.MOD_ID, "datazip_request");
    public static final Identifier DATAZIP_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "datazip_response");

    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        ServerPlayNetworking.registerGlobalReceiver(INDEX_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                try {
                    Path indexFile = Paths.get("config", TIPUAMod.MOD_ID, "modrinth.index.json");
                    if (!Files.exists(indexFile)) {
                        // send empty response
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeBoolean(false);
                        ServerPlayNetworking.send(player, INDEX_RESPONSE, out);
                        TIPUAMod.LOGGER.warn("modrinth.index.json not found for network response");
                        return;
                    }

                    byte[] content = Files.readAllBytes(indexFile);
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeBoolean(true);
                    out.writeInt(content.length);
                    out.writeBytes(content);
                    ServerPlayNetworking.send(player, INDEX_RESPONSE, out);
                    TIPUAMod.LOGGER.info("Sent modrinth.index.json to {}", player.getName().getString());
                } catch (IOException e) {
                    TIPUAMod.LOGGER.error("Failed to send index via network", e);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(FILE_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                try {
                    String path = buf.readString(32767);
                    Path gameDir = FabricLoader.getInstance().getGameDir().toPath();

                    Path resolved = gameDir.resolve(path).normalize();

                    if (!Files.exists(resolved)) {
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeBoolean(false);
                        out.writeString(path);
                        ServerPlayNetworking.send(player, FILE_RESPONSE, out);
                        TIPUAMod.LOGGER.warn("Requested file not found: {}", path);
                        return;
                    }

                    byte[] content = Files.readAllBytes(resolved);
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeBoolean(true);
                    out.writeString(path);
                    out.writeInt(content.length);
                    out.writeBytes(content);
                    ServerPlayNetworking.send(player, FILE_RESPONSE, out);
                    TIPUAMod.LOGGER.info("Sent file via network: {} to {}", path, player.getName().getString());
                } catch (IOException e) {
                    TIPUAMod.LOGGER.error("Failed to send file via network", e);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DATAZIP_REQUEST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                try {
                    Path dataZip = Paths.get("config", TIPUAMod.MOD_ID, "data.zip");
                    if (!Files.exists(dataZip)) {
                        PacketByteBuf out = PacketByteBufs.create();
                        out.writeBoolean(false);
                        ServerPlayNetworking.send(player, DATAZIP_RESPONSE, out);
                        TIPUAMod.LOGGER.info("data.zip not found when requested via network");
                        return;
                    }

                    byte[] content = Files.readAllBytes(dataZip);
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeBoolean(true);
                    out.writeInt(content.length);
                    out.writeBytes(content);
                    ServerPlayNetworking.send(player, DATAZIP_RESPONSE, out);
                    TIPUAMod.LOGGER.info("Sent data.zip to {}", player.getName().getString());
                } catch (IOException e) {
                    TIPUAMod.LOGGER.error("Failed to send data.zip via network", e);
                }
            });
        });

        TIPUAMod.LOGGER.info("ServerNetworkManager initialized");
    }

    public static void stop() {
        // no-op for now
        TIPUAMod.LOGGER.info("ServerNetworkManager stopped");
    }
}
