package com.asuka.backend.service.impl;

import com.asuka.backend.common.constant.GitHubBindingConstant;
import com.asuka.backend.common.crypto.SecretCipher;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.GitHubOAuthProperties;
import com.asuka.backend.core.github.oauth.GitHubOAuthClient;
import com.asuka.backend.core.github.oauth.GitHubOAuthUser;
import com.asuka.backend.mapper.UserGitHubBindingMapper;
import com.asuka.backend.mapper.UserMapper;
import com.asuka.backend.pojo.entity.User;
import com.asuka.backend.pojo.entity.UserGitHubBinding;
import com.asuka.backend.pojo.vo.GitHubBindingStatusVO;
import com.asuka.backend.service.GitHubBindingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * GitHub 绑定服务实现。
 *
 * <p>一个本地用户最多绑定一个 GitHub 账号，一个 GitHub 数字 id 最多绑定一个本地用户；
 * token 只以 AES-GCM 密文落库，解绑时 best-effort 撤销 GitHub 端 token。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubBindingServiceImpl implements GitHubBindingService {

    private static final Map<String, Object> REAUTHORIZE_DATA = Map.of(
            "githubAuthRequired", true,
            "action", "REAUTHORIZE"
    );

    private final UserGitHubBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final SecretCipher secretCipher;
    private final GitHubOAuthClient oauthClient;
    private final GitHubOAuthProperties oauthProperties;

    @Override
    public UserGitHubBinding findByUserId(Long userId) {
        return userId == null ? null : bindingMapper.selectById(userId);
    }

    @Override
    public UserGitHubBinding findByGithubId(Long githubId) {
        if (githubId == null) {
            return null;
        }
        return bindingMapper.selectOne(new LambdaQueryWrapper<UserGitHubBinding>()
                .eq(UserGitHubBinding::getGithubId, githubId)
                .last("LIMIT 1"));
    }

    @Override
    public GitHubBindingStatusVO getStatus(Long userId) {
        User user = userMapper.selectById(userId);
        UserGitHubBinding binding = findByUserId(userId);
        GitHubBindingStatusVO vo = new GitHubBindingStatusVO();
        vo.setBound(binding != null);
        vo.setPasswordSet(user == null || user.getPasswordSet() == null || user.getPasswordSet() != 0);
        if (binding != null) {
            vo.setGithubLogin(binding.getGithubLogin());
            vo.setAvatarUrl(binding.getAvatarUrl());
            vo.setEmail(binding.getEmail());
            vo.setScope(binding.getScope());
            vo.setTokenStatus(binding.getTokenStatus());
            vo.setAuthorizedAt(binding.getAuthorizedAt());
            vo.setLastValidatedAt(binding.getLastValidatedAt());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long userId, GitHubOAuthUser user, String accessToken, String scope) {
        if (userId == null || user == null || user.getId() == null) {
            throw new BusinessException(400, "GitHub 绑定参数不完整");
        }
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(400, "GitHub access token 为空");
        }

        UserGitHubBinding byGithubId = findByGithubId(user.getId());
        if (byGithubId != null && !userId.equals(byGithubId.getUserId())) {
            throw new BusinessException(409, "该 GitHub 账号已绑定其他本地账号");
        }

        UserGitHubBinding existing = findByUserId(userId);
        if (existing != null && !user.getId().equals(existing.getGithubId())) {
            throw new BusinessException(409, "当前账号已绑定其他 GitHub 账号");
        }

        LocalDateTime now = LocalDateTime.now();
        UserGitHubBinding binding = existing == null ? new UserGitHubBinding() : existing;
        binding.setUserId(userId);
        binding.setGithubId(user.getId());
        binding.setGithubLogin(user.getLogin());
        binding.setAvatarUrl(user.getAvatarUrl());
        binding.setEmail(user.getEmail());
        binding.setAccessTokenCiphertext(secretCipher.encrypt(accessToken));
        binding.setScope(scope);
        binding.setTokenStatus(GitHubBindingConstant.TOKEN_ACTIVE);
        binding.setAuthorizedAt(now);
        binding.setLastValidatedAt(now);
        try {
            if (existing == null) {
                bindingMapper.insert(binding);
            } else {
                bindingMapper.updateById(binding);
            }
        } catch (DuplicateKeyException ex) {
            // 并发下唯一索引兜底，避免两个本地账号同时绑定同一个 GitHub 账号
            throw new BusinessException(409, "该 GitHub 账号已绑定其他本地账号");
        }
    }

    @Override
    public String getActiveAccessTokenOrNull(Long userId) {
        UserGitHubBinding binding = findByUserId(userId);
        if (binding == null) {
            return null;
        }
        if (binding.getTokenStatus() == null
                || binding.getTokenStatus() != GitHubBindingConstant.TOKEN_ACTIVE) {
            throw new BusinessException(403, "GitHub 授权已失效，请重新授权", REAUTHORIZE_DATA);
        }
        try {
            String token = secretCipher.decrypt(binding.getAccessTokenCiphertext());
            if (!StringUtils.hasText(token)) {
                throw new BusinessException(500, "GitHub token 解密结果为空");
            }
            return token;
        } catch (BusinessException ex) {
            markInvalid(userId);
            throw new BusinessException(403, "GitHub 授权已失效，请重新授权", REAUTHORIZE_DATA);
        }
    }

    @Override
    public void markInvalid(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        UserGitHubBinding update = new UserGitHubBinding();
        update.setUserId(userId);
        update.setTokenStatus(GitHubBindingConstant.TOKEN_INVALID);
        update.setLastValidatedAt(now);
        bindingMapper.updateById(update);
        log.warn("用户 {} 的 GitHub token 已标记失效", userId);
    }

    @Override
    public void markValidated(Long userId) {
        UserGitHubBinding update = new UserGitHubBinding();
        update.setUserId(userId);
        update.setLastValidatedAt(LocalDateTime.now());
        bindingMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (user.getPasswordSet() == null || user.getPasswordSet() == 0) {
            throw new BusinessException(409, "请先设置密码再解绑 GitHub",
                    Map.of("passwordRequired", true));
        }

        UserGitHubBinding binding = findByUserId(userId);
        if (binding == null) {
            return;
        }
        try {
            String token = secretCipher.decrypt(binding.getAccessTokenCiphertext());
            if (StringUtils.hasText(oauthProperties.getClientId())
                    && StringUtils.hasText(oauthProperties.getClientSecret())) {
                oauthClient.revokeToken(token);
            }
        } catch (Exception ex) {
            log.debug("解绑时撤销 GitHub token 失败，继续删除本地绑定：{}", ex.getClass().getSimpleName());
        }
        bindingMapper.deleteById(userId);
    }
}
