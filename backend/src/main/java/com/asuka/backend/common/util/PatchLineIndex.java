package com.asuka.backend.common.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patch 行号索引。
 *
 * <p>解析 unified diff 的 hunk 头 `@@ -a,b +c,d @@`，
 * 记录新文件侧的新增行号和 hunk 覆盖行号，供 F11 校验问题定位。
 */
public final class PatchLineIndex {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    private final Set<Integer> addedLines = new LinkedHashSet<>();

    private final Set<Integer> hunkLines = new LinkedHashSet<>();

    public static PatchLineIndex parse(String patch) {
        PatchLineIndex index = new PatchLineIndex();
        if (patch == null || patch.isEmpty()) {
            return index;
        }

        String[] lines = patch.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            Matcher matcher = HUNK_HEADER.matcher(stripCr(lines[i]));
            if (!matcher.matches()) {
                i++;
                continue;
            }

            int newLine = Integer.parseInt(matcher.group(3));
            i++;
            while (i < lines.length && !stripCr(lines[i]).startsWith("@@")) {
                String line = stripCr(lines[i]);
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    index.addedLines.add(newLine);
                    index.hunkLines.add(newLine);
                    newLine++;
                } else if (line.startsWith(" ")) {
                    index.hunkLines.add(newLine);
                    newLine++;
                }
                // 删除行只消耗旧文件行号；"\ No newline at end of file" 不消耗行号
                i++;
            }
        }
        return index;
    }

    /**
     * 问题行号是否落在新增行上。
     */
    public boolean overlapsAdded(Integer start, Integer end) {
        if (start == null) {
            return false;
        }
        int from = Math.min(start, end == null ? start : end);
        int to = Math.max(start, end == null ? start : end);
        for (int line : addedLines) {
            if (line >= from && line <= to) {
                return true;
            }
        }
        return false;
    }

    /**
     * 问题行号是否落在 hunk 覆盖的新文件行号上（含上下文行）。
     */
    public boolean overlapsHunk(Integer start, Integer end) {
        if (start == null) {
            return false;
        }
        int from = Math.min(start, end == null ? start : end);
        int to = Math.max(start, end == null ? start : end);
        for (int line : hunkLines) {
            if (line >= from && line <= to) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> getAddedLines() {
        return Collections.unmodifiableSet(new HashSet<>(addedLines));
    }

    public Set<Integer> getHunkLines() {
        return Collections.unmodifiableSet(new HashSet<>(hunkLines));
    }

    private static String stripCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    private PatchLineIndex() {
    }
}
