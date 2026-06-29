package miao.byusi.mc.neoforge.tipua.parser;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class FolderModpackParser implements ModpackParser {
    @Override
    public boolean supports(File file) {
        return file.isDirectory();
    }

    @Override
    public List<ModInfo> parse(File file, String baseUrl) {
        List<ModInfo> mods = new ArrayList<>();

        File modsDir = new File(file, "mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            modsDir = file;
        }

        File[] jarFiles = modsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null) {
            return mods;
        }

        for (File jarFile : jarFiles) {
            String fileName = jarFile.getName();
            String modId = extractModId(fileName);
            String hash = calculateHash(jarFile);
            String downloadUrl = baseUrl + "/mod/" + fileName;

            ModInfo mod = new ModInfo(
                    modId,
                    fileName.replace(".jar", ""),
                    "unknown",
                    fileName,
                    hash,
                    downloadUrl,
                    true
            );
            mods.add(mod);
            TIPUAMod.LOGGER.debug("Found mod in folder: {}", fileName);
        }

        return mods;
    }

    @Override
    public String getFormatName() {
        return "folder";
    }

    private String extractModId(String fileName) {
        String name = fileName.replace(".jar", "");
        int underscoreIdx = name.lastIndexOf('_');
        int hyphenIdx = name.lastIndexOf('-');

        if (underscoreIdx > 0) {
            return name.substring(0, underscoreIdx);
        } else if (hyphenIdx > 0) {
            return name.substring(0, hyphenIdx);
        }
        return name;
    }

    private String calculateHash(File file) {
        try (InputStream is = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            TIPUAMod.LOGGER.error("Failed to calculate hash", e);
            return "";
        }
    }
}