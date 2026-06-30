package miao.byusi.mc.neoforge.tipua.config;

/**
 * TIPUA 配置入口类
 * 
 * 配置分离说明：
 * - 服务端配置文件：config/tipua-server.toml（仅服务端生成）
 * - 客户端配置文件：config/tipua-client.toml（仅客户端生成）
 * 
 * 旧版 tipua-common.toml 会在首次启动时自动迁移到对应的新配置文件
 */
public class Config {
    // 配置引用通过 ServerConfig 和 ClientConfig 类直接访问
}