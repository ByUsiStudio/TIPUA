package miao.byusi.mc.fabric.tipua.client;

import miao.byusi.mc.fabric.tipua.TIPUAMod;
import miao.byusi.mc.fabric.tipua.client.gui.ModpackDownloadScreen;
import miao.byusi.mc.fabric.tipua.config.ClientConfig;
import miao.byusi.mc.fabric.tipua.util.HashUtil;
import miao.byusi.mc.fabric.tipua.util.ModrinthIndex;
import miao.byusi.mc.fabric.tipua.util.RollbackManager;
import miao.byusi.mc.fabric.tipua.util.VersionManager;
import miao.byusi.mc.fabric.tipua.client.ClientNetworkManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClientUpdateManager {
    private static ExecutorService updateExecutor;
    private static boolean hasCheckedUpdate = false;
    private static volatile ModpackDownloadScreen downloadScreen;
    private static volatile boolean isDownloading = false;
    private static String pendingServerVersion;
    private static boolean updateInProgress = false;
    private static String previousVersion = "0.0.0";

    public static void checkUpdateOnMainMenu() {
        if (hasCheckedUpdate) {
            return;
        }
        hasCheckedUpdate = true;

        if (VersionManager.isFirstRun()) {
            TIPUAMod.LOGGER.info("First run, skipping update check");
            VersionManager.markAsRun();
            return;
        }

        if (!ClientConfig.isAutoUpdate()) {
            TIPUAMod.LOGGER.info("Auto update disabled");
            return;
        }

        startUpdateCheck();
    }

    private static boolean isConnectedToServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null;
    }

    private static void startUpdateCheck() {
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        updateExecutor = Executors.newSingleThreadExecutor();
        updateExecutor.submit(() -> {
            try {
                doUpdateCheck();
            } catch (Exception e) {
                TIPUAMod.LOGGER.error("Update check failed", e);
                showToast("Update failed", "Error checking for updates");
                executeOnMainThread(() -> {
                    if (downloadScreen != null) {
                        downloadScreen.setError(e.getMessage());
                    }
                });
            }
        });
    }

    // rest of class omitted for brevity - file copied from main implementation
}
