package miao.byusi.mc.neoforge.tipua.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.ServerConfig;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerHttpManager {
    private static HttpServer httpServer;
    private static int currentPort = -1;
    private static boolean initialized = false;

    public static void initialize() {
        startHttpServer();
        initialized = true;
    }

    public static void reload() {
        if (!initialized) {
            TIPUAMod.LOGGER.warn("HTTP服务器尚未初始化，跳过重载 / HTTP server not initialized, skipping reload");
            return;
        }

        int newPort = ServerConfig.getHttpPort();

        if (currentPort != newPort) {
            TIPUAMod.LOGGER.info("端口配置已变更，重启HTTP服务器: {} -> {} / Port config changed, restarting HTTP server: {} -> {}", currentPort, newPort, currentPort, newPort);
            stop();
            startHttpServer();
        } else {
            TIPUAMod.LOGGER.info("HTTP服务器配置已重载（端口未变更） / HTTP server config reloaded (port unchanged)");
        }
    }

    private static void startHttpServer() {
        try {
            int port = ServerConfig.getHttpPort();
            currentPort = port;
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);

            httpServer.createContext("/version", new VersionHandler());
            httpServer.createContext("/modrinth.index.json", new IndexHandler());
            httpServer.createContext("/data.zip", new DataZipHandler());

            httpServer.setExecutor(null);
            httpServer.start();

            TIPUAMod.LOGGER.info("HTTP服务器已启动，端口: {} / HTTP server started on port {}", port, port);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("启动HTTP服务器失败 / Failed to start HTTP server", e);
        }
    }

    private static class VersionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String configVersion = ServerConfig.getServerVersion();

            if (!VersionManager.isValidVersion(configVersion)) {
                TIPUAMod.LOGGER.warn("配置的版本号格式无效: {} / Invalid version format in config: {}", configVersion, configVersion);
                sendResponse(exchange, 200, "");
                return;
            }

            sendResponse(exchange, 200, configVersion);
        }
    }

    private static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path indexFile = Paths.get("config", "tipua", "modrinth.index.json");

            if (!Files.exists(indexFile)) {
                TIPUAMod.LOGGER.warn("modrinth.index.json 文件不存在 / modrinth.index.json not found");
                sendResponse(exchange, 404, "ERROR: modrinth.index.json not found");
                return;
            }

            byte[] content = Files.readAllBytes(indexFile);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();

            TIPUAMod.LOGGER.info("已发送 modrinth.index.json / Sent modrinth.index.json");
        }
    }

    private static class DataZipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path dataZipFile = Paths.get("config", "tipua", "data.zip");

            if (!Files.exists(dataZipFile)) {
                TIPUAMod.LOGGER.info("data.zip 文件不存在，返回404 / data.zip not found, returning 404");
                sendResponse(exchange, 404, "ERROR: data.zip not found");
                return;
            }

            byte[] content = Files.readAllBytes(dataZipFile);
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, content.length);
            OutputStream os = exchange.getResponseBody();
            os.write(content);
            os.close();

            TIPUAMod.LOGGER.info("已发送 data.zip / Sent data.zip");
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            TIPUAMod.LOGGER.info("HTTP服务器已停止 / HTTP server stopped");
        }
    }
}