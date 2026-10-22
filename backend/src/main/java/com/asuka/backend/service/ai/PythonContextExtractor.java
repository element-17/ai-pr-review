package com.asuka.backend.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Python 轻量函数上下文提取。
 */
public class PythonContextExtractor implements LanguageContextExtractor {

    private static final Pattern DEF_START = Pattern.compile(
            "^\\s*(async\\s+)?def\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(");
    private static final Pattern IMPORT = Pattern.compile("^\\s*(import\\s+.+|from\\s+.+\\s+import\\s+.+)$");

    @Override
    public String language() {
        return "python";
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
            if (!DEF_START.matcher(lines[i]).find()) {
                continue;
            }
            int indent = indentOf(lines[i]);
            int end = i;
            for (int j = i + 1; j < lines.length; j++) {
                String line = lines[j];
                if (line.trim().isEmpty()) {
                    end = j;
                    continue;
                }
                if (indentOf(line) <= indent) {
                    break;
                }
                end = j;
            }
            if (!overlaps(addedLines, i + 1, end + 1)) {
                continue;
            }
            blocks.add(new ContextBlock(i + 1, end + 1, join(lines, i, end)));
        }
        return blocks;
    }

    private int indentOf(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) {
            count++;
        }
        return count;
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
