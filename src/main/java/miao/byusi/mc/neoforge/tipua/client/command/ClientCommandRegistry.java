package miao.byusi.mc.neoforge.tipua.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.util.RollbackManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.ModLoadingContext;

/**
 * TIPUA客户端命令注册器
 */
@EventBusSubscriber(modid = TIPUAMod.MOD_ID, value = Dist.CLIENT)
public class ClientCommandRegistry {
    
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        CommandNode<CommandSourceStack> existingNode = dispatcher.getRoot().getChild("tipua");
        LiteralCommandNode<CommandSourceStack> tipuaNode;
        
        if (existingNode instanceof LiteralCommandNode) {
            tipuaNode = (LiteralCommandNode<CommandSourceStack>) existingNode;
        } else {
            tipuaNode = Commands.literal("tipua")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("TIPUA - 整合包自动更新工具"), false);
                    context.getSource().sendSuccess(() -> Component.literal("用法: /tipua <rollback|modinfo>"), false);
                    return 1;
                })
                .build();
            dispatcher.getRoot().addChild(tipuaNode);
        }
        
        LiteralCommandNode<CommandSourceStack> rollbackNode = Commands.literal("rollback")
            .executes(context -> {
                if (!RollbackManager.hasRollbackBackup(FMLPaths.GAMEDIR.get())) {
                    context.getSource().sendFailure(Component.literal("没有可用的回滚备份 / No rollback backup available"));
                    return 0;
                }
                
                String rollbackVersion = RollbackManager.getRollbackVersion(FMLPaths.GAMEDIR.get());
                context.getSource().sendSuccess(() -> Component.literal("开始回滚到版本: " + rollbackVersion), false);
                
                boolean success = RollbackManager.performRollback(FMLPaths.GAMEDIR.get());
                
                if (success) {
                    context.getSource().sendSuccess(() -> Component.literal("回滚成功！请重启游戏 / Rollback successful! Please restart the game"), false);
                    return 1;
                } else {
                    context.getSource().sendFailure(Component.literal("回滚失败 / Rollback failed"));
                    return 0;
                }
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
                context.getSource().sendSuccess(() -> Component.literal("运行环境: 客户端"), false);
                context.getSource().sendSuccess(() -> Component.literal("构建时间: " + getBuildTimestamp()), false);
                
                context.getSource().sendSuccess(() -> Component.literal("--- 链接 ---"), false);
                context.getSource().sendSuccess(() -> Component.literal("Modrinth: https://modrinth.com/mod/tipua"), false);
                
                return 1;
            })
            .build();
        
        tipuaNode.addChild(rollbackNode);
        tipuaNode.addChild(modinfoNode);
        
        TIPUAMod.LOGGER.info("TIPUA客户端命令已注册 / TIPUA client commands registered");
    }
    
    private static String getBuildTimestamp() {
        return "北 (BeiAne)";
    }
}
