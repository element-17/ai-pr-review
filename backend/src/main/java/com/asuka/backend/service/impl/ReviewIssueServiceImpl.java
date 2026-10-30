package com.asuka.backend.service.impl;

import com.asuka.backend.common.cache.CacheNames;
import com.asuka.backend.common.cache.LogicalCacheService;
import com.asuka.backend.common.constant.ReviewIssueConstant;
import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.util.GitHubUrlUtil;
import com.asuka.backend.pojo.dto.GeneratedIssueDTO;
import com.asuka.backend.pojo.dto.IssueQueryParam;
import com.asuka.backend.pojo.dto.ReviewIssueQueryDTO;
import com.asuka.backend.pojo.dto.ReviewIssueUpdateDTO;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.ReviewIssue;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.mapper.PullRequestMapper;
import com.asuka.backend.mapper.ReviewIssueMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.service.ReviewIssueService;
import com.asuka.backend.service.TaskAccessService;
import com.asuka.backend.service.TaskStatsService;
import com.asuka.backend.pojo.vo.IssueDetailVO;
import com.asuka.backend.pojo.vo.IssueFileVO;
import com.asuka.backend.pojo.vo.IssueListVO;
import com.asuka.backend.pojo.vo.IssueVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 评审问题服务实现。
 *
 * <p>所有查询都同时带 task_id 和 issue_id 条件，避免只按问题 ID 查询造成横向越权。
 * 反馈操作记录最后操作人和时间，满足 F13 的审计要求。
 */
@Service
@RequiredArgsConstructor
public class ReviewIssueServiceImpl implements ReviewIssueService {

    private final ReviewIssueMapper reviewIssueMapper;
    private final TaskFileMapper taskFileMapper;
    private final PullRequestMapper pullRequestMapper;
    private final TaskAccessService taskAccessService;
    private final TaskStatsService taskStatsService;
    private final LogicalCacheService logicalCacheService;

    @Override
    public IssueListVO page(Long taskId, ReviewIssueQueryDTO query) {
        taskAccessService.requireAccessibleTask(taskId);

        if (query.getFileId() != null) {
            requireFileOfTask(taskId, query.getFileId());
        }

        IssueQueryParam param = new IssueQueryParam();
        param.setSeverities(parseCsv(query.getSeverity(), 1, 4, "严重度"));
        param.setCategories(parseCsv(query.getCategory(), 1, 7, "类别"));
        param.setFeedbackStatuses(parseCsv(query.getFeedbackStatus(), 1, 4, "处理状态"));
        param.setFileId(query.getFileId());
        param.setKeyword(trimToNull(query.getKeyword()));
        param.setSort(StringUtils.hasText(query.getSort()) ? query.getSort() : "severity_desc");

        Page<IssueVO> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<IssueVO> result = reviewIssueMapper.selectIssuePage(page, taskId, param);

        IssueListVO vo = new IssueListVO();
        vo.setTotal(result.getTotal());
        vo.setRecords(result.getRecords());
        // stats 是任务级统计，不受当前筛选条件影响
        vo.setStats(taskStatsService.buildIssueStats(taskId));
        return vo;
    }

    @Override
    public IssueDetailVO getDetail(Long taskId, Long issueId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        ReviewIssue issue = requireIssueOfTask(taskId, issueId);
        return toDetail(task, issue);
    }

    @Override
    public IssueDetailVO update(Long taskId, Long issueId, ReviewIssueUpdateDTO dto) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        ReviewIssue issue = requireIssueOfTask(taskId, issueId);

        if (dto == null || (dto.getFeedbackStatus() == null
                && dto.getFeedbackRemark() == null && dto.getSuggestion() == null)) {
            throw new BusinessException(400, "至少需要提交一个更新字段");
        }
        if (dto.getFeedbackStatus() != null
                && !ReviewIssueConstant.isValidFeedbackStatus(dto.getFeedbackStatus())) {
            throw new BusinessException(400, "处理状态非法");
        }
        if (dto.getSuggestion() != null && !StringUtils.hasText(dto.getSuggestion())) {
            throw new BusinessException(400, "建议不能为空；撤销请使用 undo 接口");
        }

        String remark = dto.getFeedbackRemark() == null ? null : dto.getFeedbackRemark().trim();
        if (remark != null && remark.length() > 500) {
            throw new BusinessException(400, "处理理由不能超过 500 字");
        }
        // 误报必须写理由，便于后续统计误报原因
        if (dto.getFeedbackStatus() != null
                && dto.getFeedbackStatus() == ReviewIssueConstant.FEEDBACK_FALSE_POSITIVE) {
            String effectiveRemark = remark != null ? trimToNull(remark) : trimToNull(issue.getFeedbackRemark());
            if (effectiveRemark == null) {
                throw new BusinessException(400, "标记误报时必须填写处理理由");
            }
        }

        LambdaUpdateWrapper<ReviewIssue> update = new LambdaUpdateWrapper<>();
        update.eq(ReviewIssue::getId, issueId).eq(ReviewIssue::getTaskId, taskId);
        if (dto.getFeedbackStatus() != null) {
            update.set(ReviewIssue::getFeedbackStatus, dto.getFeedbackStatus());
        }
        if (dto.getFeedbackRemark() != null) {
            update.set(ReviewIssue::getFeedbackRemark, trimToNull(remark));
        }
        if (dto.getSuggestion() != null) {
            update.set(ReviewIssue::getSuggestion, dto.getSuggestion().trim());
        }

        LocalDateTime now = LocalDateTime.now();
        update.set(ReviewIssue::getFeedbackUserId, taskAccessService.currentUserIdOrNull());
        update.set(ReviewIssue::getFeedbackTime, now);
        update.set(ReviewIssue::getUpdateTime, now);
        reviewIssueMapper.update(null, update);
        evictTaskCaches(taskId, issue.getFileId());

        return toDetail(task, requireIssueOfTask(taskId, issueId));
    }

    @Override
    public IssueDetailVO undo(Long taskId, Long issueId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        ReviewIssue issue = requireIssueOfTask(taskId, issueId);

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ReviewIssue> update = new LambdaUpdateWrapper<>();
        update.eq(ReviewIssue::getId, issueId).eq(ReviewIssue::getTaskId, taskId)
                .set(ReviewIssue::getFeedbackStatus, ReviewIssueConstant.FEEDBACK_PENDING)
                .set(ReviewIssue::getFeedbackRemark, null)
                .set(ReviewIssue::getSuggestion, issue.getAiSuggestion())
                .set(ReviewIssue::getFeedbackUserId, taskAccessService.currentUserIdOrNull())
                .set(ReviewIssue::getFeedbackTime, now)
                .set(ReviewIssue::getUpdateTime, now);
        reviewIssueMapper.update(null, update);
        evictTaskCaches(taskId, issue.getFileId());

        return toDetail(task, requireIssueOfTask(taskId, issueId));
    }

    /**
     * 反馈会影响任务统计、报告和 Diff 行上的问题状态，更新后删除对应缓存。
     */
    private void evictTaskCaches(Long taskId, Long fileId) {
        logicalCacheService.evict(CacheNames.TASK_DETAIL, String.valueOf(taskId));
        logicalCacheService.evict(CacheNames.TASK_REPORT, String.valueOf(taskId));
        if (fileId != null) {
            logicalCacheService.evict(CacheNames.TASK_DIFF, taskId + ":" + fileId);
        }
    }

    /**
     * 校验并保存分析结果。
     *
     * <p>校验规则：
     * <ul>
     *     <li>文件必须属于当前任务；</li>
     *     <li>有文件定位时起始行号必填，结束行号不能小于起始行；</li>
     *     <li>类别、严重度、置信度必须在合法范围；</li>
     *     <li>证据不足时置信度降到阈值以下，并保持待确认状态；</li>
     *     <li>同一文件、类别、规范化标题只保留一条。</li>
     * </ul>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveGeneratedIssues(Long taskId, List<GeneratedIssueDTO> issues) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        AnalysisTask task = taskAccessService.requireTask(taskId);

        List<ReviewIssue> existing = reviewIssueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssue>().eq(ReviewIssue::getTaskId, taskId));
        Set<String> seen = new HashSet<>();
        for (ReviewIssue issue : existing) {
            seen.add(dedupeKey(issue.getFileId(), issue.getCategory(), issue.getTitle()));
        }

        int inserted = 0;
        for (GeneratedIssueDTO dto : issues) {
            ReviewIssue issue = validateGeneratedIssue(task, dto);
            if (!seen.add(dedupeKey(issue.getFileId(), issue.getCategory(), issue.getTitle()))) {
                continue;
            }
            reviewIssueMapper.insert(issue);
            inserted++;
        }
        return inserted;
    }

    /**
     * 把 AI 产出转换成实体，并执行所有入库前校验。
     */
    private ReviewIssue validateGeneratedIssue(AnalysisTask task, GeneratedIssueDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "问题数据不能为空");
        }
        String title = trimToNull(dto.getTitle());
        if (title == null || title.length() > 300) {
            throw new BusinessException(400, "问题标题不能为空且不能超过 300 字");
        }
        if (!ReviewIssueConstant.isValidCategory(dto.getCategory())) {
            throw new BusinessException(400, "问题类别非法");
        }
        if (!ReviewIssueConstant.isValidSeverity(dto.getSeverity())) {
            throw new BusinessException(400, "问题严重度非法");
        }
        if (dto.getConfidence() == null || dto.getConfidence() < 0 || dto.getConfidence() > 100) {
            throw new BusinessException(400, "问题置信度必须在 0-100 之间");
        }
        String triggerCondition = trimToNull(dto.getTriggerCondition());
        String impact = trimToNull(dto.getImpact());
        String suggestion = trimToNull(dto.getSuggestion());
        if (triggerCondition == null || impact == null || suggestion == null) {
            throw new BusinessException(400, "触发条件、潜在影响和建议不能为空");
        }

        Long fileId = dto.getFileId();
        Integer startLine = null;
        Integer endLine = null;
        if (fileId != null) {
            requireFileOfTask(task.getId(), fileId);
            if (dto.getStartLine() == null || dto.getStartLine() < 1) {
                throw new BusinessException(400, "文件级问题的起始行号必须大于 0");
            }
            startLine = dto.getStartLine();
            endLine = dto.getEndLine() == null ? startLine : dto.getEndLine();
            if (endLine < startLine) {
                throw new BusinessException(400, "结束行号不能小于起始行号");
            }
        }

        String evidence = trimToNull(dto.getEvidence());
        int confidence = dto.getConfidence();
        if (evidence == null) {
            // 证据不足不拒绝入库，但降低置信度并保持待确认，提示人工优先核查
            confidence = Math.min(confidence, ReviewIssueConstant.LOW_CONFIDENCE_THRESHOLD);
        }

        ReviewIssue issue = new ReviewIssue();
        issue.setTaskId(task.getId());
        issue.setFileId(fileId);
        issue.setTitle(title);
        issue.setCategory(dto.getCategory());
        issue.setSeverity(dto.getSeverity());
        issue.setConfidence(confidence);
        issue.setStartLine(startLine);
        issue.setEndLine(endLine);
        issue.setTriggerCondition(triggerCondition);
        issue.setImpact(impact);
        issue.setEvidence(evidence);
        issue.setAiSuggestion(suggestion);
        issue.setSuggestion(suggestion);
        issue.setFeedbackStatus(ReviewIssueConstant.FEEDBACK_PENDING);
        return issue;
    }

    private ReviewIssue requireIssueOfTask(Long taskId, Long issueId) {
        ReviewIssue issue = reviewIssueMapper.selectOne(new LambdaQueryWrapper<ReviewIssue>()
                .eq(ReviewIssue::getId, issueId)
                .eq(ReviewIssue::getTaskId, taskId));
        if (issue == null) {
            throw new BusinessException(404, "问题不存在或不属于该任务");
        }
        return issue;
    }

    private TaskFile requireFileOfTask(Long taskId, Long fileId) {
        TaskFile file = taskFileMapper.selectById(fileId);
        if (file == null || !taskId.equals(file.getTaskId())) {
            throw new BusinessException(404, "文件不存在或不属于该任务");
        }
        return file;
    }

    /**
     * 实体转详情 VO。
     * GitHub 链接使用任务固定 head_sha；删除文件跳到 PR 的 files 页面。
     */
    private IssueDetailVO toDetail(AnalysisTask task, ReviewIssue issue) {
        TaskFile file = null;
        if (issue.getFileId() != null) {
            file = taskFileMapper.selectById(issue.getFileId());
            if (file != null && !task.getId().equals(file.getTaskId())) {
                file = null;
            }
        }
        PullRequest pr = pullRequestMapper.selectById(task.getPrId());

        IssueDetailVO vo = new IssueDetailVO();
        vo.setIssueId(issue.getId());
        vo.setTitle(issue.getTitle());
        vo.setCategory(issue.getCategory());
        vo.setSeverity(issue.getSeverity());
        vo.setConfidence(issue.getConfidence());
        if (file != null) {
            IssueFileVO fileVO = new IssueFileVO();
            fileVO.setFileId(file.getId());
            fileVO.setFilePath(file.getFilePath());
            fileVO.setChangeType(file.getChangeType());
            vo.setFile(fileVO);
        }
        vo.setStartLine(issue.getStartLine());
        vo.setEndLine(issue.getEndLine());
        vo.setTriggerCondition(issue.getTriggerCondition());
        vo.setImpact(issue.getImpact());
        vo.setEvidence(issue.getEvidence());
        vo.setAiSuggestion(issue.getAiSuggestion());
        vo.setSuggestion(issue.getSuggestion());
        vo.setFeedbackStatus(issue.getFeedbackStatus());
        vo.setFeedbackRemark(issue.getFeedbackRemark());
        vo.setFeedbackUserId(issue.getFeedbackUserId());
        vo.setFeedbackTime(issue.getFeedbackTime());
        vo.setGithubUrl(buildGithubUrl(task, pr, file, issue));
        vo.setCreateTime(issue.getCreateTime());
        vo.setUpdateTime(issue.getUpdateTime());
        return vo;
    }

    private String buildGithubUrl(AnalysisTask task, PullRequest pr, TaskFile file, ReviewIssue issue) {
        if (file == null || pr == null) {
            return null;
        }
        if (file.getChangeType() != null && file.getChangeType() == TaskFileConstant.CHANGE_DELETE) {
            return GitHubUrlUtil.pullFilesUrl(pr.getRepoFullName(), pr.getPrNumber());
        }
        return GitHubUrlUtil.blobUrl(pr.getRepoFullName(), task.getHeadSha(), file.getFilePath(),
                issue.getStartLine(), issue.getEndLine());
    }

    /**
     * 解析逗号分隔的枚举筛选值，去重并校验范围。
     */
    private List<Integer> parseCsv(String raw, int min, int max, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (!StringUtils.hasText(part)) {
                throw new BusinessException(400, fieldName + "筛选值非法");
            }
            try {
                int value = Integer.parseInt(part.trim());
                if (value < min || value > max) {
                    throw new BusinessException(400, fieldName + "筛选值非法");
                }
                values.add(value);
            } catch (NumberFormatException ex) {
                throw new BusinessException(400, fieldName + "筛选值非法");
            }
        }
        return new ArrayList<>(values);
    }

    private String dedupeKey(Long fileId, Integer category, String title) {
        return (fileId == null ? "report" : fileId) + "|" + category + "|" + normalizeTitle(title);
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
