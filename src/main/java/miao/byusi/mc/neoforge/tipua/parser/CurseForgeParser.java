package miao.byusi.mc.neoforge.tipua.parser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CurseForgeParser implements ModpackParser {
    private static final Gson GSON = new Gson();

    @Override
    public boolean supports(File file) {
        if (!file.isFile() || !file.getName().toLowerCase().endsWith(".zip")) {
            return false;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase("manifest.json")) {
                    return true;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    @Override
    public List<ModInfo> parse(File file, String baseUrl) {
        List<ModInfo> mods = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase("manifest.json")) {
                    JsonObject manifest = GSON.fromJson(new InputStreamReader(zis, StandardCharsets.UTF_8), JsonObject.class);
                    
                    if (manifest.has("files")) {
                        JsonArray files = manifest.getAsJsonArray("files");
                        for (JsonElement element : files) {
                            JsonObject fileObj = element.getAsJsonObject();
                            
                            String fileName = fileObj.get("projectID").getAsString() + "_" + 
                                             fileObj.get("fileID").getAsString() + ".jar";
                            String modId = fileObj.get("projectID").getAsString();
                            String hash = "";
                            String downloadUrl = baseUrl + "/mod/" + fileName;

                            ModInfo mod = new ModInfo(
                                    modId,
                                    "CurseForge Mod " + modId,
                                    "unknown",
                                    fileName,
                                    hash,
                                    downloadUrl,
                                    true
                            );
                            mods.add(mod);
                            TIPUAMod.LOGGER.debug("Found CurseForge mod: {}", fileName);
                        }
                    }
                    break;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("Failed to parse CurseForge modpack", e);
        }

        return mods;
    }

    @Override
    public String getFormatName() {
        return "curseforge";
    }
}