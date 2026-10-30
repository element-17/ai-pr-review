package com.asuka.backend.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.asuka.backend.common.constant.AuthConstant;
import com.asuka.backend.common.constant.StatusConstant;
import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.pojo.dto.UserCreateDTO;
import com.asuka.backend.pojo.dto.UserQueryDTO;
import com.asuka.backend.pojo.dto.UserUpdateDTO;
import com.asuka.backend.pojo.entity.User;
import com.asuka.backend.mapper.UserMapper;
import com.asuka.backend.service.UserService;
import com.asuka.backend.pojo.vo.RoleCountVO;
import com.asuka.backend.pojo.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 用户服务实现。
 *
 * <p>单表操作全部使用 MyBatis-Plus：
 * <ul>
 *     <li>lambdaQuery / lambdaUpdate：条件构造，避免手写 SQL</li>
 *     <li>page：分页插件</li>
 *     <li>removeById：逻辑删除</li>
 *     <li>updateById：自动填充 updateTime/updateUser</li>
 * </ul>
 * 复杂模糊搜索和聚合统计走 Mapper XML。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 常规分页：使用 MP 条件构造器 + 分页插件。
     */
    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getPage(), query.getPageSize());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 关键字只做 OR 组合，外层由 MP 自动加上 deleted = 0
        wrapper.and(StringUtils.hasText(query.getKeyword()), w -> w
                .like(User::getUsername, query.getKeyword())
                .or()
                .like(User::getNickname, query.getKeyword())
                .or()
                .like(User::getEmail, query.getKeyword()));
        wrapper.eq(query.getStatus() != null, User::getStatus, query.getStatus());
        wrapper.eq(StringUtils.hasText(query.getRole()), User::getRole, query.getRole());
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        Page<User> result = this.page(page, wrapper);
        List<UserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    /**
     * 复杂分页：SQL 写在 UserMapper.xml，分页仍由 MP 插件完成。
     * 适合关键字同时匹配多个字段、后续需要加索引提示或复杂排序的场景。
     */
    @Override
    public PageResult<UserVO> nativePage(UserQueryDTO query) {
        Page<User> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<User> result = baseMapper.selectUserPage(page, query);
        List<UserVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public UserVO getDetail(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public Long createUser(UserCreateDTO dto) {
        // 先做一次友好提示；数据库唯一索引仍是最终兜底
        long count = lambdaQuery().eq(User::getUsername, dto.getUsername()).count();
        if (count > 0) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        BeanUtils.copyProperties(dto, user);
        // 密码必须加密存储，禁止明文落库
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setPasswordSet(1);
        user.setRole(StringUtils.hasText(dto.getRole()) ? dto.getRole() : AuthConstant.ROLE_USER);
        user.setStatus(StatusConstant.ENABLED);
        if (!StringUtils.hasText(user.getNickname())) {
            user.setNickname(user.getUsername());
        }

        // save 由 MP 完成插入，并触发 createTime/createUser 自动填充
        save(user);
        return user.getId();
    }

    @Override
    public Long createGitHubUser(String githubLogin, String nickname, String avatar, String email) {
        String normalizedEmail = StringUtils.hasText(email) && email.length() <= 100 ? email.trim() : null;
        if (normalizedEmail != null) {
            long emailCount = lambdaQuery().eq(User::getEmail, normalizedEmail).count();
            if (emailCount > 0) {
                throw new BusinessException(409, "该 GitHub 邮箱已被本地账号使用，请先登录该账号后绑定 GitHub");
            }
        }

        User user = new User();
        user.setUsername(generateGitHubUsername(githubLogin));
        // GitHub 创建的用户没有可用密码，随机值只用于满足 NOT NULL，不允许密码登录
        user.setPassword(BCrypt.hashpw(UUID.randomUUID() + UUID.randomUUID().toString(), BCrypt.gensalt()));
        user.setPasswordSet(0);
        user.setNickname(StringUtils.hasText(nickname) ? nickname : githubLogin);
        user.setAvatar(StringUtils.hasText(avatar) && avatar.length() <= 255 ? avatar : null);
        user.setEmail(normalizedEmail);
        user.setRole(AuthConstant.ROLE_USER);
        user.setStatus(StatusConstant.ENABLED);
        save(user);
        return user.getId();
    }

    /**
     * 生成 gh_ 前缀的本地登录名，冲突时追加数字后缀。
     */
    private String generateGitHubUsername(String githubLogin) {
        String sanitized = githubLogin == null ? "" : githubLogin.replaceAll("[^A-Za-z0-9_]", "_");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = "user";
        }
        String base = "gh_" + sanitized;
        if (base.length() > 44) {
            base = base.substring(0, 44);
        }
        String candidate = base;
        int suffix = 2;
        while (lambdaQuery().eq(User::getUsername, candidate).count() > 0) {
            String tail = "_" + suffix;
            candidate = (base.length() + tail.length() > 50
                    ? base.substring(0, 50 - tail.length())
                    : base) + tail;
            suffix++;
        }
        return candidate;
    }

    @Override
    public void updateUser(Long id, UserUpdateDTO dto) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 只更新传入的字段，未传字段保持原值
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getRole())) {
            user.setRole(dto.getRole());
            // 角色变更后旧会话仍保留原角色，建议让该用户重新登录
        }

        updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        Long currentUserId = UserContext.getUserId();
        if (id.equals(currentUserId)) {
            throw new BusinessException(400, "不能删除当前登录用户");
        }

        // removeById 执行逻辑删除：UPDATE user SET deleted = 1 WHERE id = ? AND deleted = 0
        boolean removed = removeById(id);
        if (!removed) {
            throw new BusinessException(404, "用户不存在或已删除");
        }
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        User update = new User();
        update.setId(id);
        update.setStatus(status);

        // 只更新 status，其余字段为 null 不会进入 SQL
        boolean updated = updateById(update);
        if (!updated) {
            throw new BusinessException(404, "用户不存在");
        }
    }

    @Override
    public List<RoleCountVO> roleStats() {
        return baseMapper.countByRole();
    }

    /**
     * 实体转 VO，避免把 password、deleted 等内部字段返回给前端。
     */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
