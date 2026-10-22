package com.asuka.backend.service.ai;

import com.asuka.backend.common.util.PatchLineIndex;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.integration.github.GitHubClient;
import com.asuka.backend.integration.github.dto.GitHubContentItem;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务上下文构建器。
 *
 * <p>只允许读取当前仓库 head SHA 下的文件；路径白名单和大小限制在 GitHubClient 与这里双重校验。
 * 文件内容按任务缓存，不跨任务共享。
 */
public class ContextBuilder {

    private static final int MAX_CONTEXT_CHARS = 20_000;
    private static final int MAX_CALLER_FILES = 20;
    private static final int MAX_TEST_FILES = 10;
    private static final Pattern IMPORT_PATH = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern SYMBOL = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");

    private final GitHubClient gitHubClient;
    private final TaskContext taskContext;
    private final Map<String, LanguageContextExtractor> extractors;

    public ContextBuilder(GitHubClient gitHubClient, TaskContext taskContext) {
        this.gitHubClient = gitHubClient;
        this.taskContext = taskContext;
        this.extractors = Map.of(
                "typescript", new TypeScriptContextExtractor(),
                "javascript", new TypeScriptContextExtractor(),
                "python", new PythonContextExtractor()
        );
    }

    public String readFile(String path, Integer startLine, Integer endLine) {
        if (!StringUtils.hasText(path) || path.startsWith("/") || path.contains("..")) {
            return "";
        }
        String content = taskContext.getCache().get(path,
                () -> gitHubClient.getFileContent(taskContext.getOwner(), taskContext.getRepo(),
                        path, taskContext.getHeadSha()));
        if (!StringUtils.hasText(content)) {
            return "";
        }
        if (startLine == null && endLine == null) {
            return truncate(content);
        }
        String[] lines = content.split("\n", -1);
        int start = Math.max(1, startLine == null ? 1 : startLine);
        int end = Math.min(lines.length, endLine == null ? start + 80 : endLine);
        if (start > lines.length || end < start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start - 1; i < end; i++) {
            builder.append(lines[i]).append('\n');
        }
        return truncate(builder.toString());
    }

    /**
     * 提取变更文件涉及函数的上下文和 import。
     */
    public String functionContext(TaskFile file) {
        if (file == null || !StringUtils.hasText(file.getPatch())) {
            return "";
        }
        String content;
        try {
            content = readFile(file.getFilePath(), null, null);
        } catch (Exception ex) {
            return "";
        }
        if (!StringUtils.hasText(content)) {
            return "";
        }

        Set<Integer> addedLines = PatchLineIndex.parse(file.getPatch()).getAddedLines();
        LanguageContextExtractor extractor = extractor(file.getLanguage());
        List<ContextBlock> blocks = extractor == null
                ? List.of()
                : extractor.extract(content, addedLines);
        List<String> imports = extractor == null ? List.of() : extractor.imports(content);

        StringBuilder builder = new StringBuilder();
        builder.append("// file: ").append(file.getFilePath()).append('\n');
        if (!imports.isEmpty()) {
            builder.append("// imports:\n");
            imports.stream().limit(20).forEach(line -> builder.append(line).append('\n'));
        }
        if (blocks.isEmpty()) {
            builder.append("// fallback window around changed lines\n");
            builder.append(fallbackWindow(content, addedLines));
        } else {
            for (ContextBlock block : blocks) {
                builder.append("// function lines ").append(block.startLine())
                        .append('-').append(block.endLine()).append('\n');
                builder.append(block.content());
            }
        }
        return truncate(builder.toString());
    }

    /**
     * 在变更文件、直接 import 文件和同目录文件中查找符号调用。
     */
    public String findCallers(String symbol, String path) {
        if (!StringUtils.hasText(symbol) || !SYMBOL.matcher(symbol).matches()) {
            return "Invalid symbol.";
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (TaskFile file : taskContext.getFiles()) {
            if (StringUtils.hasText(file.getFilePath())) {
                candidates.add(file.getFilePath());
            }
            if (candidates.size() >= MAX_CALLER_FILES) {
                break;
            }
        }
        if (StringUtils.hasText(path)) {
            candidates.add(path);
            candidates.addAll(resolveImports(path));
        }

        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (matches.size() >= MAX_CALLER_FILES) {
                break;
            }
            String content;
            try {
                content = readFile(candidate, null, null);
            } catch (Exception ex) {
                continue;
            }
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String[] lines = content.split("\n", -1);
            int perFile = 0;
            for (int i = 0; i < lines.length && perFile < 5; i++) {
                if (lines[i].contains(symbol)) {
                    matches.add(candidate + ":" + (i + 1) + ": " + lines[i].trim());
                    perFile++;
                    if (matches.size() >= MAX_CALLER_FILES) {
                        break;
                    }
                }
            }
        }
        return matches.isEmpty()
                ? "No callers found in changed or directly imported files."
                : String.join("\n", matches);
    }

    /**
     * 查找同目录/__tests__ 下的测试文件。
     */
    public String readTests(String path) {
        String dir = StringUtils.hasText(path) && path.contains("/")
                ? path.substring(0, path.lastIndexOf('/')) : "";
        List<GitHubContentItem> items;
        try {
            items = gitHubClient.listDirectory(taskContext.getOwner(), taskContext.getRepo(),
                    dir, taskContext.getHeadSha());
        } catch (Exception ex) {
            return "No tests found.";
        }

        List<String> selected = new ArrayList<>();
        for (GitHubContentItem item : items) {
            if (!"file".equals(item.getType())) {
                continue;
            }
            if (isTestFile(item.getName())) {
                selected.add(item.getPath());
            }
            if (selected.size() >= MAX_TEST_FILES) {
                break;
            }
        }

        if (selected.isEmpty() && StringUtils.hasText(dir)) {
            try {
                List<GitHubContentItem> testDir = gitHubClient.listDirectory(
                        taskContext.getOwner(), taskContext.getRepo(),
                        dir + "/__tests__", taskContext.getHeadSha());
                for (GitHubContentItem item : testDir) {
                    if ("file".equals(item.getType()) && selected.size() < MAX_TEST_FILES) {
                        selected.add(item.getPath());
                    }
                }
            } catch (Exception ignored) {
                // 没有 __tests__ 目录
            }
        }

        if (selected.isEmpty()) {
            return "No tests found.";
        }

        StringBuilder builder = new StringBuilder();
        for (String testPath : selected) {
            try {
                builder.append("// test: ").append(testPath).append('\n')
                        .append(readFile(testPath, null, null)).append('\n');
            } catch (Exception ignored) {
                // 单个测试文件读取失败不影响其他文件
            }
        }
        return truncate(builder.toString());
    }

    private List<String> resolveImports(String path) {
        List<String> result = new ArrayList<>();
        String content;
        try {
            content = readFile(path, null, null);
        } catch (Exception ex) {
            return result;
        }
        LanguageContextExtractor extractor = extractor(languageOf(path));
        if (extractor == null) {
            return result;
        }
        for (String importLine : extractor.imports(content)) {
            Matcher matcher = IMPORT_PATH.matcher(importLine);
            if (!matcher.find()) {
                continue;
            }
            String importPath = matcher.group(1);
            if (!importPath.startsWith(".")) {
                continue;
            }
            String baseDir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
            String base = normalize(baseDir.isEmpty() ? importPath : baseDir + "/" + importPath);
            for (String candidate : candidateExtensions(base, path)) {
                try {
                    readFile(candidate, 1, 1);
                    result.add(candidate);
                    break;
                } catch (Exception ignored) {
                    // 继续尝试下一个扩展名
                }
            }
            if (result.size() >= 10) {
                break;
            }
        }
        return result;
    }

    private List<String> candidateExtensions(String base, String fromPath) {
        String lower = fromPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".py")) {
            return List.of(base + ".py", base + "/__init__.py");
        }
        return List.of(base + ".ts", base + ".tsx", base + ".js",
                base + "/index.ts", base + "/index.tsx");
    }

    private String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else {
                parts.add(part);
            }
        }
        return String.join("/", parts);
    }

    private boolean isTestFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".test.ts") || lower.endsWith(".spec.ts")
                || lower.endsWith(".test.tsx") || lower.endsWith(".spec.tsx")
                || (lower.startsWith("test_") && lower.endsWith(".py"))
                || lower.endsWith("_test.py");
    }

    private String fallbackWindow(String content, Set<Integer> addedLines) {
        String[] lines = content.split("\n", -1);
        int min = lines.length;
        int max = 1;
        for (int line : addedLines) {
            min = Math.min(min, line);
            max = Math.max(max, line);
        }
        if (addedLines.isEmpty()) {
            min = 1;
            max = Math.min(lines.length, 120);
        }
        int start = Math.max(1, min - 40);
        int end = Math.min(lines.length, max + 40);
        StringBuilder builder = new StringBuilder();
        for (int i = start - 1; i < end; i++) {
            builder.append(lines[i]).append('\n');
        }
        return builder.toString();
    }

    private LanguageContextExtractor extractor(String language) {
        if (!StringUtils.hasText(language)) {
            return null;
        }
        return extractors.get(language.toLowerCase(Locale.ROOT));
    }

    private String languageOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".js") || lower.endsWith(".jsx")) {
            return "typescript";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        return null;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_CONTEXT_CHARS ? value : value.substring(0, MAX_CONTEXT_CHARS);
    }
}
