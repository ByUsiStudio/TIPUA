package miao.byusi.mc.neoforge.tipua.parser;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipModpackParser implements ModpackParser {
    @Override
    public boolean supports(File file) {
        return file.isFile() && file.getName().toLowerCase().endsWith(".zip");
    }

    @Override
    public List<ModInfo> parse(File file, String baseUrl) {
        List<ModInfo> mods = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (name.toLowerCase().endsWith(".jar") && (name.contains("mods/") || name.startsWith("mods"))) {
                    String fileName = new File(name).getName();
                    String modId = extractModId(fileName);
                    String hash = calculateHash(zis);
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
                    TIPUAMod.LOGGER.debug("Found mod in zip: {}", fileName);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            TIPUAMod.LOGGER.error("Failed to parse zip modpack", e);
        }

        return mods;
    }

    @Override
    public String getFormatName() {
        return "zip";
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

    private String calculateHash(InputStream is) {
        try {
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