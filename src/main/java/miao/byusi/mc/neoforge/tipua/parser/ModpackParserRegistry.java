package miao.byusi.mc.neoforge.tipua.parser;

import miao.byusi.mc.neoforge.tipua.TIPUAMod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModpackParserRegistry {
    private static final List<ModpackParser> parsers = new ArrayList<>();

    static {
        registerParser(new ZipModpackParser());
        registerParser(new FolderModpackParser());
        registerParser(new CurseForgeParser());
        registerParser(new ModrinthParser());
    }

    public static void registerParser(ModpackParser parser) {
        parsers.add(parser);
        TIPUAMod.LOGGER.info("Registered modpack parser: {}", parser.getFormatName());
    }

    public static ModpackParser findParser(File file) {
        for (ModpackParser parser : parsers) {
            if (parser.supports(file)) {
                return parser;
            }
        }
        return null;
    }

    public static ModpackParser findParserByFormat(String format) {
        for (ModpackParser parser : parsers) {
            if (parser.getFormatName().equalsIgnoreCase(format)) {
                return parser;
            }
        }
        return null;
    }

    public static List<ModpackParser> getAllParsers() {
        return new ArrayList<>(parsers);
    }
}