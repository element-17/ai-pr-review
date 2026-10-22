package com.asuka.backend.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified diff 结构化解析。
 *
 * <p>只解析 diff 文本，不访问数据库；行号是 unified diff 中旧/新文件侧的行号。
 * 新增行和 hunk 覆盖行同时供问题定位使用。
 */
public final class PatchDiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private PatchDiffParser() {
    }

    public static Parsed parse(String patch) {
        if (patch == null || patch.isEmpty()) {
            return new Parsed(List.of(), List.of(), List.of());
        }
        List<Row> rows = new ArrayList<>();
        Set<Integer> addedLines = new LinkedHashSet<>();
        Set<Integer> hunkLines = new LinkedHashSet<>();
        int oldLine = 0;
        int newLine = 0;

        for (String raw : patch.split("\n", -1)) {
            String line = stripCr(raw);
            Matcher hunk = HUNK_HEADER.matcher(line);
            if (hunk.matches()) {
                oldLine = Integer.parseInt(hunk.group(1));
                newLine = Integer.parseInt(hunk.group(3));
                rows.add(new Row("hunk", null, null, line));
                continue;
            }
            if (line.startsWith("diff --git") || line.startsWith("index ")
                    || line.startsWith("--- ") || line.startsWith("+++ ")
                    || line.startsWith("\\")) {
                rows.add(new Row("meta", null, null, line));
                continue;
            }
            if (line.startsWith("+")) {
                addedLines.add(newLine);
                hunkLines.add(newLine);
                rows.add(new Row("add", null, newLine++, line.substring(1)));
                continue;
            }
            if (line.startsWith("-")) {
                rows.add(new Row("delete", oldLine++, null, line.substring(1)));
                continue;
            }
            if (line.startsWith(" ")) {
                hunkLines.add(newLine);
                rows.add(new Row("context", oldLine++, newLine++, line.substring(1)));
                continue;
            }
            rows.add(new Row("meta", null, null, line));
        }
        return new Parsed(rows, List.copyOf(addedLines), List.copyOf(hunkLines));
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    public record Row(String type, Integer oldLine, Integer newLine, String content) {
    }

    public record Parsed(List<Row> rows, List<Integer> addedLines, List<Integer> hunkLines) {
    }
}
