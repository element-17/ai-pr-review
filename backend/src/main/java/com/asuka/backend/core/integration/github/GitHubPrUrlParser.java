package com.asuka.backend.core.integration.github;

import com.asuka.backend.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub PR URL 解析。
 *
 * <p>只接受 https://github.com/{owner}/{repo}/pull/{number}，
 * 允许末尾斜杠、查询参数和锚点；其他域名、非 HTTPS、缺少编号都拒绝。
 */
public final class GitHubPrUrlParser {

    private static final Pattern PR_PATH =
            Pattern.compile("^/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/(\\d+)/?$");

    public static PrUrl parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(400, "PR URL 不能为空");
        }

        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "PR URL 格式错误");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException(400, "PR URL 格式错误");
        }

        Matcher matcher = PR_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (!matcher.matches()) {
            throw new BusinessException(400, "PR URL 格式错误");
        }

        int number;
        try {
            number = Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "PR URL 格式错误");
        }
        if (number <= 0) {
            throw new BusinessException(400, "PR URL 格式错误");
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);
        return new PrUrl(owner, repo, number, owner + "/" + repo);
    }

    /**
     * 解析后的 PR 引用。
     */
    public record PrUrl(String owner, String repo, int number, String repoFullName) {
    }

    private GitHubPrUrlParser() {
    }
}
