package miao.byusi.mc.fabric.tipua.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import miao.byusi.mc.fabric.tipua.TIPUAMod;
import miao.byusi.mc.fabric.tipua.config.ServerConfig;
import miao.byusi.mc.fabric.tipua.util.VersionManager;

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
            TIPUAMod.LOGGER.warn("HTTP server not initialized, skipping reload");
            return;
        }

        int newPort = ServerConfig.getHttpPort();

        if (currentPort != newPort) {
            TIPUAMod.LOGGER.info("Port config changed, restarting HTTP server: {} -> {}", currentPort, newPort);
            stop();
            startHttpServer();
        } else {
            TIPUAMod.LOGGER.info("HTTP server config reloaded (port unchanged)");
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

            TIPUAMod.LOGGER.info("HTTP server started on port {}", port);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to start HTTP server", e);
        }
    }

    private static class VersionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String configVersion = ServerConfig.getServerVersion();

            if (!VersionManager.isValidVersion(configVersion)) {
                TIPUAMod.LOGGER.warn("Invalid version format in config: {}", configVersion);
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
                TIPUAMod.LOGGER.warn("modrinth.index.json not found");
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

            TIPUAMod.LOGGER.info("Sent modrinth.index.json");
        }
    }

    private static class DataZipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path dataZipFile = Paths.get("config", "tipua", "data.zip");

            if (!Files.exists(dataZipFile)) {
                TIPUAMod.LOGGER.info("data.zip not found, returning 404");
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

            TIPUAMod.LOGGER.info("Sent data.zip");
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
            TIPUAMod.LOGGER.info("HTTP server stopped");
        }
    }
}