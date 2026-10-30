package com.asuka.backend.service.impl;

import com.asuka.backend.common.constant.StatusConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.core.github.oauth.GitHubOAuthUser;
import com.asuka.backend.mapper.UserMapper;
import com.asuka.backend.pojo.entity.User;
import com.asuka.backend.pojo.entity.UserGitHubBinding;
import com.asuka.backend.service.GitHubAccountService;
import com.asuka.backend.service.GitHubBindingService;
import com.asuka.backend.service.UserService;
import com.asuka.backend.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub 登录账号匹配/创建。
 *
 * <p>GitHub 数字 id 是唯一绑定依据；login/email 只作为展示和冲突提示，
 * 不会按邮箱自动合并到已有账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubAccountServiceImpl implements GitHubAccountService {

    private final GitHubBindingService bindingService;
    private final UserService userService;
    private final UserSettingService userSettingService;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long loginOrRegister(GitHubOAuthUser githubUser, String accessToken, String scope) {
        UserGitHubBinding binding = bindingService.findByGithubId(githubUser.getId());
        if (binding != null) {
            User user = userMapper.selectById(binding.getUserId());
            if (user == null) {
                throw new BusinessException(401, "GitHub 绑定的本地账号不存在");
            }
            if (!StatusConstant.ENABLED.equals(user.getStatus())) {
                throw new BusinessException(403, "账号已被禁用，请联系管理员");
            }
            // 每次登录刷新可变的展示字段和 token
            bindingService.bind(user.getId(), githubUser, accessToken, scope);
            log.info("GitHub 用户已匹配本地账号：userId={}", user.getId());
            return user.getId();
        }

        Long userId = userService.createGitHubUser(
                githubUser.getLogin(), githubUser.getName(), githubUser.getAvatarUrl(), githubUser.getEmail());
        userSettingService.initDefault(userId);
        bindingService.bind(userId, githubUser, accessToken, scope);
        log.info("GitHub 用户已创建本地账号：userId={}", userId);
        return userId;
    }
}
