package com.asuka.backend.service.impl;

import com.asuka.backend.common.constant.AuthConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.exception.GitHubOAuthException;
import com.asuka.backend.common.properties.GitHubOAuthProperties;
import com.asuka.backend.core.github.oauth.GitHubOAuthClient;
import com.asuka.backend.core.github.oauth.GitHubOAuthTokenResponse;
import com.asuka.backend.core.github.oauth.GitHubOAuthUser;
import com.asuka.backend.pojo.vo.LoginVO;
import com.asuka.backend.pojo.vo.OAuthAuthorizeVO;
import com.asuka.backend.service.AuthService;
import com.asuka.backend.service.GitHubAccountService;
import com.asuka.backend.service.GitHubBindingService;
import com.asuka.backend.service.GitHubOAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * GitHub OAuth 登录/绑定实现。
 *
 * <p>state 和 ticket 都是一次性 Redis 凭证：state 绑定模式（LOGIN/BIND）和本地 userId，
 * ticket 只保存已校验的 userId。GitHub access token 不进入回调 URL、前端存储或日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubOAuthServiceImpl implements GitHubOAuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String MODE_LOGIN = "LOGIN";
    private static final String MODE_BIND = "BIND";

    private final GitHubOAuthProperties properties;
    private final GitHubOAuthClient oauthClient;
    private final GitHubAccountService accountService;
    private final GitHubBindingService bindingService;
    private final AuthService authService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public OAuthAuthorizeVO beginLogin() {
        String state = newRandomToken();
        storeState(state, MODE_LOGIN, null);
        return OAuthAuthorizeVO.builder()
                .authorizeUrl(oauthClient.buildAuthorizeUrl(state))
                .state(state)
                .build();
    }

    @Override
    public OAuthAuthorizeVO beginBind(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        String state = newRandomToken();
        storeState(state, MODE_BIND, userId);
        return OAuthAuthorizeVO.builder()
                .authorizeUrl(oauthClient.buildAuthorizeUrl(state))
                .state(state)
                .build();
    }

    @Override
    public String handleCallback(String code, String state, String error) {
        OAuthState payload = consumeState(state);
        if (payload == null) {
            log.warn("GitHub OAuth 回调 state 无效或已过期");
            return redirectError(null, "STATE_INVALID");
        }
        if (StringUtils.hasText(error)) {
            log.info("GitHub OAuth 授权未完成：mode={}, error={}", payload.getMode(), error);
            return redirectError(payload, "access_denied".equalsIgnoreCase(error) ? "CANCELED" : "FAILED");
        }
        if (!StringUtils.hasText(code)) {
            return redirectError(payload, "TOKEN_EXCHANGE_FAILED");
        }

        try {
            GitHubOAuthTokenResponse token = oauthClient.exchangeCode(code);
            GitHubOAuthUser user = oauthClient.getUser(token.getAccessToken());
            if (!StringUtils.hasText(user.getEmail())) {
                user.setEmail(oauthClient.getPrimaryEmail(token.getAccessToken()));
            }

            if (MODE_BIND.equals(payload.getMode())) {
                if (payload.getUserId() == null) {
                    return redirectError(payload, "STATE_INVALID");
                }
                bindingService.bind(payload.getUserId(), user, token.getAccessToken(), token.getScope());
                log.info("GitHub 绑定成功：userId={}", payload.getUserId());
                return redirectBind("SUCCESS");
            }

            Long userId = accountService.loginOrRegister(user, token.getAccessToken(), token.getScope());
            String ticket = createTicket(userId);
            return buildUrl(properties.getFrontendLoginCallbackUrl(), "ticket", ticket);
        } catch (GitHubOAuthException ex) {
            log.warn("GitHub OAuth 回调失败：reason={}", ex.getReason());
            return redirectError(payload, ex.getReason().name());
        } catch (BusinessException ex) {
            String reason = mapBusinessReason(ex);
            log.warn("GitHub OAuth 账号匹配失败：reason={}", reason);
            return redirectError(payload, reason);
        } catch (Exception ex) {
            log.error("GitHub OAuth 回调异常", ex);
            return redirectError(payload, "FAILED");
        }
    }

    @Override
    public LoginVO exchangeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new BusinessException(400, "ticket 不能为空");
        }
        String key = AuthConstant.GITHUB_OAUTH_TICKET_KEY_PREFIX + ticket;
        String json = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(json)) {
            throw new BusinessException(400, "ticket 已失效，请重新登录");
        }
        try {
            TicketPayload payload = OBJECT_MAPPER.readValue(json, TicketPayload.class);
            return authService.issueTokensForUser(payload.getUserId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("GitHub 登录 ticket 解析失败：{}", ex.getClass().getSimpleName());
            throw new BusinessException(400, "ticket 已失效，请重新登录");
        }
    }

    private void storeState(String state, String mode, Long userId) {
        OAuthState payload = new OAuthState();
        payload.setMode(mode);
        payload.setUserId(userId);
        try {
            stringRedisTemplate.opsForValue().set(
                    AuthConstant.GITHUB_OAUTH_STATE_KEY_PREFIX + state,
                    OBJECT_MAPPER.writeValueAsString(payload),
                    Duration.ofSeconds(Math.max(60, properties.getStateTtlSeconds())));
        } catch (Exception ex) {
            throw new BusinessException(500, "GitHub 授权初始化失败");
        }
    }

    private OAuthState consumeState(String state) {
        if (!StringUtils.hasText(state)) {
            return null;
        }
        String key = AuthConstant.GITHUB_OAUTH_STATE_KEY_PREFIX + state;
        String json = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, OAuthState.class);
        } catch (Exception ex) {
            log.warn("GitHub OAuth state 解析失败：{}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private String createTicket(Long userId) {
        TicketPayload payload = new TicketPayload();
        payload.setUserId(userId);
        String ticket = newRandomToken();
        try {
            stringRedisTemplate.opsForValue().set(
                    AuthConstant.GITHUB_OAUTH_TICKET_KEY_PREFIX + ticket,
                    OBJECT_MAPPER.writeValueAsString(payload),
                    Duration.ofSeconds(Math.max(30, properties.getTicketTtlSeconds())));
        } catch (Exception ex) {
            throw new BusinessException(500, "GitHub 登录 ticket 创建失败");
        }
        return ticket;
    }

    private String redirectError(OAuthState payload, String reason) {
        if (payload != null && MODE_BIND.equals(payload.getMode())) {
            return buildUrl(properties.getFrontendBindResultUrl(), "githubBindError", reason);
        }
        return buildUrl(properties.getFrontendLoginCallbackUrl(), "githubError", reason);
    }

    private String redirectBind(String result) {
        return buildUrl(properties.getFrontendBindResultUrl(), "githubBind", result);
    }

    private String buildUrl(String baseUrl, String name, String value) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam(name, value)
                .build()
                .encode()
                .toUriString();
    }

    private String mapBusinessReason(BusinessException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("邮箱")) {
            return "EMAIL_EXISTS";
        }
        if (message.contains("已绑定")) {
            return "ACCOUNT_CONFLICT";
        }
        if (message.contains("禁用")) {
            return "ACCOUNT_DISABLED";
        }
        return "FAILED";
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    @Data
    public static class OAuthState {
        private String mode;
        private Long userId;
    }

    @Data
    public static class TicketPayload {
        private Long userId;
    }
}
