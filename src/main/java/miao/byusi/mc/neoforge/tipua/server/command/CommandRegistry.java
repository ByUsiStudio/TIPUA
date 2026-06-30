package miao.byusi.mc.neoforge.tipua.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.config.ServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.ModLoadingContext;

/**
 * TIPUA命令注册器
 */
@EventBusSubscriber(modid = TIPUAMod.MOD_ID, value = Dist.DEDICATED_SERVER)
public class CommandRegistry {
    
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        // /tipua 命令
        LiteralCommandNode<CommandSourceStack> tipuaNode = Commands.literal("tipua")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal("TIPUA - 整合包自动更新工具"), false);
                context.getSource().sendSuccess(() -> Component.literal("用法: /tipua <version|url|info|reload|modinfo>"), false);
                return 1;
            })
            .build();
        
        // /tipua version - 显示当前版本
        LiteralCommandNode<CommandSourceStack> versionNode = Commands.literal("version")
            .executes(context -> {
                String serverVersion = ServerConfig.getServerVersion();
                context.getSource().sendSuccess(() -> Component.literal("TIPUA 当前版本: " + serverVersion), false);
                return 1;
            })
            .build();
        
        // /tipua url - 显示下载地址
        LiteralCommandNode<CommandSourceStack> urlNode = Commands.literal("url")
            .executes(context -> {
                String url = ServerConfig.getModpackDownloadUrl();
                if (url == null || url.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("TIPUA 下载地址: 未配置"), false);
                } else {
                    context.getSource().sendSuccess(() -> Component.literal("TIPUA 下载地址: " + url), false);
                }
                return 1;
            })
            .build();
        
        // /tipua info - 显示服务器信息
        LiteralCommandNode<CommandSourceStack> infoNode = Commands.literal("info")
            .executes(context -> {
                int port = ServerConfig.getHttpPort();
                String serverVersion = ServerConfig.getServerVersion();
                String downloadUrl = ServerConfig.getModpackDownloadUrl();
                
                context.getSource().sendSuccess(() -> Component.literal("=== TIPUA 服务器信息 ==="), false);
                context.getSource().sendSuccess(() -> Component.literal("HTTP端口: " + port), false);
                context.getSource().sendSuccess(() -> Component.literal("当前版本: " + serverVersion), false);
                context.getSource().sendSuccess(() -> Component.literal("下载地址: " + (downloadUrl.isEmpty() ? "未配置" : downloadUrl)), false);
                context.getSource().sendSuccess(() -> Component.literal("版本端点: http://<地址>:" + port + "/version"), false);
                context.getSource().sendSuccess(() -> Component.literal("地址端点: http://<地址>:" + port + "/download-url"), false);
                return 1;
            })
            .build();
        
        // /tipua reload - 重载配置
        LiteralCommandNode<CommandSourceStack> reloadNode = Commands.literal("reload")
            .executes(context -> {
                TIPUAMod.LOGGER.info("手动重载TIPUA配置 / Manually reloading TIPUA config");
                context.getSource().sendSuccess(() -> Component.literal("TIPUA 配置已重载"), false);
                miao.byusi.mc.neoforge.tipua.server.ServerHttpManager.reload();
                return 1;
            })
            .build();
        
        // /tipua modinfo - 显示模组编译信息
        LiteralCommandNode<CommandSourceStack> modinfoNode = Commands.literal("modinfo")
            .executes(context -> {
                var modInfo = ModLoadingContext.get().getActiveContainer();
                
                context.getSource().sendSuccess(() -> Component.literal("=== TIPUA 模组信息 ==="), false);
                context.getSource().sendSuccess(() -> Component.literal("模组ID: " + TIPUAMod.MOD_ID), false);
                context.getSource().sendSuccess(() -> Component.literal("模组名称: " + TIPUAMod.MOD_NAME), false);
                context.getSource().sendSuccess(() -> Component.literal("版本: " + modInfo.getModInfo().getVersion().toString()), false);
                
                // 显示开发者信息
                context.getSource().sendSuccess(() -> Component.literal("开发者: 北 (BeiAne)"), false);
                context.getSource().sendSuccess(() -> Component.literal("作者: BeiAne"), false);
                
                // 显示详细编译信息
                context.getSource().sendSuccess(() -> Component.literal("--- 编译信息 ---"), false);
                context.getSource().sendSuccess(() -> Component.literal("Java版本: " + System.getProperty("java.version")), false);
                context.getSource().sendSuccess(() -> Component.literal("运行环境: 服务端"), false);
                context.getSource().sendSuccess(() -> Component.literal("构建时间: " + getBuildTimestamp()), false);
                
                context.getSource().sendSuccess(() -> Component.literal("--- 链接 ---"), false);
                context.getSource().sendSuccess(() -> Component.literal("Modrinth: https://modrinth.com/mod/tipua"), false);
                
                return 1;
            })
            .build();
        
        // 注册命令树
        dispatcher.getRoot().addChild(tipuaNode);
        tipuaNode.addChild(versionNode);
        tipuaNode.addChild(urlNode);
        tipuaNode.addChild(infoNode);
        tipuaNode.addChild(reloadNode);
        tipuaNode.addChild(modinfoNode);
        
        TIPUAMod.LOGGER.info("TIPUA命令已注册 / TIPUA commands registered");
    }
    
    private static String getBuildTimestamp() {
        return "北 (BeiAne)";
    }
}
