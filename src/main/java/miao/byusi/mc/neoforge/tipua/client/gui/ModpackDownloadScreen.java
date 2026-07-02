package miao.byusi.mc.neoforge.tipua.client.gui;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ModpackDownloadScreen extends Screen {
    private static final Component TITLE = Component.translatable("tipua.gui.download.title").withStyle(ChatFormatting.BOLD);

    private final Screen parent;
    private final String version;
    private final Runnable onCancel;

    private long downloadedBytes = 0;
    private long totalBytes = 0;
    private String status = Component.translatable("tipua.gui.download.starting").getString();

    private static final int SPEED_SAMPLE_WINDOW_MS = 3000;
    private static final int MAX_SAMPLES = 10;
    private final long[] sampleTimes = new long[MAX_SAMPLES];
    private final long[] sampleBytes = new long[MAX_SAMPLES];
    private int sampleCount = 0;
    private int sampleHead = 0;

    private long extractionCurrent = 0;
    private long extractionTotal = 0;
    private String extractingFile = "";

    private int currentFileIndex = 0;
    private int totalFiles = 0;
    private String currentFileName = "";
    private long currentFileDownloaded = 0;
    private long currentFileTotal = 0;

    private boolean isComplete = false;
    private boolean hasError = false;
    private String errorMessage = "";
    private boolean showRestart = false;
    private boolean showExit = false;

    private Button restartButton;
    private Button exitButton;
    private Button rollbackButton;

    private final List<String> logEntries = new ArrayList<>();
    private float logScrollOffset = 0;
    private static final int MAX_LOG_LINES = 200;

    private final Runnable onRollback;

    public ModpackDownloadScreen(Screen parent, String version, Runnable onCancel, Runnable onRollback) {
        super(TITLE);
        this.parent = parent;
        this.version = version;
        this.onCancel = onCancel;
        this.onRollback = onRollback;
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

        exitButton = Button.builder(
                Component.translatable("tipua.gui.download.exit"),
                button -> {
                    this.minecraft.stop();
                }
        ).bounds(centerX - 75, centerY + 50, 150, 25).build();

        rollbackButton = Button.builder(
                Component.translatable("tipua.rollback.auto"),
                button -> {
                    if (onRollback != null) {
                        onRollback.run();
                    }
                }
        ).bounds(centerX - 100, centerY + 80, 200, 25).build();

        this.addRenderableWidget(restartButton);
        this.addRenderableWidget(exitButton);
        this.addRenderableWidget(rollbackButton);
        restartButton.visible = false;
        exitButton.visible = false;
        rollbackButton.visible = false;

        addLogEntry("info", "[系统] TIPUA 更新管理器已启动");
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int contentWidth = Math.min(this.width - 40, 600);
        int contentHeight = Math.min(this.height - 80, 400);

        guiGraphics.drawCenteredString(this.font, TITLE, centerX, centerY - contentHeight / 2 + 20, 0xFFFFFF);

        Component versionText = Component.translatable("tipua.gui.download.version", this.version);
        guiGraphics.drawCenteredString(this.font, versionText, centerX, centerY - contentHeight / 2 + 45, 0xAAAAAA);

        guiGraphics.drawCenteredString(this.font, Component.literal(this.status), centerX, centerY - contentHeight / 2 + 70, 0xFFFFFF);

        int progressBarWidth = contentWidth;
        int progressBarHeight = 20;
        int mainProgressBarY = centerY - contentHeight / 2 + 100;

        if (totalFiles > 0) {
            String fileProgress = String.format("文件 %d/%d: %s", currentFileIndex, totalFiles, currentFileName);
            guiGraphics.drawCenteredString(this.font, Component.literal(fileProgress), centerX, mainProgressBarY - 25, 0xAAAAAA);
        }

        guiGraphics.fill(centerX - progressBarWidth / 2, mainProgressBarY, centerX + progressBarWidth / 2, mainProgressBarY + progressBarHeight, 0xFF202020);

        int borderColor = 0xFF606060;
        guiGraphics.fill(centerX - progressBarWidth / 2, mainProgressBarY, centerX + progressBarWidth / 2, mainProgressBarY + 1, borderColor);
        guiGraphics.fill(centerX - progressBarWidth / 2, mainProgressBarY + progressBarHeight - 1, centerX + progressBarWidth / 2, mainProgressBarY + progressBarHeight, borderColor);
        guiGraphics.fill(centerX - progressBarWidth / 2, mainProgressBarY, centerX - progressBarWidth / 2 + 1, mainProgressBarY + progressBarHeight, borderColor);
        guiGraphics.fill(centerX + progressBarWidth / 2 - 1, mainProgressBarY, centerX + progressBarWidth / 2, mainProgressBarY + progressBarHeight, borderColor);

        if (totalBytes > 0) {
            drawMainDownloadProgress(guiGraphics, centerX, mainProgressBarY, progressBarWidth, progressBarHeight);
        } else if (extractionTotal > 0) {
            drawExtractionProgress(guiGraphics, centerX, mainProgressBarY, progressBarWidth, progressBarHeight);
        }

        if (currentFileTotal > 0) {
            int fileProgressBarY = mainProgressBarY + 30;
            guiGraphics.fill(centerX - progressBarWidth / 2, fileProgressBarY, centerX + progressBarWidth / 2, fileProgressBarY + progressBarHeight, 0xFF202020);

            guiGraphics.fill(centerX - progressBarWidth / 2, fileProgressBarY, centerX + progressBarWidth / 2, fileProgressBarY + 1, borderColor);
            guiGraphics.fill(centerX - progressBarWidth / 2, fileProgressBarY + progressBarHeight - 1, centerX + progressBarWidth / 2, fileProgressBarY + progressBarHeight, borderColor);
            guiGraphics.fill(centerX - progressBarWidth / 2, fileProgressBarY, centerX - progressBarWidth / 2 + 1, fileProgressBarY + progressBarHeight, borderColor);
            guiGraphics.fill(centerX + progressBarWidth / 2 - 1, fileProgressBarY, centerX + progressBarWidth / 2, fileProgressBarY + progressBarHeight, borderColor);

            drawFileDownloadProgress(guiGraphics, centerX, fileProgressBarY, progressBarWidth, progressBarHeight);
        }

        int logY = centerY - contentHeight / 2 + 160 + (currentFileTotal > 0 ? 35 : 0);
        drawLogArea(guiGraphics, centerX, logY, contentWidth);

        if (hasError) {
            Component errorText = Component.literal(errorMessage).withStyle(ChatFormatting.RED);
            guiGraphics.drawCenteredString(this.font, errorText, centerX, logY + contentWidth / 2 + 30, 0xFF5555);
        }

        if (showExit) {
            exitButton.visible = true;
            Component exitHint = Component.translatable("tipua.gui.download.exit_hint");
            guiGraphics.drawCenteredString(this.font, exitHint, centerX, logY + contentWidth / 2 + 55, 0xAAAAAA);
        }

        if (showRestart) {
            restartButton.visible = true;
            Component completeText = Component.translatable("tipua.gui.download.complete").withStyle(ChatFormatting.GREEN);
            guiGraphics.drawCenteredString(this.font, completeText, centerX, logY + contentWidth / 2 + 30, 0x55FF55);

            Component restartHint = Component.translatable("tipua.gui.download.restart_hint");
            guiGraphics.drawCenteredString(this.font, restartHint, centerX, logY + contentWidth / 2 + 55, 0xAAAAAA);
        } else {
            restartButton.visible = false;
        }

        this.renderables.forEach(widget -> widget.render(guiGraphics, mouseX, mouseY, partialTick));
    }

    private void drawMainDownloadProgress(GuiGraphics guiGraphics, int centerX, int progressBarY,
            int progressBarWidth, int progressBarHeight) {
        double progress = (double) downloadedBytes / totalBytes;
        int progressWidth = (int) (progress * progressBarWidth);
        int progressColor = 0xFF55FF55;
        if (progress < 0.3) progressColor = 0xFFFF5555;
        else if (progress < 0.7) progressColor = 0xFFFFFF55;

        guiGraphics.fill(centerX - progressBarWidth / 2, progressBarY, centerX - progressBarWidth / 2 + progressWidth, progressBarY + progressBarHeight, progressColor);

        int percentage = (int) (progress * 100);
        Component percentageText = Component.literal(percentage + "%");
        guiGraphics.drawCenteredString(this.font, percentageText, centerX, progressBarY + 28, 0xFFFFFF);

        String downloadedStr = formatBytes(downloadedBytes);
        String totalStr = formatBytes(totalBytes);
        Component sizeText = Component.literal(downloadedStr + " / " + totalStr);
        guiGraphics.drawCenteredString(this.font, sizeText, centerX, progressBarY + 43, 0xAAAAAA);

        long avgSpeed = calculateAverageSpeed(System.currentTimeMillis());
        if (avgSpeed > 0) {
            Component speedText = Component.translatable("tipua.gui.download.speed", formatBytes(avgSpeed) + "/s");
            guiGraphics.drawCenteredString(this.font, speedText, centerX, progressBarY + 58, 0xAAAAAA);
        }
    }

    private void drawFileDownloadProgress(GuiGraphics guiGraphics, int centerX, int progressBarY,
            int progressBarWidth, int progressBarHeight) {
        double progress = (double) currentFileDownloaded / currentFileTotal;
        int progressWidth = (int) (progress * progressBarWidth);
        int progressColor = 0xFF55AAFF;

        guiGraphics.fill(centerX - progressBarWidth / 2, progressBarY, centerX - progressBarWidth / 2 + progressWidth, progressBarY + progressBarHeight, progressColor);

        int percentage = (int) (progress * 100);
        Component percentageText = Component.literal("单个文件: " + percentage + "%");
        guiGraphics.drawCenteredString(this.font, percentageText, centerX, progressBarY + 28, 0xFFFFFF);

        String downloadedStr = formatBytes(currentFileDownloaded);
        String totalStr = formatBytes(currentFileTotal);
        Component sizeText = Component.literal(downloadedStr + " / " + totalStr);
        guiGraphics.drawCenteredString(this.font, sizeText, centerX, progressBarY + 43, 0xAAAAAA);
    }

    private long calculateAverageSpeed(long now) {
        if (sampleCount == 0) {
            return 0;
        }
        int oldestIdx = -1;
        for (int i = 0; i < sampleCount; i++) {
            int idx = (sampleHead - sampleCount + i + MAX_SAMPLES) % MAX_SAMPLES;
            if (now - sampleTimes[idx] <= SPEED_SAMPLE_WINDOW_MS) {
                oldestIdx = idx;
                break;
            }
        }
        if (oldestIdx == -1) {
            oldestIdx = (sampleHead - sampleCount + MAX_SAMPLES) % MAX_SAMPLES;
        }
        long timeDelta = now - sampleTimes[oldestIdx];
        long bytesDelta = downloadedBytes - sampleBytes[oldestIdx];
        if (timeDelta <= 0 || bytesDelta <= 0) {
            return 0;
        }
        return bytesDelta * 1000L / timeDelta;
    }

    private void drawExtractionProgress(GuiGraphics guiGraphics, int centerX, int progressBarY,
            int progressBarWidth, int progressBarHeight) {
        double progress = (double) extractionCurrent / extractionTotal;
        int progressWidth = (int) (progress * progressBarWidth);
        int progressColor = 0xFF55AAFF;
        guiGraphics.fill(centerX - progressBarWidth / 2, progressBarY, centerX - progressBarWidth / 2 + progressWidth, progressBarY + progressBarHeight, progressColor);

        int percentage = (int) (progress * 100);
        Component percentageText = Component.literal(percentage + "%");
        guiGraphics.drawCenteredString(this.font, percentageText, centerX, progressBarY + 28, 0xFFFFFF);

        Component countText = Component.literal(extractionCurrent + " / " + extractionTotal + " 文件");
        guiGraphics.drawCenteredString(this.font, countText, centerX, progressBarY + 43, 0xAAAAAA);
    }

    private void drawLogArea(GuiGraphics guiGraphics, int centerX, int logY, int contentWidth) {
        int logWidth = contentWidth;
        int logHeight = Math.max(80, (this.height - logY - 80));

        guiGraphics.fill(centerX - logWidth / 2, logY, centerX + logWidth / 2, logY + logHeight, 0xFF1A1A1A);

        int borderColor = 0xFF404040;
        guiGraphics.fill(centerX - logWidth / 2, logY, centerX + logWidth / 2, logY + 1, borderColor);
        guiGraphics.fill(centerX - logWidth / 2, logY + logHeight - 1, centerX + logWidth / 2, logY + logHeight, borderColor);
        guiGraphics.fill(centerX - logWidth / 2, logY, centerX - logWidth / 2 + 1, logY + logHeight, borderColor);
        guiGraphics.fill(centerX + logWidth / 2 - 1, logY, centerX + logWidth / 2, logY + logHeight, borderColor);

        Component logTitle = Component.literal("[ 操作日志 ]").withStyle(ChatFormatting.GRAY);
        guiGraphics.drawString(this.font, logTitle, centerX - logWidth / 2 + 5, logY + 2, 0xAAAAAA);

        int lineHeight = 10;
        int visibleLines = (logHeight - 15) / lineHeight;
        int totalLines = logEntries.size();

        if (totalLines > visibleLines) {
            while (logScrollOffset > totalLines - visibleLines) {
                logScrollOffset = totalLines - visibleLines;
            }
            while (logScrollOffset < 0) {
                logScrollOffset = 0;
            }
        } else {
            logScrollOffset = 0;
        }

        int startLine = (int) logScrollOffset;
        for (int i = 0; i < visibleLines && startLine + i < totalLines; i++) {
            String entry = logEntries.get(startLine + i);
            int y = logY + 15 + i * lineHeight;
            int color = 0x888888;

            if (entry.startsWith("[INFO]")) color = 0x55FF55;
            else if (entry.startsWith("[WARN]")) color = 0xFFFF55;
            else if (entry.startsWith("[ERROR]")) color = 0xFF5555;
            else if (entry.startsWith("[解压]")) color = 0x55AAFF;

            guiGraphics.drawString(this.font, entry, centerX - logWidth / 2 + 5, y, color);
        }
    }

    public void addLogEntry(String type, String message) {
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String prefix = switch (type.toLowerCase()) {
            case "info" -> "[INFO]";
            case "warn" -> "[WARN]";
            case "error" -> "[ERROR]";
            case "extract" -> "[解压]";
            default -> "[INFO]";
        };

        logEntries.add(timestamp + " " + prefix + " " + message);

        if (logEntries.size() > MAX_LOG_LINES) {
            logEntries.remove(0);
        }

        logScrollOffset = logEntries.size();
    }

    public void updateDownloadProgress(long downloadedBytes, long totalBytes) {
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.extractionCurrent = 0;
        this.extractionTotal = 0;
        this.status = Component.translatable("tipua.gui.download.downloading").getString();
        long now = System.currentTimeMillis();
        sampleTimes[sampleHead] = now;
        sampleBytes[sampleHead] = downloadedBytes;
        sampleHead = (sampleHead + 1) % MAX_SAMPLES;
        if (sampleCount < MAX_SAMPLES) {
            sampleCount++;
        }
    }

    public void updateCurrentFileProgress(long downloaded, long total) {
        this.currentFileDownloaded = downloaded;
        this.currentFileTotal = total;
    }

    public void resetCurrentFileProgress() {
        this.currentFileDownloaded = 0;
        this.currentFileTotal = 0;
    }

    public void updateExtractionProgress(String relativePath, long current, long total) {
        this.extractionCurrent = current;
        this.extractionTotal = total;
        this.extractingFile = relativePath;
        this.downloadedBytes = 0;
        this.totalBytes = 0;
        this.currentFileDownloaded = 0;
        this.currentFileTotal = 0;
        this.status = Component.translatable("tipua.gui.download.extracting").getString();

        addLogEntry("extract", relativePath);
    }

    public void setExtractionComplete() {
        this.status = Component.translatable("tipua.gui.download.extract_complete").getString();
        addLogEntry("info", "解压完成");
    }

    public void showRestartButton() {
        this.showRestart = true;
        this.status = Component.translatable("tipua.gui.download.update_complete").getString();
        addLogEntry("info", "更新完成，请重启游戏");
    }

    public void setError(String errorMessage) {
        this.hasError = true;
        this.errorMessage = errorMessage;
        this.status = Component.translatable("tipua.gui.download.error").getString();
        addLogEntry("error", errorMessage);
    }

    public void showExitButton() {
        this.showExit = true;
    }

    public void showRollbackButton() {
        this.rollbackButton.visible = true;
        addLogEntry("info", "显示回滚按钮");
    }

    public void setFileProgress(int currentIndex, int totalFiles, String fileName) {
        this.currentFileIndex = currentIndex;
        this.totalFiles = totalFiles;
        this.currentFileName = fileName;
        this.currentFileDownloaded = 0;
        this.currentFileTotal = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int contentWidth = Math.min(this.width - 40, 600);
        int logY = centerY - Math.min(this.height - 80, 400) / 2 + 160 + (currentFileTotal > 0 ? 35 : 0);
        int logWidth = contentWidth;
        int logHeight = Math.max(80, (this.height - logY - 80));

        if (mouseX >= centerX - logWidth / 2 && mouseX <= centerX + logWidth / 2 && 
            mouseY >= logY && mouseY <= logY + logHeight) {
            logScrollOffset -= deltaY * 3;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return hasError;
    }
}