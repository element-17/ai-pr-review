package com.asuka.backend.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TypeScript/JavaScript 轻量函数上下文提取。
 *
 * <p>不引入 tree-sitter；用声明正则和大括号配对截取函数体，
 * 失败时由 ContextBuilder 回退到 hunk 附近窗口。
 */
public class TypeScriptContextExtractor implements LanguageContextExtractor {

    private static final Pattern FUNCTION_START = Pattern.compile(
            "^\\s*(export\\s+)?(default\\s+)?(async\\s+)?function\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");
    private static final Pattern ARROW_OR_CONST = Pattern.compile(
            "^\\s*(export\\s+)?(const|let|var)\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=\\s*(async\\s*)?\\(");
    private static final Pattern METHOD_START = Pattern.compile(
            "^\\s*(public\\s+|private\\s+|protected\\s+|static\\s+|async\\s+)*[A-Za-z_$][A-Za-z0-9_$]*\\s*\\([^)]*\\)\\s*\\{");
    private static final Pattern IMPORT = Pattern.compile(
            "^\\s*(import\\s+.*from\\s+['\"].*['\"]|.*require\\(\\s*['\"].*['\"]\\s*\\)).*$");

    @Override
    public String language() {
        return "typescript";
    }

    @Override
    public List<String> imports(String content) {
        List<String> result = new ArrayList<>();
        for (String line : content.split("\n", -1)) {
            if (IMPORT.matcher(line).matches()) {
                result.add(line.trim());
            }
        }
        return result;
    }

    @Override
    public List<ContextBlock> extract(String content, Set<Integer> addedLines) {
        String[] lines = content.split("\n", -1);
        List<ContextBlock> blocks = new ArrayList<>();
        for (int i = 0; i < lines.length && blocks.size() < 6; i++) {
            if (!isFunctionStart(lines[i])) {
                continue;
            }
            int end = findBlockEnd(lines, i);
            if (end < i) {
                continue;
            }
            if (!overlaps(addedLines, i + 1, end + 1)) {
                continue;
            }
            blocks.add(new ContextBlock(i + 1, end + 1, join(lines, i, end)));
        }
        return blocks;
    }

    private boolean isFunctionStart(String line) {
        return FUNCTION_START.matcher(line).find()
                || ARROW_OR_CONST.matcher(line).find()
                || METHOD_START.matcher(line).find();
    }

    private int findBlockEnd(String[] lines, int start) {
        int depth = 0;
        boolean started = false;
        for (int i = start; i < lines.length; i++) {
            for (char c : lines[i].toCharArray()) {
                if (c == '{') {
                    depth++;
                    started = true;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (started && depth <= 0) {
                return i;
            }
            // 箭头函数表达式体没有大括号：遇到分号结束
            if (!started && lines[i].contains(";")) {
                return i;
            }
            if (started) {
                continue;
            }
            if (i - start > 3) {
                return i;
            }
        }
        return Math.min(lines.length - 1, start + 80);
    }

    private boolean overlaps(Set<Integer> addedLines, int start, int end) {
        for (int line : addedLines) {
            if (line >= start && line <= end) {
                return true;
            }
        }
        return false;
    }

    private String join(String[] lines, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= end; i++) {
            builder.append(lines[i]).append('\n');
        }
        return builder.toString();
    }
}
