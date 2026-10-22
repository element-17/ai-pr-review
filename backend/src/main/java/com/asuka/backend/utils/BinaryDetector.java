package com.asuka.backend.utils;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 常见二进制文件判断。
 *
 * <p>GitHub 对二进制文件通常不返回 patch，但部分大文本文件也可能没有 patch；
 * 这里按扩展名先做一次判断，再结合 patch 是否为空标记跳过原因。
 */
public final class BinaryDetector {

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp", "tiff", "psd",
            "pdf", "zip", "gz", "tar", "rar", "7z", "jar", "war", "class",
            "exe", "dll", "so", "dylib", "bin", "dat",
            "mp3", "mp4", "mov", "avi", "wav", "flac",
            "woff", "woff2", "ttf", "otf", "eot",
            "xls", "xlsx", "doc", "docx", "ppt", "pptx",
            "sqlite", "db", "lock"
    );

    public static boolean isBinary(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return false;
        }
        return BINARY_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private BinaryDetector() {
    }
}
