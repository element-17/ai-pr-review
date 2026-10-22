package com.asuka.backend.core.integration.github;

import com.asuka.backend.common.exception.GitHubApiException;
import com.asuka.backend.common.properties.GitHubProperties;
import com.asuka.backend.core.integration.github.dto.GitHubCommitResponse;
import com.asuka.backend.core.integration.github.dto.GitHubContentItem;
import com.asuka.backend.core.integration.github.dto.GitHubFileResponse;
import com.asuka.backend.core.integration.github.dto.GitHubPrResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * GitHub REST API 客户端。
 *
 * <p>不引入 GitHub SDK；使用 Spring RestClient 手写请求。
 * 响应体先取字符串，再用独立的 Jackson 2 ObjectMapper 解析，
 * 避免 Spring Boot 4 的 Jackson 3 与外部 DTO 注解不一致。
 */
@Slf4j
@Component
public class GitHubClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int MAX_FILE_PAGES = 30;
    private static final int PER_PAGE = 100;
    private static final int MAX_CONTENT_CHARS = 200_000;
    private static final int MAX_RETRIES = 2;

    private final RestClient restClient;

    public GitHubClient(GitHubProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", properties.getUserAgent());

        if (StringUtils.hasText(properties.getToken())) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getToken());
        }
        this.restClient = builder.build();
    }

    /**
     * 拉取 PR 元数据。
     */
    public GitHubPrResponse getPullRequest(String owner, String repo, int number) {
        String json = getForString("/repos/{owner}/{repo}/pulls/{number}", null, owner, repo, number);
        return read(json, GitHubPrResponse.class);
    }

    /**
     * 拉取 PR 文件变更，自动分页。GitHub 最多返回 3000 个文件。
     */
    public List<GitHubFileResponse> listPullRequestFiles(String owner, String repo, int number) {
        List<GitHubFileResponse> all = new ArrayList<>();
        for (int page = 1; page <= MAX_FILE_PAGES; page++) {
            String json = getForString(
                    "/repos/{owner}/{repo}/pulls/{number}/files?per_page=" + PER_PAGE + "&page={page}",
                    null, owner, repo, number, page);
            List<GitHubFileResponse> current = readList(json, new TypeReference<>() {
            });
            all.addAll(current);
            if (current.size() < PER_PAGE) {
                break;
            }
        }
        return all;
    }

    /**
     * 拉取 PR 提交信息，最多 max 条。
     */
    public List<GitHubCommitResponse> listPullRequestCommits(String owner, String repo, int number, int max) {
        List<GitHubCommitResponse> all = new ArrayList<>();
        int target = Math.max(1, Math.min(max, 100));
        for (int page = 1; page <= 2; page++) {
            String json = getForString(
                    "/repos/{owner}/{repo}/pulls/{number}/commits?per_page=" + PER_PAGE + "&page={page}",
                    null, owner, repo, number, page);
            List<GitHubCommitResponse> current = readList(json, new TypeReference<>() {
            });
            all.addAll(current);
            if (current.size() < PER_PAGE || all.size() >= target) {
                break;
            }
        }
        return all.size() > target ? all.subList(0, target) : all;
    }

    /**
     * 按 head SHA 读取仓库文件内容。
     *
     * <p>只允许仓库相对路径；读取结果超过 200KB 或包含二进制内容时拒绝。
     */
    public String getFileContent(String owner, String repo, String path, String ref) {
        if (!StringUtils.hasText(path) || path.startsWith("/") || path.contains("..")) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "文件路径非法");
        }
        String encodedPath = encodePath(path);
        String content = getForString(
                "/repos/{owner}/{repo}/contents/" + encodedPath + "?ref={ref}",
                "application/vnd.github.raw", owner, repo, ref);
        if (content == null) {
            throw new GitHubApiException(GitHubApiException.Kind.NOT_FOUND, 404, "文件不存在");
        }
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "文件内容超过上限");
        }
        if (content.indexOf('\0') >= 0) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "文件是二进制内容");
        }
        return content;
    }

    /**
     * 列出目录内容，用于查找测试文件和同目录调用方。
     */
    public List<GitHubContentItem> listDirectory(String owner, String repo, String path, String ref) {
        String suffix = StringUtils.hasText(path) && !".".equals(path)
                ? "/" + encodePath(path) : "";
        String json = getForString(
                "/repos/{owner}/{repo}/contents" + suffix + "?ref={ref}",
                "application/vnd.github+json", owner, repo, ref);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<GitHubContentItem> items = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    items.add(OBJECT_MAPPER.treeToValue(node, GitHubContentItem.class));
                }
            } else if (root.isObject()) {
                items.add(OBJECT_MAPPER.treeToValue(root, GitHubContentItem.class));
            }
            return items;
        } catch (Exception ex) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0,
                    "GitHub 目录响应解析失败", ex);
        }
    }

    private String getForString(String path, String accept, Object... uriVars) {
        long started = System.currentTimeMillis();
        int attempt = 0;
        while (true) {
            try {
                var uriSpec = restClient.get().uri(path, uriVars);
                var spec = StringUtils.hasText(accept)
                        ? uriSpec.accept(MediaType.parseMediaType(accept))
                        : uriSpec;
                String body = spec.retrieve().body(String.class);
                long durationMs = System.currentTimeMillis() - started;
                if (durationMs >= 1000) {
                    log.info("GitHub API 慢请求：path={}, durationMs={}", path, durationMs);
                } else {
                    log.debug("GitHub API 完成：path={}, durationMs={}", path, durationMs);
                }
                return body;
            } catch (RestClientResponseException ex) {
                if (shouldRetry(ex) && attempt < MAX_RETRIES) {
                    long delay = retryDelay(ex, attempt);
                    log.warn("GitHub API 暂时失败，{}ms 后重试：status={}, durationMs={}",
                            delay, ex.getStatusCode().value(), System.currentTimeMillis() - started);
                    sleep(delay);
                    attempt++;
                    continue;
                }
                GitHubApiException mapped = mapException(ex);
                log.warn("GitHub API 失败：path={}, status={}, durationMs={}",
                        path, ex.getStatusCode().value(), System.currentTimeMillis() - started);
                throw mapped;
            } catch (ResourceAccessException ex) {
                log.warn("GitHub API 超时：path={}, durationMs={}",
                        path, System.currentTimeMillis() - started);
                throw new GitHubApiException(GitHubApiException.Kind.TIMEOUT, 0, "GitHub 请求超时", ex);
            }
        }
    }

    private boolean shouldRetry(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private long retryDelay(RestClientResponseException ex, int attempt) {
        String retryAfter = ex.getResponseHeaders() == null
                ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (StringUtils.hasText(retryAfter)) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()) * 1000, 5000);
            } catch (NumberFormatException ignored) {
                // 使用默认退避
            }
        }
        return 500L * (attempt + 1);
    }

    private GitHubApiException mapException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        boolean rateLimited = status == 429 || (status == 403 && isRateLimited(ex));
        GitHubApiException.Kind kind;
        if (status == 404) {
            kind = GitHubApiException.Kind.NOT_FOUND;
        } else if (rateLimited) {
            kind = GitHubApiException.Kind.RATE_LIMITED;
        } else if (status == 401 || status == 403) {
            kind = GitHubApiException.Kind.FORBIDDEN;
        } else if (status >= 500) {
            kind = GitHubApiException.Kind.SERVER_ERROR;
        } else {
            kind = GitHubApiException.Kind.UNKNOWN;
        }
        // 不打印响应体，避免把 GitHub 原始内容或敏感信息写入日志
        return new GitHubApiException(kind, status, "GitHub 请求失败");
    }

    private boolean isRateLimited(RestClientResponseException ex) {
        if (ex.getResponseHeaders() == null) {
            return false;
        }
        String remaining = ex.getResponseHeaders().getFirst("X-RateLimit-Remaining");
        return "0".equals(remaining) || ex.getResponseHeaders().getFirst("Retry-After") != null;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "GitHub 响应解析失败", ex);
        }
    }

    private <T> T readList(String json, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "GitHub 响应解析失败", ex);
        }
    }

    private String encodePath(String path) {
        return Arrays.stream(path.split("/", -1))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(GitHubApiException.Kind.UNKNOWN, 0, "GitHub 重试被中断", ex);
        }
    }
}
