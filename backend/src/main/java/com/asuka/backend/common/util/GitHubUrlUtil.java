package com.asuka.backend.common.util;

import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * GitHub 页面链接构造工具。
 *
 * <p>只用于生成前端可点击的 GitHub 链接，不参与 API 调用。
 * 链接使用任务固定的 head_sha，保证报告中的定位与评审版本一致。
 */
public final class GitHubUrlUtil {

    private static final String GITHUB_BASE = "https://github.com/";

    /**
     * 文件 blob 链接，可选行号锚点。
     */
    public static String blobUrl(String repoFullName, String headSha, String filePath,
                                 Integer startLine, Integer endLine) {
        if (!StringUtils.hasText(repoFullName) || !StringUtils.hasText(headSha)
                || !StringUtils.hasText(filePath)) {
            return null;
        }
        StringBuilder url = new StringBuilder(GITHUB_BASE)
                .append(encodePath(repoFullName))
                .append("/blob/")
                .append(headSha)
                .append('/')
                .append(encodePath(filePath));
        if (startLine != null && startLine > 0) {
            url.append("#L").append(startLine);
            if (endLine != null && endLine > startLine) {
                url.append("-L").append(endLine);
            }
        }
        return url.toString();
    }

    /**
     * PR 文件页链接，删除文件无法定位到 blob，跳到 PR 的 files 页面。
     */
    public static String pullFilesUrl(String repoFullName, Integer prNumber) {
        if (!StringUtils.hasText(repoFullName) || prNumber == null) {
            return null;
        }
        return GITHUB_BASE + encodePath(repoFullName) + "/pull/" + prNumber + "/files";
    }

    /**
     * 按路径段编码，保留 / 分隔符，避免空格和特殊字符破坏 URL。
     */
    private static String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                        .replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private GitHubUrlUtil() {
    }
}
