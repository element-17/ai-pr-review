package com.asuka.backend.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具。
 *
 * <p>日志本身不应包含密钥、token 或密码；这里再做一层兜底，
 * 防止异常信息、第三方响应或误用日志把敏感值带出去。
 */
public final class LogSanitizer {

    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(\"[^\"]*\"|(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+|[^\\s,;}]+)");

    private static final Pattern SECRET_KEY_VALUE = Pattern.compile(
            "(?i)(\"?(?:api[-_]?key|apikey|access[-_]?token|refresh[-_]?token|guest[-_]?token|password|passwd|secret|token)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\s,;}]+)");

    private static final Pattern BEARER = Pattern.compile(
            "(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    private static final Pattern TOKEN_PREFIX = Pattern.compile(
            "\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|user_[A-Za-z0-9]{16,}|sk-[A-Za-z0-9]{16,})\\b");

    private LogSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String result = AUTHORIZATION.matcher(message).replaceAll("$1***");
        result = BEARER.matcher(result).replaceAll("$1***");
        result = SECRET_KEY_VALUE.matcher(result).replaceAll("$1***");
        Matcher tokenMatcher = TOKEN_PREFIX.matcher(result);
        return tokenMatcher.replaceAll("***");
    }
}
