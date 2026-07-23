package miao.byusi.mc.fabric.tipua.client;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
// no alias imports

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNetworkManager {
    public static final Identifier INDEX_REQUEST = new Identifier(TIPUAMod.MOD_ID, "index_request");
    public static final Identifier INDEX_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "index_response");
    public static final Identifier FILE_REQUEST = new Identifier(TIPUAMod.MOD_ID, "file_request");
    public static final Identifier FILE_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "file_response");
    public static final Identifier DATAZIP_REQUEST = new Identifier(TIPUAMod.MOD_ID, "datazip_request");
    public static final Identifier DATAZIP_RESPONSE = new Identifier(TIPUAMod.MOD_ID, "datazip_response");

    private static final Map<Integer, CompletableFuture<byte[]>> pending = new ConcurrentHashMap<>();
    private static int nextId = 1;

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(INDEX_RESPONSE, (client, handler, buf, responseSender) -> {
            boolean ok = buf.readBoolean();
            if (!ok) return;
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            // complete a special index future keyed by 0
            CompletableFuture<byte[]> f = pending.remove(0);
            if (f != null) f.complete(data);
        });

        ClientPlayNetworking.registerGlobalReceiver(FILE_RESPONSE, (client, handler, buf, responseSender) -> {
            boolean ok = buf.readBoolean();
            String path = buf.readString(32767);
            if (!ok) {
                CompletableFuture<byte[]> f = pending.remove(-1);
                if (f != null) f.complete(null);
                return;
            }
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            // for simplicity complete special file future keyed by -1
            CompletableFuture<byte[]> f = pending.remove(-1);
            if (f != null) f.complete(data);
        });

        ClientPlayNetworking.registerGlobalReceiver(DATAZIP_RESPONSE, (client, handler, buf, responseSender) -> {
            boolean ok = buf.readBoolean();
            if (!ok) {
                CompletableFuture<byte[]> f = pending.remove(-2);
                if (f != null) f.complete(null);
                return;
            }
            int len = buf.readInt();
            byte[] data = new byte[len];
            buf.readBytes(data);
            CompletableFuture<byte[]> f = pending.remove(-2);
            if (f != null) f.complete(data);
        });

        TIPUAMod.LOGGER.info("ClientNetworkManager initialized");
    }

    public static byte[] requestIndex() {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(0, future);
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(INDEX_REQUEST, buf);
        try {
            return future.get();
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("Index request failed", e);
            return null;
        }
    }

    public static byte[] requestFile(String path) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(-1, future);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(path);
        ClientPlayNetworking.send(FILE_REQUEST, buf);
        try {
            return future.get();
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("File request failed", e);
            return null;
        }
    }

    public static byte[] requestDataZip() {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pending.put(-2, future);
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(DATAZIP_REQUEST, buf);
        try {
            return future.get();
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("DataZip request failed", e);
            return null;
        }
    }
}
