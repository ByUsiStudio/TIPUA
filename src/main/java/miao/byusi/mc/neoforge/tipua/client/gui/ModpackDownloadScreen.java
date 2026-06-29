package miao.byusi.mc.neoforge.tipua.client.gui;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 整合包下载进度界面
 * Modpack download progress screen
 */
public class ModpackDownloadScreen extends Screen {
    private static final Component TITLE = Component.translatable("tipua.gui.download.title").withStyle(ChatFormatting.BOLD);
    
    private final Screen parent;
    private final String version;
    private final Runnable onCancel;
    
    private long downloadedBytes = 0;
    private long totalBytes = 0;
    private long startTime = 0;
    private String status = Component.translatable("tipua.gui.download.starting").getString();
    private boolean isComplete = false;
    private boolean hasError = false;
    private String errorMessage = "";
    
    private Button cancelButton;
    private Button closeButton;
    
    public ModpackDownloadScreen(Screen parent, String version, Runnable onCancel) {
        super(TITLE);
        this.parent = parent;
        this.version = version;
        this.onCancel = onCancel;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // 取消按钮
        this.cancelButton = Button.builder(
                Component.translatable("gui.cancel"),
                button -> {
                    onCancel.run();
                    this.minecraft.setScreen(this.parent);
                }
        ).bounds(centerX - 50, centerY + 60, 100, 20).build();
        
        this.addRenderableWidget(this.cancelButton);
        
        // 关闭按钮（完成或错误时显示）
        this.closeButton = Button.builder(
                Component.translatable("gui.done"),
                button -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX - 50, centerY + 60, 100, 20).build();
        
        this.addRenderableWidget(this.closeButton);
        this.closeButton.visible = false;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制半透明背景
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        // 标题
        guiGraphics.drawCenteredString(this.font, TITLE, centerX, centerY - 80, 0xFFFFFF);
        
        // 版本信息
        Component versionText = Component.translatable("tipua.gui.download.version", this.version);
        guiGraphics.drawCenteredString(this.font, versionText, centerX, centerY - 60, 0xAAAAAA);
        
        // 状态信息
        guiGraphics.drawCenteredString(this.font, Component.literal(this.status), centerX, centerY - 35, 0xFFFFFF);
        
        // 进度条背景
        int progressBarWidth = 300;
        int progressBarHeight = 20;
        int progressBarX = centerX - progressBarWidth / 2;
        int progressBarY = centerY - 5;
        
        guiGraphics.fillGradient(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight, 0xFF000000, 0xFF000000);
        guiGraphics.renderOutline(progressBarX, progressBarY, progressBarWidth, progressBarHeight, 0xFFFFFFFF);
        
        // 进度条前景
        if (totalBytes > 0) {
            double progress = (double) downloadedBytes / totalBytes;
            int progressWidth = (int) (progress * progressBarWidth);
            
            // 根据进度选择颜色
            int progressColor;
            if (progress < 0.3) {
                progressColor = 0xFFFF5555; // 红色
            } else if (progress < 0.7) {
                progressColor = 0xFFFFFF55; // 黄色
            } else {
                progressColor = 0xFF55FF55; // 绿色
            }
            
            guiGraphics.fillGradient(progressBarX, progressBarY, progressBarX + progressWidth, progressBarY + progressBarHeight, progressColor, progressColor);
        }
        
        // 下载统计信息
        if (totalBytes > 0 && !isComplete && !hasError) {
            double progress = (double) downloadedBytes / totalBytes;
            int percentage = (int) (progress * 100);
            Component percentageText = Component.literal(percentage + "%");
            guiGraphics.drawCenteredString(this.font, percentageText, centerX, centerY + 25, 0xFFFFFF);
            
            // 已下载 / 总大小
            String downloadedStr = formatBytes(downloadedBytes);
            String totalStr = formatBytes(totalBytes);
            Component sizeText = Component.literal(downloadedStr + " / " + totalStr);
            guiGraphics.drawCenteredString(this.font, sizeText, centerX, centerY + 40, 0xAAAAAA);
            
            // 下载速度
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > 0) {
                long speed = downloadedBytes / (elapsedTime / 1000);
                Component speedText = Component.translatable("tipua.gui.download.speed", formatBytes(speed) + "/s");
                guiGraphics.drawCenteredString(this.font, speedText, centerX, centerY + 55, 0xAAAAAA);
                
                // 剩余时间
                long remainingBytes = totalBytes - downloadedBytes;
                long remainingTime = remainingBytes / speed;
                Component timeText = Component.translatable("tipua.gui.download.remaining", formatTime(remainingTime));
                guiGraphics.drawCenteredString(this.font, timeText, centerX, centerY + 70, 0xAAAAAA);
            }
        }
        
        // 错误信息
        if (hasError) {
            Component errorText = Component.literal(errorMessage).withStyle(ChatFormatting.RED);
            guiGraphics.drawCenteredString(this.font, errorText, centerX, centerY + 40, 0xFF5555);
        }
        
        // 管理按钮可见性
        if (isComplete || hasError) {
            this.cancelButton.visible = false;
            this.closeButton.visible = true;
        } else {
            this.cancelButton.visible = true;
            this.closeButton.visible = false;
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    /**
     * 更新下载进度
     */
    public void updateProgress(long downloadedBytes, long totalBytes, String status) {
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.status = status;
    }
    
    /**
     * 标记下载完成
     */
    public void setComplete() {
        this.isComplete = true;
        this.status = Component.translatable("tipua.gui.download.complete").getString();
    }
    
    /**
     * 标记下载错误
     */
    public void setError(String errorMessage) {
        this.hasError = true;
        this.errorMessage = errorMessage;
        this.status = Component.translatable("tipua.gui.download.error").getString();
    }
    
    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * 格式化时间
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%dm %ds", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return String.format("%dh %dm", hours, remainingMinutes);
        }
    }
    
    /**
     * 检查是否完成
     */
    public boolean isComplete() {
        return isComplete;
    }
    
    /**
     * 检查是否有错误
     */
    public boolean hasError() {
        return hasError;
    }
}