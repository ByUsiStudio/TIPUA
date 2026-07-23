package miao.byusi.mc.fabric.tipua.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleMarkdownRenderer {
    
    public static class RenderedFragment {
        private final String text;
        private final TextFormat format;
        
        public RenderedFragment(String text, TextFormat format) {
            this.text = text;
            this.format = format;
        }
        
        public String getText() { return text; }
        public TextFormat getFormat() { return format; }
    }
    
    public enum TextFormat {
        NORMAL("normal", 0xFFFFFF),
        BOLD("bold", 0xFFFFFF),
        ITALIC("italic", 0xFFFFFF),
        CODE("code", 0xAAFFAA),
        HEADER("header", 0x55AAFF),
        LINK("link", 0x5555FF),
        LIST("list", 0xAAAAAA),
        QUOTE("quote", 0x888888),
        HIGHLIGHT("highlight", 0xFFFF55);
        
        private final String name;
        private final int color;
        
        TextFormat(String name, int color) {
            this.name = name;
            this.color = color;
        }
        
        public String getName() { return name; }
        public int getColor() { return color; }
    }
    
    public static List<RenderedFragment> render(String markdown) {
        List<RenderedFragment> fragments = new ArrayList<>();
        
        if (markdown == null || markdown.isEmpty()) {
            return fragments;
        }
        
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");
        
        for (String line : lines) {
            processLine(line.trim(), fragments);
        }
        
        return fragments;
    }
    
    private static void processLine(String line, List<RenderedFragment> fragments) {
        if (line.isEmpty()) {
            fragments.add(new RenderedFragment("\n", TextFormat.NORMAL));
            return;
        }
        
        if (line.startsWith("#")) {
            processHeader(line, fragments);
            return;
        }
        
        if (line.startsWith("- ") || line.startsWith("* ") || line.matches("^\\d+\\.\\s.*")) {
            processList(line, fragments);
            return;
        }
        
        if (line.startsWith(">")) {
            processQuote(line, fragments);
            return;
        }
        
        if (line.startsWith("```")) {
            processCodeBlock(line, fragments);
            return;
        }
        
        processInlineFormatting(line + "\n", fragments);
    }
    
    private static void processHeader(String line, List<RenderedFragment> fragments) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        
        String text = line.substring(level).trim();
        if (!text.isEmpty()) {
            fragments.add(new RenderedFragment(text + "\n", TextFormat.HEADER));
        }
    }
    
    private static void processList(String line, List<RenderedFragment> fragments) {
        String text;
        if (line.startsWith("- ") || line.startsWith("* ")) {
            text = line.substring(2);
        } else {
            Pattern pattern = Pattern.compile("^\\d+\\.\\s(.*)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                text = matcher.group(1);
            } else {
                text = line;
            }
        }
        
        List<RenderedFragment> inlineFragments = new ArrayList<>();
        processInlineFormatting(text, inlineFragments);
        
        for (RenderedFragment fragment : inlineFragments) {
            if (fragment == inlineFragments.get(0)) {
                fragments.add(new RenderedFragment("• " + fragment.getText(), TextFormat.LIST));
            } else {
                fragments.add(new RenderedFragment(fragment.getText(), TextFormat.LIST));
            }
        }
        
        if (!fragments.isEmpty()) {
            RenderedFragment lastFragment = fragments.get(fragments.size() - 1);
            fragments.set(fragments.size() - 1, 
                new RenderedFragment(lastFragment.getText() + "\n", lastFragment.getFormat()));
        }
    }
    
    private static void processQuote(String line, List<RenderedFragment> fragments) {
        String text = line.substring(1).trim();
        
        List<RenderedFragment> inlineFragments = new ArrayList<>();
        processInlineFormatting(text, inlineFragments);
        
        for (RenderedFragment fragment : inlineFragments) {
            if (fragment == inlineFragments.get(0)) {
                fragments.add(new RenderedFragment("│ " + fragment.getText(), TextFormat.QUOTE));
            } else {
                fragments.add(new RenderedFragment(fragment.getText(), TextFormat.QUOTE));
            }
        }
        
        if (!fragments.isEmpty()) {
            RenderedFragment lastFragment = fragments.get(fragments.size() - 1);
            fragments.set(fragments.size() - 1, 
                new RenderedFragment(lastFragment.getText() + "\n", lastFragment.getFormat()));
        }
    }
    
    private static void processCodeBlock(String line, List<RenderedFragment> fragments) {
        String text = line.replace("```", "").trim();
        if (!text.isEmpty()) {
            fragments.add(new RenderedFragment(text + "\n", TextFormat.CODE));
        }
    }
    
    private static void processInlineFormatting(String text, List<RenderedFragment> fragments) {
        if (text.isEmpty()) {
            return;
        }
        
        text = processBoldItalic(text, fragments);
        text = processCode(text, fragments);
        text = processLinks(text, fragments);
        text = processHighlights(text, fragments);
        
        if (!text.isEmpty()) {
            fragments.add(new RenderedFragment(text, TextFormat.NORMAL));
        }
    }
    
    private static String processBoldItalic(String text, List<RenderedFragment> fragments) {
        Pattern boldPattern = Pattern.compile("\\*\\*([^*]+)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(text);
        
        while (boldMatcher.find()) {
            String beforeText = text.substring(0, boldMatcher.start());
            if (!beforeText.isEmpty()) {
                fragments.add(new RenderedFragment(beforeText, TextFormat.NORMAL));
            }
            
            String boldText = boldMatcher.group(1);
            fragments.add(new RenderedFragment(boldText, TextFormat.BOLD));
            
            text = text.substring(boldMatcher.end());
            boldMatcher = boldPattern.matcher(text);
        }
        
        Pattern italicPattern = Pattern.compile("\\*([^*]+)\\*");
        Matcher italicMatcher = italicPattern.matcher(text);
        
        while (italicMatcher.find()) {
            String beforeText = text.substring(0, italicMatcher.start());
            if (!beforeText.isEmpty()) {
                fragments.add(new RenderedFragment(beforeText, TextFormat.NORMAL));
            }
            
            String italicText = italicMatcher.group(1);
            fragments.add(new RenderedFragment(italicText, TextFormat.ITALIC));
            
            text = text.substring(italicMatcher.end());
            italicMatcher = italicPattern.matcher(text);
        }
        
        return text;
    }
    
    private static String processCode(String text, List<RenderedFragment> fragments) {
        Pattern codePattern = Pattern.compile("`([^`]+)`");
        Matcher codeMatcher = codePattern.matcher(text);
        
        while (codeMatcher.find()) {
            String beforeText = text.substring(0, codeMatcher.start());
            if (!beforeText.isEmpty()) {
                fragments.add(new RenderedFragment(beforeText, TextFormat.NORMAL));
            }
            
            String codeText = codeMatcher.group(1);
            fragments.add(new RenderedFragment(codeText, TextFormat.CODE));
            
            text = text.substring(codeMatcher.end());
            codeMatcher = codePattern.matcher(text);
        }
        
        return text;
    }
    
    private static String processLinks(String text, List<RenderedFragment> fragments) {
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher linkMatcher = linkPattern.matcher(text);
        
        while (linkMatcher.find()) {
            String beforeText = text.substring(0, linkMatcher.start());
            if (!beforeText.isEmpty()) {
                fragments.add(new RenderedFragment(beforeText, TextFormat.NORMAL));
            }
            
            String linkText = linkMatcher.group(1);
            fragments.add(new RenderedFragment(linkText, TextFormat.LINK));
            
            text = text.substring(linkMatcher.end());
            linkMatcher = linkPattern.matcher(text);
        }
        
        return text;
    }
    
    private static String processHighlights(String text, List<RenderedFragment> fragments) {
        Pattern highlightPattern = Pattern.compile("==([^=]+)==");
        Matcher highlightMatcher = highlightPattern.matcher(text);
        
        while (highlightMatcher.find()) {
            String beforeText = text.substring(0, highlightMatcher.start());
            if (!beforeText.isEmpty()) {
                fragments.add(new RenderedFragment(beforeText, TextFormat.NORMAL));
            }
            
            String highlightText = highlightMatcher.group(1);
            fragments.add(new RenderedFragment(highlightText, TextFormat.HIGHLIGHT));
            
            text = text.substring(highlightMatcher.end());
            highlightMatcher = highlightPattern.matcher(text);
        }
        
        return text;
    }
    
    public static String toPlainText(List<RenderedFragment> fragments) {
        StringBuilder sb = new StringBuilder();
        for (RenderedFragment fragment : fragments) {
            sb.append(fragment.getText());
        }
        return sb.toString();
    }
    
    public static String extractTitle(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                if (level <= 3) {
                    return line.substring(level).trim();
                }
            }
        }
        
        return "";
    }
    
    public static boolean isValidMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return false;
        }
        
        int openBrackets = 0;
        int openParens = 0;
        int openBackticks = 0;
        
        for (char c : markdown.toCharArray()) {
            if (c == '[') openBrackets++;
            else if (c == ']') openBrackets--;
            else if (c == '(') openParens++;
            else if (c == ')') openParens--;
            else if (c == '`') openBackticks = (openBackticks + 1) % 2;
            
            if (openBrackets < 0 || openParens < 0) {
                return false;
            }
        }
        
        return openBrackets == 0 && openParens == 0 && openBackticks == 0;
    }
}