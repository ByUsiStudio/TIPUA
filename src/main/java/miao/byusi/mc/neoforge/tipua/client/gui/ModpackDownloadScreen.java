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
    
    private long extractionCurrent = 0;
    private long extractionTotal = 0;
    private String extractingFile = "";
    
    private boolean isComplete = false;
    private boolean hasError = false;
    private String errorMessage = "";
    private boolean showRestart = false;
    
    private Button restartButton;
    
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
        
        restartButton = Button.builder(
                Component.translatable("tipua.gui.download.restart"),
                button -> {
                    this.minecraft.stop();
                }
        ).bounds(centerX - 100, centerY + 50, 200, 25).build();
        
        this.addRenderableWidget(restartButton);
        restartButton.visible = false;
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        
        guiGraphics.drawCenteredString(this.font, TITLE, centerX, centerY - 80, 0xFFFFFF);
        
        Component versionText = Component.translatable("tipua.gui.download.version", this.version);
        guiGraphics.drawCenteredString(this.font, versionText, centerX, centerY - 60, 0xAAAAAA);
        
        guiGraphics.drawCenteredString(this.font, Component.literal(this.status), centerX, centerY - 35, 0xFFFFFF);
        
        int progressBarWidth = 400;
        int progressBarHeight = 20;
        int progressBarX = centerX - progressBarWidth / 2;
        int progressBarY = centerY - 5;
        
        guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight, 0xFF202020);
        
        // 手动绘制边框
        int borderColor = 0xFF606060;
        guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + 1, borderColor); // 上边
        guiGraphics.fill(progressBarX, progressBarY + progressBarHeight - 1, progressBarX + progressBarWidth, progressBarY + progressBarHeight, borderColor); // 下边
        guiGraphics.fill(progressBarX, progressBarY, progressBarX + 1, progressBarY + progressBarHeight, borderColor); // 左边
        guiGraphics.fill(progressBarX + progressBarWidth - 1, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight, borderColor); // 右边
        
        if (totalBytes > 0) {
            double progress = (double) downloadedBytes / totalBytes;
            int progressWidth = (int) (progress * progressBarWidth);
            int progressColor = 0xFF55FF55;
            if (progress < 0.3) progressColor = 0xFFFF5555;
            else if (progress < 0.7) progressColor = 0xFFFFFF55;
            
            guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressWidth, progressBarY + progressBarHeight, progressColor);
            
            int percentage = (int) (progress * 100);
            Component percentageText = Component.literal(percentage + "%");
            guiGraphics.drawCenteredString(this.font, percentageText, centerX, centerY + 25, 0xFFFFFF);
            
            String downloadedStr = formatBytes(downloadedBytes);
            String totalStr = formatBytes(totalBytes);
            Component sizeText = Component.literal(downloadedStr + " / " + totalStr);
            guiGraphics.drawCenteredString(this.font, sizeText, centerX, centerY + 40, 0xAAAAAA);
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            if (elapsedTime > 0) {
                long speed = downloadedBytes / (elapsedTime / 1000);
                Component speedText = Component.translatable("tipua.gui.download.speed", formatBytes(speed) + "/s");
                guiGraphics.drawCenteredString(this.font, speedText, centerX, centerY + 55, 0xAAAAAA);
                
                long remainingBytes = totalBytes - downloadedBytes;
                if (speed > 0) {
                    long remainingTime = remainingBytes / speed;
                    Component timeText = Component.translatable("tipua.gui.download.remaining", formatTime(remainingTime));
                    guiGraphics.drawCenteredString(this.font, timeText, centerX, centerY + 70, 0xAAAAAA);
                }
            }
        } else if (extractionTotal > 0) {
            double progress = (double) extractionCurrent / extractionTotal;
            int progressWidth = (int) (progress * progressBarWidth);
            int progressColor = 0xFF55AAFF;
            guiGraphics.fill(progressBarX, progressBarY, progressBarX + progressWidth, progressBarY + progressBarHeight, progressColor);
            
            int percentage = (int) (progress * 100);
            Component percentageText = Component.literal(percentage + "%");
            guiGraphics.drawCenteredString(this.font, percentageText, centerX, centerY + 25, 0xFFFFFF);
            
            Component fileText = Component.literal(extractingFile);
            guiGraphics.drawCenteredString(this.font, fileText, centerX, centerY + 40, 0xAAAAAA);
            
            Component countText = Component.literal(extractionCurrent + " / " + extractionTotal);
            guiGraphics.drawCenteredString(this.font, countText, centerX, centerY + 55, 0xAAAAAA);
        }
        
        if (hasError) {
            Component errorText = Component.literal(errorMessage).withStyle(ChatFormatting.RED);
            guiGraphics.drawCenteredString(this.font, errorText, centerX, centerY + 40, 0xFF5555);
            
            Button backButton = Button.builder(
                    Component.translatable("gui.back"),
                    button -> this.minecraft.setScreen(this.parent)
            ).bounds(centerX - 50, centerY + 70, 100, 20).build();
            backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        
        if (showRestart) {
            restartButton.visible = true;
            Component completeText = Component.translatable("tipua.gui.download.complete").withStyle(ChatFormatting.GREEN);
            guiGraphics.drawCenteredString(this.font, completeText, centerX, centerY + 25, 0x55FF55);
            
            Component restartHint = Component.translatable("tipua.gui.download.restart_hint");
            guiGraphics.drawCenteredString(this.font, restartHint, centerX, centerY + 85, 0xAAAAAA);
        } else {
            restartButton.visible = false;
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    public void updateDownloadProgress(long downloadedBytes, long totalBytes) {
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.extractionCurrent = 0;
        this.extractionTotal = 0;
        this.status = Component.translatable("tipua.gui.download.downloading").getString();
    }
    
    public void updateExtractionProgress(String relativePath, long current, long total) {
        this.extractionCurrent = current;
        this.extractionTotal = total;
        this.extractingFile = relativePath;
        this.downloadedBytes = 0;
        this.totalBytes = 0;
        this.status = Component.translatable("tipua.gui.download.extracting").getString();
    }
    
    public void setExtractionComplete() {
        this.status = Component.translatable("tipua.gui.download.extract_complete").getString();
    }
    
    public void showRestartButton() {
        this.showRestart = true;
        this.status = Component.translatable("tipua.gui.download.update_complete").getString();
    }
    
    public void setError(String errorMessage) {
        this.hasError = true;
        this.errorMessage = errorMessage;
        this.status = Component.translatable("tipua.gui.download.error").getString();
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return String.format("%dm %ds", minutes, remainingSeconds);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return String.format("%dh %dm", hours, remainingMinutes);
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return hasError;
    }
}
