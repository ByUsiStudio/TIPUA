package miao.byusi.mc.neoforge.tipua.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.ServerConfig;
import miao.byusi.mc.neoforge.tipua.util.VersionManager;

import java.io.*;
import java.net.InetSocketAddress;

/**
 * 服务端HTTP服务器
 * 提供版本标识和直链下载地址
 */
public class ServerHttpManager {
    private static HttpServer httpServer;
    private static int currentPort = -1;
    private static boolean initialized = false;

    /**
     * 初始化服务端HTTP服务器
     */
    public static void initialize() {
        startHttpServer();
        initialized = true;
    }

    /**
     * 重载HTTP服务器（配置变更时调用）
     */
    public static void reload() {
        if (!initialized) {
            TIPUAMod.LOGGER.warn("HTTP服务器尚未初始化，跳过重载 / HTTP server not initialized, skipping reload");
            return;
        }

        int newPort = ServerConfig.getHttpPort();
        
        // 端口变更需要重启服务器
        if (currentPort != newPort) {
            TIPUAMod.LOGGER.info("端口配置已变更，重启HTTP服务器: {} -> {} / Port config changed, restarting HTTP server: {} -> {}", currentPort, newPort, currentPort, newPort);
            stop();
            startHttpServer();
        } else {
            TIPUAMod.LOGGER.info("HTTP服务器配置已重载（端口未变更） / HTTP server config reloaded (port unchanged)");
        }
    }

    /**
     * 启动HTTP服务器
     */
    private static void startHttpServer() {
        try {
            int port = ServerConfig.getHttpPort();
            currentPort = port;
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);

            httpServer.createContext("/version", new VersionHandler());
            httpServer.createContext("/download-url", new DownloadUrlHandler());

            httpServer.setExecutor(null);
            httpServer.start();

            TIPUAMod.LOGGER.info("HTTP服务器已启动，端口: {} / HTTP server started on port {}", port, port);
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("启动HTTP服务器失败 / Failed to start HTTP server", e);
        }
    }

    /**
     * 版本标识处理器
     */
    private static class VersionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 直接读取配置中的版本号，支持动态更新
            String configVersion = ServerConfig.getServerVersion();
            
            if (!VersionManager.isValidVersion(configVersion)) {
                TIPUAMod.LOGGER.warn("配置的版本号格式无效: {} / Invalid version format in config: {}", configVersion, configVersion);
                sendResponse(exchange, 200, "");
                return;
            }

            sendResponse(exchange, 200, configVersion);
        }
    }

    /**
     * 下载地址处理器（返回直链）
     */
    private static class DownloadUrlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String configuredUrl = ServerConfig.getModpackDownloadUrl();
            
            if (configuredUrl == null || configuredUrl.isEmpty()) {
                TIPUAMod.LOGGER.warn("未配置整合包直链下载地址 / Modpack direct download URL not configured");
                sendResponse(exchange, 500, "ERROR: 未配置下载地址 / Download URL not configured");
                return;
            }

            if (!isValidUrl(configuredUrl)) {
                TIPUAMod.LOGGER.warn("配置的直链格式无效: {} / Invalid direct URL format: {}", configuredUrl);
                sendResponse(exchange, 500, "ERROR: 无效的下载地址格式 / Invalid download URL format");
                return;
            }

            TIPUAMod.LOGGER.info("返回直链下载地址: {} / Sent direct download URL: {}", configuredUrl, configuredUrl);
            sendResponse(exchange, 200, configuredUrl);
        }

        private boolean isValidUrl(String url) {
            if (url == null || url.isEmpty()) {
                return false;
            }
            try {
                java.net.URL urlObj = new java.net.URL(url);
                String protocol = urlObj.getProtocol();
                return "http".equals(protocol) || "https".equals(protocol);
            } catch (java.net.MalformedURLException e) {
                return false;
            }
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

    /**
     * 停止HTTP服务器
     */
    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            TIPUAMod.LOGGER.info("HTTP服务器已停止 / HTTP server stopped");
        }
    }
}