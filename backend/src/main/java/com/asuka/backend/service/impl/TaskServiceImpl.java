package com.asuka.backend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.asuka.backend.common.cache.CacheNames;
import com.asuka.backend.common.cache.LogicalCacheService;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.pojo.dto.TaskListRow;
import com.asuka.backend.pojo.dto.TaskQueryDTO;
import com.asuka.backend.pojo.entity.AnalysisReport;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.core.analysis.AnalysisConfigParser;
import com.asuka.backend.service.PullRequestRefreshService;
import com.asuka.backend.service.TaskAccessService;
import com.asuka.backend.service.TaskService;
import com.asuka.backend.service.TaskStatsService;
import com.asuka.backend.pojo.vo.PrBriefVO;
import com.asuka.backend.pojo.vo.PrVO;
import com.asuka.backend.pojo.vo.TaskDetailVO;
import com.asuka.backend.pojo.vo.TaskListItemVO;
import com.asuka.backend.pojo.vo.TaskProgressVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 分析任务查询服务实现。
 *
 * <p>任务读取统一经过 TaskAccessService，登录用户和游客链接都按同一套规则校验。
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final Pattern REPO_PATTERN =
            Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisReportMapper analysisReportMapper;
    private final TaskFileMapper taskFileMapper;
    private final TaskAccessService taskAccessService;
    private final TaskStatsService taskStatsService;
    private final AnalysisConfigParser analysisConfigParser;
    private final PullRequestRefreshService pullRequestRefreshService;
    private final LogicalCacheService logicalCacheService;

    @Override
    public PageResult<TaskListItemVO> pageMyTasks(TaskQueryDTO query) {
        // 历史列表只对登录用户开放；服务层再取一次登录 ID，避免依赖前端传参
        Long userId = StpUtil.getLoginIdAsLong();

        String repo = trimToNull(query.getRepo());
        if (repo != null) {
            if (!REPO_PATTERN.matcher(repo).matches()) {
                throw new BusinessException(400, "仓库全名格式应为 owner/repo");
            }
            query.setRepo(repo);
        }
        if (query.getPrNumber() != null && repo == null) {
            throw new BusinessException(400, "按 PR 编号筛选时必须同时指定仓库");
        }
        if (query.getStartDate() != null && query.getEndDate() != null
                && query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "开始日期不能晚于结束日期");
        }
        if (query.getSort() == null) {
            query.setSort("createTime_desc");
        }

        Page<TaskListRow> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<TaskListRow> result = analysisTaskMapper.selectTaskPage(page, userId, query);
        List<TaskListItemVO> records = result.getRecords().stream().map(this::toListItem).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public TaskDetailVO getDetail(Long taskId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        TaskDetailVO vo = TaskStatusConstant.isFinished(task.getStatus())
                ? logicalCacheService.get(CacheNames.TASK_DETAIL, String.valueOf(taskId),
                        CacheNames.TASK_DETAIL_LOGICAL_TTL_SECONDS, () -> buildDetail(taskId))
                : buildDetail(taskId);
        // canDelete 依赖当前请求的登录身份，不能放进任务级缓存，缓存读取后再按请求计算
        Long currentUserId = taskAccessService.currentUserIdOrNull();
        vo.setCanDelete(currentUserId != null && currentUserId.equals(task.getUserId())
                && TaskStatusConstant.isFinished(task.getStatus()));
        return vo;
    }

    private TaskDetailVO buildDetail(Long taskId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        // 详情页 best-effort 刷新 PR 当前 head_sha，用于 F04 过期判断；失败时使用缓存值
        PullRequest pr = pullRequestRefreshService.refreshIfStale(task.getPrId());

        TaskDetailVO vo = new TaskDetailVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setStage(task.getStage());
        vo.setProgress(task.getProgress());
        vo.setHeadSha(task.getHeadSha());
        vo.setReportLanguage(task.getReportLanguage());
        vo.setModelName(task.getModelName());
        vo.setAnalysisConfig(analysisConfigParser.parse(task.getAnalysisConfig(), task.getId()));
        vo.setErrorCode(task.getErrorCode());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setIsGuest(task.getUserId() == null);
        vo.setIsExpired(isExpired(task, pr));
        vo.setCanCancel(TaskStatusConstant.isRunning(task.getStatus()));
        vo.setCanRetry(task.getStatus() != null
                && (task.getStatus() == TaskStatusConstant.PARTIAL
                || task.getStatus() == TaskStatusConstant.FAILED
                || task.getStatus() == TaskStatusConstant.CANCELED));

        vo.setHasReport(analysisReportMapper.selectCount(
                new LambdaQueryWrapper<AnalysisReport>().eq(AnalysisReport::getTaskId, taskId)) > 0);

        vo.setCreateTime(task.getCreateTime());
        vo.setStartTime(task.getStartTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setPr(toPrVO(pr));
        vo.setStats(taskStatsService.buildTaskStats(taskId));
        vo.setPriorityFiles(taskFileMapper.selectPriorityFiles(taskId, 20));
        return vo;
    }

    @Override
    public TaskProgressVO getProgress(Long taskId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        TaskProgressVO vo = new TaskProgressVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setStage(task.getStage());
        vo.setProgress(task.getProgress());
        vo.setStartTime(task.getStartTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setErrorCode(task.getErrorCode());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private TaskListItemVO toListItem(TaskListRow row) {
        TaskListItemVO vo = new TaskListItemVO();
        vo.setTaskId(row.getTaskId());

        PrBriefVO pr = new PrBriefVO();
        pr.setRepoFullName(row.getRepoFullName());
        pr.setPrNumber(row.getPrNumber());
        pr.setTitle(row.getPrTitle());
        vo.setPr(pr);

        vo.setHeadSha(row.getHeadSha());
        vo.setStatus(row.getStatus());
        vo.setStage(row.getStage());
        vo.setProgress(row.getProgress());
        vo.setReportLanguage(row.getReportLanguage());
        vo.setIsExpired(Boolean.TRUE.equals(row.getIsExpired()));
        vo.setCreateTime(row.getCreateTime());
        vo.setFinishTime(row.getFinishTime());
        return vo;
    }

    private PrVO toPrVO(PullRequest pr) {
        if (pr == null) {
            return null;
        }
        PrVO vo = new PrVO();
        vo.setRepoFullName(pr.getRepoFullName());
        vo.setPrNumber(pr.getPrNumber());
        vo.setTitle(pr.getTitle());
        vo.setAuthor(pr.getAuthor());
        vo.setState(pr.getState());
        vo.setBaseBranch(pr.getBaseBranch());
        vo.setHeadBranch(pr.getHeadBranch());
        vo.setHeadSha(pr.getHeadSha());
        vo.setAddLines(pr.getAddLines());
        vo.setDeleteLines(pr.getDeleteLines());
        vo.setFileCount(pr.getFileCount());
        return vo;
    }

    /**
     * 过期判断：任务固定 head_sha 与 PR 当前 head_sha 不一致即为过期。
     * 旧报告仍可查看，但不能标记为最新版本。
     */
    private boolean isExpired(AnalysisTask task, PullRequest pr) {
        if (pr == null || !StringUtils.hasText(task.getHeadSha()) || !StringUtils.hasText(pr.getHeadSha())) {
            return false;
        }
        return !task.getHeadSha().equalsIgnoreCase(pr.getHeadSha());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
