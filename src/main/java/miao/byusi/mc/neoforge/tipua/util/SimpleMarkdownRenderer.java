package miao.byusi.mc.neoforge.tipua.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化的Markdown渲染器
 * 支持基本的Markdown语法，用于更新日志的富文本显示
 */
public class SimpleMarkdownRenderer {
    
    /**
     * 渲染后的文本片段
     */
    public static class RenderedFragment {
        private final String text;
        private final TextFormat format;
        
        public RenderedFragment(String text, TextFormat format) {
            this.text = text;
            this.format = format;
        }
        
        public String getText() {
            return text;
        }
        
        public TextFormat getFormat() {
            return format;
        }
    }
    
    /**
     * 文本格式枚举
     */
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
        
        public String getName() {
            return name;
        }
        
        public int getColor() {
            return color;
        }
    }
    
    /**
     * 将Markdown文本渲染为文本片段列表
     */
    public static List<RenderedFragment> render(String markdown) {
        List<RenderedFragment> fragments = new ArrayList<>();
        
        if (markdown == null || markdown.isEmpty()) {
            return fragments;
        }
        
        // 移除Windows换行符
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        
        // 按行处理
        String[] lines = normalized.split("\n");
        
        for (String line : lines) {
            processLine(line.trim(), fragments);
        }
        
        return fragments;
    }
    
    /**
     * 处理单行文本
     */
    private static void processLine(String line, List<RenderedFragment> fragments) {
        if (line.isEmpty()) {
            fragments.add(new RenderedFragment("\n", TextFormat.NORMAL));
            return;
        }
        
        // 检查标题
        if (line.startsWith("#")) {
            processHeader(line, fragments);
            return;
        }
        
        // 检查列表
        if (line.startsWith("- ") || line.startsWith("* ") || line.matches("^\\d+\\.\\s.*")) {
            processList(line, fragments);
            return;
        }
        
        // 检查引用
        if (line.startsWith(">")) {
            processQuote(line, fragments);
            return;
        }
        
        // 检查代码块
        if (line.startsWith("```")) {
            processCodeBlock(line, fragments);
            return;
        }
        
        // 处理普通行，包括内联格式
        processInlineFormatting(line + "\n", fragments);
    }
    
    /**
     * 处理标题
     */
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
    
    /**
     * 处理列表
     */
    private static void processList(String line, List<RenderedFragment> fragments) {
        String text;
        if (line.startsWith("- ") || line.startsWith("* ")) {
            text = line.substring(2);
        } else {
            // 数字列表
            Pattern pattern = Pattern.compile("^\\d+\\.\\s(.*)");
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                text = matcher.group(1);
            } else {
                text = line;
            }
        }
        
        // 处理列表项内的内联格式
        List<RenderedFragment> inlineFragments = new ArrayList<>();
        processInlineFormatting(text, inlineFragments);
        
        // 添加列表项符号并合并格式
        for (RenderedFragment fragment : inlineFragments) {
            if (fragment == inlineFragments.get(0)) {
                fragments.add(new RenderedFragment("• " + fragment.getText(), TextFormat.LIST));
            } else {
                fragments.add(new RenderedFragment(fragment.getText(), TextFormat.LIST));
            }
        }
        
        // 添加换行
        if (!fragments.isEmpty()) {
            RenderedFragment lastFragment = fragments.get(fragments.size() - 1);
            fragments.set(fragments.size() - 1, 
                new RenderedFragment(lastFragment.getText() + "\n", lastFragment.getFormat()));
        }
    }
    
    /**
     * 处理引用
     */
    private static void processQuote(String line, List<RenderedFragment> fragments) {
        String text = line.substring(1).trim();
        
        // 处理引用内的内联格式
        List<RenderedFragment> inlineFragments = new ArrayList<>();
        processInlineFormatting(text, inlineFragments);
        
        // 添加引用符号并合并格式
        for (RenderedFragment fragment : inlineFragments) {
            if (fragment == inlineFragments.get(0)) {
                fragments.add(new RenderedFragment("│ " + fragment.getText(), TextFormat.QUOTE));
            } else {
                fragments.add(new RenderedFragment(fragment.getText(), TextFormat.QUOTE));
            }
        }
        
        // 添加换行
        if (!fragments.isEmpty()) {
            RenderedFragment lastFragment = fragments.get(fragments.size() - 1);
            fragments.set(fragments.size() - 1, 
                new RenderedFragment(lastFragment.getText() + "\n", lastFragment.getFormat()));
        }
    }
    
    /**
     * 处理代码块
     */
    private static void processCodeBlock(String line, List<RenderedFragment> fragments) {
        // 简化处理：直接显示代码块内容
        String text = line.replace("```", "").trim();
        if (!text.isEmpty()) {
            fragments.add(new RenderedFragment(text + "\n", TextFormat.CODE));
        }
    }
    
    /**
     * 处理内联格式
     */
    private static void processInlineFormatting(String text, List<RenderedFragment> fragments) {
        if (text.isEmpty()) {
            return;
        }
        
        // 按优先级处理各种格式标记
        text = processBoldItalic(text, fragments);
        text = processCode(text, fragments);
        text = processLinks(text, fragments);
        text = processHighlights(text, fragments);
        
        // 处理剩余的普通文本
        if (!text.isEmpty()) {
            fragments.add(new RenderedFragment(text, TextFormat.NORMAL));
        }
    }
    
    /**
     * 处理粗体和斜体
     */
    private static String processBoldItalic(String text, List<RenderedFragment> fragments) {
        // 处理粗体 **text**
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
        
        // 处理斜体 *text*
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
    
    /**
     * 处理内联代码
     */
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
    
    /**
     * 处理链接
     */
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
    
    /**
     * 处理高亮 ==text==
     */
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
    
    /**
     * 将渲染片段转换为纯文本（用于调试）
     */
    public static String toPlainText(List<RenderedFragment> fragments) {
        StringBuilder sb = new StringBuilder();
        for (RenderedFragment fragment : fragments) {
            sb.append(fragment.getText());
        }
        return sb.toString();
    }
    
    /**
     * 从Markdown中提取纯文本标题
     */
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
                if (level <= 3) { // 只提取前三级标题
                    return line.substring(level).trim();
                }
            }
        }
        
        return "";
    }
    
    /**
     * 验证Markdown文本是否有效
     */
    public static boolean isValidMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return false;
        }
        
        // 基本的语法检查
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
                return false; // 括号不匹配
            }
        }
        
        return openBrackets == 0 && openParens == 0 && openBackticks == 0;
    }
}