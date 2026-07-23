package miao.byusi.mc.fabric.tipua.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModpackDownloadScreen extends Screen {
    public ModpackDownloadScreen(Screen parent, String version, Runnable onCancel, Runnable onRollback) {
        super(Text.of("Modpack Download"));
    }

    public void addLogEntry(String level, String message) {}
    public void setFileProgress(int current, int total, String name) {}
    public void resetCurrentFileProgress() {}
    public void updateCurrentFileProgress(long current, long total) {}
    public void setError(String message) {}
    public void showRollbackButton() {}
    public void showExitButton() {}
    public void showRestartButton() {}
}
