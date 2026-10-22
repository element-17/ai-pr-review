package com.asuka.backend.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.exception.BusinessException;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 任务访问权限校验。
 *
 * <p>任务访问规则：
 * <ul>
 *     <li>登录用户只能访问自己的任务；</li>
 *     <li>游客任务凭 X-Guest-Token 或 guestToken 查询参数访问，并校验过期时间；</li>
 *     <li>登录用户拿到有效的游客链接时，也允许按游客身份访问他人创建的游客任务。</li>
 * </ul>
 * 所有任务子资源接口都必须先经过这里，避免只按 fileId / issueId 查询造成横向越权。
 */
@Service
@RequiredArgsConstructor
public class TaskAccessService {

    private final AnalysisTaskMapper analysisTaskMapper;

    /**
     * 查询任务，不校验访问权限。仅用于内部统计、导入等已经明确任务归属的场景。
     */
    public AnalysisTask requireTask(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(400, "任务 ID 不能为空");
        }
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    /**
     * 查询并校验当前请求是否有权访问该任务。
     */
    public AnalysisTask requireAccessibleTask(Long taskId) {
        AnalysisTask task = requireTask(taskId);
        Long loginId = currentUserIdOrNull();

        // 任务归属优先：登录用户访问自己的任务
        if (loginId != null && loginId.equals(task.getUserId())) {
            return task;
        }

        // 游客令牌：只对创建它的那一条任务有效
        String guestToken = resolveGuestToken();
        if (StringUtils.hasText(guestToken) && guestToken.equals(task.getGuestToken())) {
            if (task.getExpireTime() != null && task.getExpireTime().isBefore(LocalDateTime.now())) {
                throw new BusinessException(403, "游客结果已过期");
            }
            return task;
        }

        if (loginId != null) {
            throw new BusinessException(403, "无权访问该任务");
        }
        if (!StringUtils.hasText(guestToken)) {
            throw new BusinessException(401, "请先登录或提供游客令牌");
        }
        throw new BusinessException(403, "游客令牌无效或已过期");
    }

    /**
     * 当前登录用户 ID；游客请求返回 null。
     */
    public Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (NotLoginException ex) {
            // 任务接口允许携带无效 Authorization 的游客访问，这里按未登录处理，
            // 后续仍会校验游客令牌，不会放宽权限。
            return null;
        }
    }

    /**
     * 要求登录用户是任务所有者；游客任务和他人任务都拒绝。
     */
    public AnalysisTask requireOwnedTask(Long taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        AnalysisTask task = requireTask(taskId);
        if (task.getUserId() == null || !userId.equals(task.getUserId())) {
            throw new BusinessException(403, "无权操作该任务");
        }
        return task;
    }

    /**
     * 可选登录：预览和创建任务允许游客，但带了无效 Authorization 时必须返回 401。
     *
     * @return 登录用户 ID；无 Authorization 时返回 null
     */
    public Long requireOptionalLogin() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader("Authorization");
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (NotLoginException ex) {
            if (StringUtils.hasText(authorization)) {
                throw new BusinessException(401, "登录状态已失效，请重新登录");
            }
            return null;
        }
        if (StringUtils.hasText(authorization)) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        return null;
    }

    /**
     * 从请求头或查询参数读取游客令牌。
     * 查询参数只用于导出这类直接打开链接的场景。
     */
    private String resolveGuestToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader("X-Guest-Token");
        if (!StringUtils.hasText(token)) {
            token = request.getParameter("guestToken");
        }
        return StringUtils.hasText(token) ? token.trim() : null;
    }
}
