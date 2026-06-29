package miao.byusi.mc.neoforge.tipua.parser;

import java.io.File;
import java.util.List;

public interface ModpackParser {
    boolean supports(File file);
    List<ModInfo> parse(File file, String baseUrl);
    String getFormatName();
}