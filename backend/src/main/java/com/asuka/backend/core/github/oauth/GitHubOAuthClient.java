package com.asuka.backend.core.github.oauth;

import com.asuka.backend.common.exception.GitHubOAuthException;
import com.asuka.backend.common.properties.GitHubOAuthProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth 与用户资料客户端。
 *
 * <p>授权码交换、用户资料读取和 token 撤销都在服务端完成；
 * 日志只记录路径、状态码和耗时，不记录 code、token 或 GitHub 原始响应。
 */
@Slf4j
@Component
public class GitHubOAuthClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GitHubOAuthProperties properties;
    private final RestClient restClient;

    public GitHubOAuthClient(GitHubOAuthProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .build();
    }

    /**
     * 生成 GitHub 授权页地址，state 由服务端生成并存入 Redis。
     */
    public String buildAuthorizeUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(properties.getAuthorizeUrl())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getCallbackUrl())
                .queryParam("scope", properties.getScopes())
                .queryParam("state", state)
                .queryParam("allow_signup", "true")
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 使用授权码交换 GitHub access token。
     */
    public GitHubOAuthTokenResponse exchangeCode(String code) {
        requireConfigured();
        if (!StringUtils.hasText(code)) {
            throw new GitHubOAuthException(GitHubOAuthException.Reason.TOKEN_EXCHANGE_FAILED,
                    "GitHub 授权码为空");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code", code);
        form.add("redirect_uri", properties.getCallbackUrl());

        long started = System.currentTimeMillis();
        try {
            String body = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            GitHubOAuthTokenResponse response = OBJECT_MAPPER.readValue(body, GitHubOAuthTokenResponse.class);
            if (!StringUtils.hasText(response.getAccessToken())) {
                log.warn("GitHub OAuth 授权码交换失败：error={}, durationMs={}",
                        response.getError(), System.currentTimeMillis() - started);
                throw new GitHubOAuthException(GitHubOAuthException.Reason.TOKEN_EXCHANGE_FAILED,
                        "GitHub 授权码交换失败");
            }
            log.info("GitHub OAuth 授权码交换成功：durationMs={}", System.currentTimeMillis() - started);
            return response;
        } catch (GitHubOAuthException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("GitHub OAuth 授权码交换失败：HTTP {}, durationMs={}",
                    ex.getStatusCode().value(), System.currentTimeMillis() - started);
            throw new GitHubOAuthException(GitHubOAuthException.Reason.TOKEN_EXCHANGE_FAILED,
                    "GitHub 授权码交换失败");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub OAuth 授权码交换超时：durationMs={}", System.currentTimeMillis() - started);
            throw new GitHubOAuthException(GitHubOAuthException.Reason.TOKEN_EXCHANGE_FAILED,
                    "GitHub 授权码交换超时", ex);
        } catch (Exception ex) {
            log.warn("GitHub OAuth 授权码响应解析失败：{}", ex.getClass().getSimpleName());
            throw new GitHubOAuthException(GitHubOAuthException.Reason.TOKEN_EXCHANGE_FAILED,
                    "GitHub 授权码响应解析失败", ex);
        }
    }

    /**
     * 读取 GitHub 用户资料。id 是唯一标识，login/avatar 可能变化。
     */
    public GitHubOAuthUser getUser(String accessToken) {
        long started = System.currentTimeMillis();
        try {
            String body = restClient.get()
                    .uri(properties.getUserApiUrl() + "/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            GitHubOAuthUser user = OBJECT_MAPPER.readValue(body, GitHubOAuthUser.class);
            if (user.getId() == null || !StringUtils.hasText(user.getLogin())) {
                throw new GitHubOAuthException(GitHubOAuthException.Reason.USER_FETCH_FAILED,
                        "GitHub 用户信息不完整");
            }
            log.info("GitHub 用户资料读取成功：durationMs={}", System.currentTimeMillis() - started);
            return user;
        } catch (GitHubOAuthException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("GitHub 用户资料读取失败：HTTP {}, durationMs={}",
                    ex.getStatusCode().value(), System.currentTimeMillis() - started);
            throw new GitHubOAuthException(GitHubOAuthException.Reason.USER_FETCH_FAILED,
                    "GitHub 用户资料读取失败");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub 用户资料读取超时：durationMs={}", System.currentTimeMillis() - started);
            throw new GitHubOAuthException(GitHubOAuthException.Reason.USER_FETCH_FAILED,
                    "GitHub 用户资料读取超时", ex);
        } catch (Exception ex) {
            log.warn("GitHub 用户资料解析失败：{}", ex.getClass().getSimpleName());
            throw new GitHubOAuthException(GitHubOAuthException.Reason.USER_FETCH_FAILED,
                    "GitHub 用户资料解析失败", ex);
        }
    }

    /**
     * 读取主邮箱。邮箱只作辅助展示，失败不阻断登录。
     */
    public String getPrimaryEmail(String accessToken) {
        try {
            String body = restClient.get()
                    .uri(properties.getUserApiUrl() + "/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            List<GitHubOAuthEmail> emails = OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
            if (emails == null || emails.isEmpty()) {
                return null;
            }
            return emails.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getVerified()))
                    .filter(item -> Boolean.TRUE.equals(item.getPrimary()))
                    .map(GitHubOAuthEmail::getEmail)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElseGet(() -> emails.stream()
                            .filter(item -> Boolean.TRUE.equals(item.getVerified()))
                            .map(GitHubOAuthEmail::getEmail)
                            .filter(StringUtils::hasText)
                            .findFirst()
                            .orElse(null));
        } catch (Exception ex) {
            log.debug("GitHub 邮箱读取失败，忽略邮箱信息：{}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 撤销 GitHub OAuth token。解绑时 best-effort 调用，失败不回滚本地解绑。
     */
    public void revokeToken(String accessToken) {
        requireConfigured();
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(properties.getUserApiUrl() + "/applications/" + properties.getClientId() + "/token")
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(Map.of("access_token", accessToken))
                    .retrieve()
                    .toBodilessEntity();
            log.info("GitHub token 撤销成功");
        } catch (Exception ex) {
            log.warn("GitHub token 撤销失败，本地绑定仍按用户请求删除：{}", ex.getClass().getSimpleName());
            throw new GitHubOAuthException(GitHubOAuthException.Reason.REVOKE_FAILED,
                    "GitHub token 撤销失败", ex);
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(properties.getClientId())
                || !StringUtils.hasText(properties.getClientSecret())) {
            throw new GitHubOAuthException(GitHubOAuthException.Reason.NOT_CONFIGURED,
                    "GitHub OAuth 未配置");
        }
    }
}
