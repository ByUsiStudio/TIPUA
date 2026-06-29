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

public class ModrinthParser implements ModpackParser {
    private static final Gson GSON = new Gson();

    @Override
    public boolean supports(File file) {
        if (!file.isFile() || !file.getName().toLowerCase().endsWith(".zip")) {
            return false;
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equalsIgnoreCase("modrinth.index.json")) {
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
                if (entry.getName().equalsIgnoreCase("modrinth.index.json")) {
                    JsonObject index = GSON.fromJson(new InputStreamReader(zis, StandardCharsets.UTF_8), JsonObject.class);
                    
                    if (index.has("files")) {
                        JsonArray files = index.getAsJsonArray("files");
                        for (JsonElement element : files) {
                            JsonObject fileObj = element.getAsJsonObject();
                            
                            String fileName = fileObj.get("path").getAsString();
                            if (!fileName.toLowerCase().endsWith(".jar")) {
                                zis.closeEntry();
                                continue;
                            }
                            
                            fileName = new File(fileName).getName();
                            String modId = fileObj.has("project_id") ? fileObj.get("project_id").getAsString() : "unknown";
                            String hash = "";
                            if (fileObj.has("hashes")) {
                                JsonObject hashes = fileObj.getAsJsonObject("hashes");
                                if (hashes.has("sha256")) {
                                    hash = hashes.get("sha256").getAsString();
                                }
                            }
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
                            TIPUAMod.LOGGER.debug("Found Modrinth mod: {}", fileName);
                        }
                    }
                    break;
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            TIPUAMod.LOGGER.error("Failed to parse Modrinth modpack", e);
        }

        return mods;
    }

    @Override
    public String getFormatName() {
        return "modrinth";
    }
}