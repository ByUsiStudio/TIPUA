package miao.byusi.mc.neoforge.tipua.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.Config;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 服务端HTTP服务器
 * 提供版本标识和ZIP下载
 */
public class ServerHttpManager {
    private static HttpServer httpServer;
    private static Timer checkTimer;
    private static String currentVersion = "";
    private static File zipFile;

    /**
     * 初始化服务端HTTP服务器
     */
    public static void initialize() {
        // 初始化ZIP文件路径
        zipFile = new File(Config.SERVER.zipPath.get());
        if (!zipFile.isAbsolute()) {
            zipFile = new File(TIPUAMod.MODPACK_DIR, Config.SERVER.zipPath.get());
        }

        TIPUAMod.LOGGER.info("ZIP文件路径: {} / ZIP file path: {}", zipFile.getAbsolutePath(), zipFile.getAbsolutePath());

        // 启动HTTP服务器
        if (Config.SERVER.enableFileServer.get()) {
            startHttpServer();
        }

        // 启动定时检查
        startVersionCheck();
    }

    /**
     * 启动HTTP服务器
     */
    private static void startHttpServer() {
        try {
            int port = Config.SERVER.httpPort.get();
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);

            // 版本标识接口
            httpServer.createContext("/version", new VersionHandler());

            // ZIP下载接口
            httpServer.createContext("/modpack.zip", new ModpackHandler());

            httpServer.setExecutor(null);
            httpServer.start();

            TIPUAMod.LOGGER.info("HTTP服务器已启动，端口: {} / HTTP server started on port {}", port, port);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("启动HTTP服务器失败 / Failed to start HTTP server", e);
        }
    }

    /**
     * 启动定时版本检查
     */
    private static void startVersionCheck() {
        // 初始检查
        checkVersion();

        // 定时检查
        if (checkTimer != null) {
            checkTimer.cancel();
        }

        int interval = Config.SERVER.checkIntervalSeconds.get() * 1000;
        checkTimer = new Timer(true);
        checkTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkVersion();
            }
        }, interval, interval);
    }

    /**
     * 检查ZIP版本
     */
    private static void checkVersion() {
        if (!zipFile.exists()) {
            TIPUAMod.LOGGER.warn("ZIP文件不存在: {} / ZIP file not found: {}", zipFile.getAbsolutePath(), zipFile.getAbsolutePath());
            return;
        }

        String newVersion = VersionManager.calculateFileHash(zipFile);
        if (!newVersion.equals(currentVersion)) {
            currentVersion = newVersion;
            TIPUAMod.LOGGER.info("ZIP已更新，新版本标识: {} / ZIP updated, new version: {}", currentVersion, currentVersion);
        }
    }

    /**
     * 版本标识处理器
     */
    private static class VersionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = currentVersion;
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();

            TIPUAMod.LOGGER.debug("返回版本标识: {} / Sent version: {}", response, response);
        }
    }

    /**
     * ZIP下载处理器
     */
    private static class ModpackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!zipFile.exists()) {
                String error = "ZIP文件不存在 / ZIP file not found";
                exchange.sendResponseHeaders(404, error.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(error.getBytes());
                os.close();
                return;
            }

            byte[] data = Files.readAllBytes(zipFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"modpack.zip\"");
            exchange.sendResponseHeaders(200, data.length);
            OutputStream os = exchange.getResponseBody();
            os.write(data);
            os.close();

            TIPUAMod.LOGGER.info("ZIP已发送，大小: {} bytes / ZIP sent, size: {} bytes", data.length, data.length);
        }
    }

    /**
     * 停止HTTP服务器
     */
    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            TIPUAMod.LOGGER.info("HTTP服务器已停止 / HTTP server stopped");
        }
        if (checkTimer != null) {
            checkTimer.cancel();
        }
    }
}