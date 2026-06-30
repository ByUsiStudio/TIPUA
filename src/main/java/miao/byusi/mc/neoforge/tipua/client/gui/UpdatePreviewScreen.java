package miao.byusi.mc.neoforge.tipua.client.gui;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;
import miao.byusi.mc.neoforge.tipua.util.ModpackManifest;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新预览界面
 * 在下载前展示将要更新的模组和文件列表
 */
public class UpdatePreviewScreen extends Screen {
    private static final Component TITLE = Component.translatable("tipua.gui.preview.title").withStyle(ChatFormatting.BOLD);
    
    private final Screen parent;
    private final String serverVersion;
    private final String localVersion;
    private final ModpackManifest manifest;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    
    private List<ModpackManifest.FileEntry> visibleFiles;
    private String currentTab = "all";
    
    private Button confirmButton;
    private Button cancelButton;
    private Button allButton;
    private Button modsButton;
    private Button configButton;
    
    // 滚动相关
    private int scrollOffset = 0;
    private boolean isScrolling = false;
    private double lastMouseY = 0;
    private int panelHeight;
    private int panelY;
    private int panelX;
    private int panelWidth;
    
    private static final int ITEM_HEIGHT = 18;
    
    public UpdatePreviewScreen(Screen parent, String serverVersion, String localVersion, 
                               ModpackManifest manifest, Runnable onConfirm, Runnable onCancel) {
        super(TITLE);
        this.parent = parent;
        this.serverVersion = serverVersion;
        this.localVersion = localVersion;
        this.manifest = manifest;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.visibleFiles = new ArrayList<>(manifest.getFiles());
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        panelWidth = Math.min(this.width - 40, 600);
        panelHeight = this.height - 180;
        panelX = centerX - panelWidth / 2;
        panelY = 80;
        
        allButton = Button.builder(
                Component.literal("全部").withStyle(currentTab.equals("all") ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                button -> switchTab("all")
        ).bounds(panelX, panelY - 25, 60, 20).build();
        
        modsButton = Button.builder(
                Component.literal("模组").withStyle(currentTab.equals("mods") ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                button -> switchTab("mods")
        ).bounds(panelX + 65, panelY - 25, 60, 20).build();
        
        configButton = Button.builder(
                Component.literal("配置").withStyle(currentTab.equals("config") ? ChatFormatting.GREEN : ChatFormatting.WHITE),
                button -> switchTab("config")
        ).bounds(panelX + 130, panelY - 25, 60, 20).build();
        
        this.addRenderableWidget(allButton);
        this.addRenderableWidget(modsButton);
        this.addRenderableWidget(configButton);
        
        confirmButton = Button.builder(
                Component.translatable("tipua.gui.preview.confirm"),
                button -> onConfirm.run()
        ).bounds(centerX - 110, this.height - 35, 100, 25).build();
        
        cancelButton = Button.builder(
                Component.translatable("tipua.gui.preview.cancel"),
                button -> onCancel.run()
        ).bounds(centerX + 10, this.height - 35, 100, 25).build();
        
        this.addRenderableWidget(confirmButton);
        this.addRenderableWidget(cancelButton);
        
        updateFileList();
    }
    
    private void switchTab(String tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        updateFileList();
        
        allButton.setMessage(Component.literal("全部").withStyle(tab.equals("all") ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        modsButton.setMessage(Component.literal("模组").withStyle(tab.equals("mods") ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        configButton.setMessage(Component.literal("配置").withStyle(tab.equals("config") ? ChatFormatting.GREEN : ChatFormatting.WHITE));
    }
    
    private void updateFileList() {
        switch (currentTab) {
            case "mods":
                visibleFiles = manifest.getModList();
                break;
            case "config":
                visibleFiles = manifest.getConfigList();
                break;
            default:
                visibleFiles = new ArrayList<>(manifest.getFiles());
                break;
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查滚动条点击
        if (mouseX >= panelX + panelWidth - 8 && mouseX <= panelX + panelWidth + 2 &&
            mouseY >= panelY && mouseY <= panelY + panelHeight) {
            isScrolling = true;
            lastMouseY = mouseY;
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isScrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth &&
            mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int maxScroll = Math.max(0, visibleFiles.size() * ITEM_HEIGHT - panelHeight);
            scrollOffset -= (int) verticalAmount * 3;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isScrolling) {
            int maxScroll = Math.max(0, visibleFiles.size() * ITEM_HEIGHT - panelHeight);
            scrollOffset += (lastMouseY - mouseY);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        
        int centerX = this.width / 2;
        
        guiGraphics.drawCenteredString(this.font, TITLE, centerX, 20, 0xFFFFFF);
        
        Component versionInfo = Component.translatable("tipua.gui.preview.version_info", localVersion, serverVersion);
        guiGraphics.drawCenteredString(this.font, versionInfo, centerX, 45, 0xAAAAAA);
        
        Component fileCount = Component.translatable("tipua.gui.preview.file_count", 
                visibleFiles.size(), manifest.getFiles().size());
        guiGraphics.drawCenteredString(this.font, fileCount, centerX, 60, 0xAAAAAA);
        
        String totalSize = ModpackManifest.formatSize(manifest.getTotalSize());
        Component sizeInfo = Component.translatable("tipua.gui.preview.size", totalSize);
        guiGraphics.drawCenteredString(this.font, sizeInfo, centerX, 72, 0xAAAAAA);
        
        // 绘制面板背景
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF404040);
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF202020);
        
        // 绘制文件列表
        int listX = panelX + 5;
        int listY = panelY - scrollOffset;
        int maxItems = panelHeight / ITEM_HEIGHT + 1;
        
        for (int i = 0; i < Math.min(visibleFiles.size(), maxItems + scrollOffset / ITEM_HEIGHT + 1); i++) {
            if (i < scrollOffset / ITEM_HEIGHT) continue;
            
            ModpackManifest.FileEntry file = visibleFiles.get(i);
            int itemY = listY + (i - scrollOffset / ITEM_HEIGHT) * ITEM_HEIGHT;
            
            if (itemY + ITEM_HEIGHT < panelY || itemY > panelY + panelHeight) {
                continue;
            }
            
            int typeColor = 0xAAAAAA;
            String typeIcon = "[R]";
            if ("mod".equals(file.type)) {
                typeColor = 0xFF55FF55;
                typeIcon = "[M]";
            } else if ("config".equals(file.type)) {
                typeColor = 0xFF55AAFF;
                typeIcon = "[C]";
            }
            
            guiGraphics.drawString(font, typeIcon, listX, itemY + 2, typeColor);
            
            String fileName = file.path;
            if (fileName.length() > 55) {
                fileName = "..." + fileName.substring(fileName.length() - 52);
            }
            guiGraphics.drawString(font, fileName, listX + 30, itemY + 2, 0xFFFFFF);
            
            String sizeStr = ModpackManifest.formatSize(file.size);
            int sizeWidth = font.width(sizeStr);
            guiGraphics.drawString(font, sizeStr, panelX + panelWidth - 10 - sizeWidth, itemY + 2, 0x888888);
        }
        
        // 绘制滚动条
        int maxScroll = Math.max(1, visibleFiles.size() * ITEM_HEIGHT - panelHeight);
        int scrollBarHeight = Math.max(20, panelHeight * panelHeight / (visibleFiles.size() * ITEM_HEIGHT));
        int scrollBarY = panelY + (scrollOffset * (panelHeight - scrollBarHeight) / maxScroll);
        
        guiGraphics.fill(panelX + panelWidth - 6, panelY, panelX + panelWidth - 2, panelY + panelHeight, 0xFF303030);
        guiGraphics.fill(panelX + panelWidth - 5, scrollBarY, panelX + panelWidth - 3, scrollBarY + scrollBarHeight, 0xFF606060);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public void onClose() {
        if (onCancel != null) {
            onCancel.run();
        }
    }
}