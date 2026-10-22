package com.asuka.backend.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.constant.AuthConstant;
import com.asuka.backend.constant.StatusConstant;
import com.asuka.backend.exception.BusinessException;
import com.asuka.backend.dto.LoginDTO;
import com.asuka.backend.dto.RegisterDTO;
import com.asuka.backend.dto.UserCreateDTO;
import com.asuka.backend.entity.SysUser;
import com.asuka.backend.mapper.SysUserMapper;
import com.asuka.backend.service.AuthService;
import com.asuka.backend.service.SysUserService;
import com.asuka.backend.service.UserSettingService;
import com.asuka.backend.vo.LoginVO;
import com.asuka.backend.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 认证服务实现：Sa-Token access-token + Redis refresh-token 双 token。
 *
 * <p>令牌设计：
 * <ul>
 *     <li>access-token：由 Sa-Token 生成和校验，默认 30 分钟，放在 Authorization: Bearer 中；</li>
 *     <li>refresh-token：随机 UUID，存在 Redis，默认 7 天，只用于 /api/auth/refresh；</li>
 *     <li>刷新时立即删除旧 refresh-token（旋转），防止重复使用；</li>
 *     <li>退出时注销当前 access-token，并删除该用户全部 refresh-token。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysUserService sysUserService;
    private final UserSettingService userSettingService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 登录流程：
     * 1. 检查 Redis 中的连续失败次数，防止暴力破解；
     * 2. 查询用户并校验 BCrypt 密码；
     * 3. 校验账号状态；
     * 4. Sa-Token 登录，签发 access-token；
     * 5. 生成 refresh-token 并写入 Redis；
     * 6. 更新最后登录时间，返回双 token。
     */
    @Override
    public LoginVO login(LoginDTO dto) {
        String username = dto.getUsername().trim();

        // 登录前先检查失败次数，避免已经锁定后还继续查库/比对密码
        checkLoginFail(username);

        SysUser user = sysUserMapper.selectByUsernameForLogin(username);
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            // 用户名不存在和密码错误统一提示，避免泄露账号是否存在
            recordLoginFail(username);
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (!StatusConstant.ENABLED.equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        // 登录成功，清除失败计数
        clearLoginFail(username);

        // 签发 access-token，并把角色写入 Sa-Token Session 供权限注解使用
        doLogin(user);
        String accessToken = StpUtil.getTokenValue();

        // 生成 refresh-token；与 access-token 分离，便于独立过期和撤销
        String refreshToken = createRefreshToken(user.getId());

        // 最后登录时间属于单表更新，直接使用 MP updateById，自动填充 updateTime
        updateLastLoginTime(user.getId());

        return buildLoginVO(user, accessToken, refreshToken);
    }

    /**
     * 刷新流程：
     * 1. 从 Redis 校验 refresh-token；
     * 2. 删除旧 refresh-token，实现旋转；
     * 3. 重新签发 access-token 和新的 refresh-token；
     * 4. 账号被禁用或删除时拒绝刷新。
     */
    @Override
    public LoginVO refresh(String refreshToken) {
        String refreshKey = AuthConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken;
        String userIdValue = stringRedisTemplate.opsForValue().get(refreshKey);
        if (!StringUtils.hasText(userIdValue)) {
            throw new BusinessException(401, "refresh-token 已失效，请重新登录");
        }

        Long userId = Long.valueOf(userIdValue);

        // 旋转：旧 refresh-token 立即失效，防止被重复使用
        stringRedisTemplate.delete(refreshKey);
        stringRedisTemplate.opsForSet().remove(AuthConstant.REFRESH_TOKEN_USER_KEY_PREFIX + userId, refreshToken);

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        if (!StatusConstant.ENABLED.equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        doLogin(user);
        String newAccessToken = StpUtil.getTokenValue();
        String newRefreshToken = createRefreshToken(userId);

        return buildLoginVO(user, newAccessToken, newRefreshToken);
    }

    /**
     * 退出登录：注销当前 access-token，并清理该用户所有 refresh-token。
     * 这样即使 refresh-token 被复制，也无法继续换取新 token。
     */
    @Override
    public void logout() {
        Long userId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        deleteRefreshTokens(userId);
    }

    @Override
    public UserInfoVO currentUser() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在，请重新登录");
        }
        return toUserInfo(user);
    }

    /**
     * 注册普通用户。管理员创建用户走 /api/users 接口。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        UserCreateDTO createDTO = new UserCreateDTO();
        BeanUtils.copyProperties(dto, createDTO);
        createDTO.setRole(AuthConstant.ROLE_USER);
        Long userId = sysUserService.createUser(createDTO);
        // 注册时同步初始化默认设置，避免第一次打开设置页时出现空状态
        userSettingService.initDefault(userId);
    }

    /**
     * Sa-Token 登录：timeout 使用全局配置，默认 1800 秒。
     * 登录后把角色放入 Session，StpInterfaceImpl 读取该角色做权限判断。
     */
    private void doLogin(SysUser user) {
        long timeout = SaManager.getConfig().getTimeout();
        StpUtil.login(user.getId(), new SaLoginModel().setTimeout(timeout));
        StpUtil.getSession().set("role", user.getRole());
    }

    /**
     * 生成 refresh-token 并写入 Redis。
     *
     * <p>两个 key：
     * <ul>
     *     <li>auth:refresh:{token} -> userId，用于刷新时反查用户；</li>
     *     <li>auth:refresh:user:{userId} -> token 集合，用于退出时批量撤销。</li>
     * </ul>
     */
    private String createRefreshToken(Long userId) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofDays(AuthConstant.REFRESH_TOKEN_TTL_DAYS);

        stringRedisTemplate.opsForValue().set(
                AuthConstant.REFRESH_TOKEN_KEY_PREFIX + refreshToken,
                String.valueOf(userId),
                ttl
        );

        String userKey = AuthConstant.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
        stringRedisTemplate.opsForSet().add(userKey, refreshToken);
        stringRedisTemplate.expire(userKey, ttl);

        return refreshToken;
    }

    /**
     * 撤销用户所有 refresh-token。
     */
    private void deleteRefreshTokens(Long userId) {
        String userKey = AuthConstant.REFRESH_TOKEN_USER_KEY_PREFIX + userId;
        Set<String> tokens = stringRedisTemplate.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            List<String> keys = tokens.stream()
                    .map(token -> AuthConstant.REFRESH_TOKEN_KEY_PREFIX + token)
                    .toList();
            stringRedisTemplate.delete(keys);
        }
        stringRedisTemplate.delete(userKey);
    }

    /**
     * 登录失败计数：第一次失败时设置 15 分钟过期，后续失败累加。
     */
    private void recordLoginFail(String username) {
        String key = AuthConstant.LOGIN_FAIL_KEY_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(AuthConstant.LOGIN_FAIL_TTL_MINUTES));
        }
    }

    /**
     * 检查是否已达到失败上限。
     */
    private void checkLoginFail(String username) {
        String value = stringRedisTemplate.opsForValue().get(AuthConstant.LOGIN_FAIL_KEY_PREFIX + username);
        if (StringUtils.hasText(value) && Integer.parseInt(value) >= AuthConstant.LOGIN_FAIL_LIMIT) {
            throw new BusinessException(429, "登录失败次数过多，请 15 分钟后再试");
        }
    }

    private void clearLoginFail(String username) {
        stringRedisTemplate.delete(AuthConstant.LOGIN_FAIL_KEY_PREFIX + username);
    }

    /**
     * 更新最后登录时间。只构造 id + lastLoginTime，避免覆盖其他字段。
     */
    private void updateLastLoginTime(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
    }

    private LoginVO buildLoginVO(SysUser user, String accessToken, String refreshToken) {
        long expiresIn = SaManager.getConfig().getTimeout();
        if (expiresIn <= 0) {
            expiresIn = 1800;
        }
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(AuthConstant.TOKEN_TYPE)
                .expiresIn(expiresIn)
                .userInfo(toUserInfo(user))
                .build();
    }

    private UserInfoVO toUserInfo(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();
    }
}
