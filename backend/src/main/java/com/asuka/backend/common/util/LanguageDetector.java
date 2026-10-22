package com.asuka.backend.common.util;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 根据文件扩展名推断语言，用于文件展示和上下文解析策略。
 */
public final class LanguageDetector {

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("ts", "typescript"),
            Map.entry("tsx", "typescript"),
            Map.entry("js", "javascript"),
            Map.entry("jsx", "javascript"),
            Map.entry("py", "python"),
            Map.entry("java", "java"),
            Map.entry("kt", "kotlin"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("cs", "csharp"),
            Map.entry("c", "c"),
            Map.entry("h", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("cc", "cpp"),
            Map.entry("cxx", "cpp"),
            Map.entry("swift", "swift"),
            Map.entry("scala", "scala"),
            Map.entry("sql", "sql"),
            Map.entry("sh", "shell"),
            Map.entry("bash", "shell"),
            Map.entry("yml", "yaml"),
            Map.entry("yaml", "yaml"),
            Map.entry("json", "json"),
            Map.entry("xml", "xml"),
            Map.entry("html", "html"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("md", "markdown")
    );

    public static String detect(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return null;
        }
        return LANGUAGES.get(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private LanguageDetector() {
    }
}
