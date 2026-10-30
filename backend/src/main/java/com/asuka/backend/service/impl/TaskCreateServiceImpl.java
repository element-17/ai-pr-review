package com.asuka.backend.service.impl;

import com.asuka.backend.service.TaskCreateService;
import com.asuka.backend.service.IdempotencyService;
import com.asuka.backend.service.PullRequestService;
import com.asuka.backend.service.RateLimitService;
import com.asuka.backend.service.TaskAccessService;
import com.asuka.backend.service.UserSettingService;
import com.asuka.backend.service.GitHubTokenProvider;
import com.asuka.backend.service.ModelRuntimeService;

import com.asuka.backend.common.constant.ReportLanguageConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.PrAppProperties;
import com.asuka.backend.core.agent.ai.ResolvedModel;
import com.asuka.backend.core.analysis.AnalysisConfigParser;
import com.asuka.backend.pojo.dto.TaskCreateDTO;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.common.exception.GitHubApiException;
import com.asuka.backend.core.github.GitHubAuth;
import com.asuka.backend.core.github.GitHubClient;
import com.asuka.backend.core.github.GitHubErrorMapper;
import com.asuka.backend.core.github.GitHubPrUrlParser;
import com.asuka.backend.core.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.asuka.backend.pojo.vo.TaskCreateVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 创建分析任务与重试任务。
 *
 * <p>创建时同步校验 PR 并固定 head SHA；文件拉取和 AI 分析交给异步 worker。
 * 游客与登录用户共用一套逻辑，区别只在 user_id、游客令牌和配置来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCreateServiceImpl implements TaskCreateService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GitHubClient gitHubClient;
    private final GitHubTokenProvider gitHubTokenProvider;
    private final PullRequestService pullRequestService;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final UserSettingService userSettingService;
    private final TaskAccessService taskAccessService;
    private final RateLimitService rateLimitService;
    private final IdempotencyService idempotencyService;
    private final PrAppProperties prAppProperties;
    private final ModelRuntimeService modelRuntimeService;
    private final AnalysisConfigParser analysisConfigParser;

    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO create(TaskCreateDTO dto, String idempotencyKey) {
        Long userId = taskAccessService.requireOptionalLogin();

        String url = dto.getUrl().trim();
        String identity = identity(userId);
        String requestHash = sha256(identity + "|" + url + "|" + nullToEmpty(dto.getReportLanguage()));
        String cached = idempotencyService.lookup("task-create", identity, idempotencyKey, requestHash);
        if (cached != null) {
            return readTaskCreateVO(cached);
        }

        // 幂等命中不消耗游客限额；只有真正的新任务才计数
        if (userId == null) {
            rateLimitService.checkGuestTask();
        }

        GitHubPrUrlParser.PrUrl ref = GitHubPrUrlParser.parse(url);
        GitHubAuth auth = gitHubTokenProvider.resolveForUser(userId);
        GitHubPrResponse response;
        try {
            response = gitHubClient.getPullRequest(ref.owner(), ref.repo(), ref.number(), auth);
            gitHubTokenProvider.markValidated(auth);
        } catch (GitHubApiException ex) {
            gitHubTokenProvider.handleFailure(auth, ex);
            throw GitHubErrorMapper.toBusinessException(ex, auth);
        }
        PullRequest pr = pullRequestService.upsert(ref.repoFullName(), response);

        if (userId != null) {
            long running = analysisTaskMapper.selectCount(new LambdaQueryWrapper<AnalysisTask>()
                    .eq(AnalysisTask::getUserId, userId)
                    .eq(AnalysisTask::getPrId, pr.getId())
                    .eq(AnalysisTask::getHeadSha, pr.getHeadSha())
                    .in(AnalysisTask::getStatus, 1, 2));
            if (running > 0) {
                throw new BusinessException(409, "相同提交版本的任务正在执行");
            }
        }

        String language = resolveLanguage(dto.getReportLanguage(), userId);
        AnalysisConfigVO config = userId != null
                ? userSettingService.buildAnalysisConfig(userId)
                : userSettingService.buildGuestAnalysisConfig();
        ResolvedModel model = userId != null
                ? modelRuntimeService.resolveForUser(userId)
                : modelRuntimeService.resolveForGuest();
        AnalysisConfigVO configSnapshot = copyConfig(config);
        configSnapshot.setModel(modelRuntimeService.toSnapshot(model));

        AnalysisTask task = new AnalysisTask();
        task.setPrId(pr.getId());
        task.setUserId(userId);
        task.setHeadSha(pr.getHeadSha());
        task.setReportLanguage(language);
        task.setStatus(1);
        task.setStage(1);
        task.setProgress(0);
        task.setModelName(model.getModelId());
        task.setModelConfigId(model.getModelConfigId());
        task.setAnalysisConfig(toJson(configSnapshot));
        task.setIsDeleted(0);
        if (userId == null) {
            task.setGuestToken(newGuestToken());
            task.setExpireTime(LocalDateTime.now().plusDays(prAppProperties.getGuestTaskTtlDays()));
        }
        analysisTaskMapper.insert(task);

        TaskCreateVO vo = toTaskCreateVO(task);
        idempotencyService.store("task-create", identity, idempotencyKey, requestHash, toJson(vo));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO retry(Long taskId, String idempotencyKey) {
        AnalysisTask original = taskAccessService.requireAccessibleTask(taskId);
        if (original.getStatus() == null
                || (original.getStatus() != 4 && original.getStatus() != 5 && original.getStatus() != 6)) {
            throw new BusinessException(409, "当前任务状态不允许重试");
        }

        Long userId = original.getUserId();
        String identity = identity(userId);
        String requestHash = sha256("retry|" + taskId + "|" + original.getHeadSha());
        String cached = idempotencyService.lookup("task-retry-" + taskId, identity, idempotencyKey, requestHash);
        if (cached != null) {
            return readTaskCreateVO(cached);
        }

        if (userId == null) {
            rateLimitService.checkGuestTask();
        }

        ResolvedModel model = modelRuntimeService.resolveForRetry(original);
        AnalysisConfigVO configSnapshot = analysisConfigParser.parse(original.getAnalysisConfig(), original.getId());
        if (configSnapshot == null) {
            configSnapshot = new AnalysisConfigVO();
        }
        configSnapshot.setModel(modelRuntimeService.toSnapshot(model));

        AnalysisTask task = new AnalysisTask();
        task.setPrId(original.getPrId());
        task.setUserId(original.getUserId());
        task.setHeadSha(original.getHeadSha());
        task.setReportLanguage(original.getReportLanguage());
        task.setStatus(1);
        task.setStage(1);
        task.setProgress(0);
        task.setModelName(model.getModelId());
        task.setModelConfigId(model.getModelConfigId());
        task.setAnalysisConfig(toJson(configSnapshot));
        task.setRetryOfTaskId(original.getId());
        task.setIsDeleted(0);
        if (original.getUserId() == null) {
            task.setGuestToken(newGuestToken());
            task.setExpireTime(LocalDateTime.now().plusDays(prAppProperties.getGuestTaskTtlDays()));
        }
        analysisTaskMapper.insert(task);

        TaskCreateVO vo = toTaskCreateVO(task);
        idempotencyService.store("task-retry-" + taskId, identity, idempotencyKey, requestHash, toJson(vo));
        return vo;
    }

    private String resolveLanguage(String requested, Long userId) {
        if (StringUtils.hasText(requested)) {
            String normalized = ReportLanguageConstant.normalize(requested);
            if (normalized == null) {
                throw new BusinessException(400, "报告语言只能是 zh-CN 或 en-US");
            }
            return normalized;
        }
        if (userId != null) {
            return userSettingService.getSettings(userId).getLanguage();
        }
        return ReportLanguageConstant.ZH_CN;
    }

    private String identity(Long userId) {
        return userId != null ? "user:" + userId : "guest:" + rateLimitService.clientIp();
    }

    private TaskCreateVO toTaskCreateVO(AnalysisTask task) {
        TaskCreateVO vo = new TaskCreateVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setStage(task.getStage());
        vo.setProgress(task.getProgress());
        vo.setReportLanguage(task.getReportLanguage());
        vo.setIsGuest(task.getUserId() == null);
        vo.setCreateTime(task.getCreateTime());
        vo.setRetryOfTaskId(task.getRetryOfTaskId());
        if (task.getUserId() == null) {
            vo.setGuestToken(task.getGuestToken());
            vo.setExpireTime(task.getExpireTime());
            if (StringUtils.hasText(task.getGuestToken())) {
                String base = prAppProperties.getFrontendBaseUrl();
                if (base.endsWith("/")) {
                    base = base.substring(0, base.length() - 1);
                }
                vo.setShareUrl(base + "/tasks/" + task.getId() + "?guestToken=" + task.getGuestToken());
            }
        }
        return vo;
    }

    private String newGuestToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(500, "任务配置序列化失败");
        }
    }

    /**
     * 配置快照对象可能来自缓存，复制一份再写入模型快照，避免污染缓存。
     */
    private AnalysisConfigVO copyConfig(AnalysisConfigVO source) {
        AnalysisConfigVO copy = new AnalysisConfigVO();
        if (source != null) {
            BeanUtils.copyProperties(source, copy);
        }
        copy.setModel(null);
        return copy;
    }

    private TaskCreateVO readTaskCreateVO(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, TaskCreateVO.class);
        } catch (Exception ex) {
            throw new BusinessException(500, "幂等响应解析失败");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
