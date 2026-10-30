package com.asuka.backend.service.impl;

import com.asuka.backend.common.cache.CacheNames;
import com.asuka.backend.common.cache.LogicalCacheService;
import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.constant.TaskStatusConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.common.util.GitHubUrlUtil;
import com.asuka.backend.pojo.dto.TaskFileQueryDTO;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.mapper.PullRequestMapper;
import com.asuka.backend.mapper.ReviewIssueMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.service.TaskAccessService;
import com.asuka.backend.service.TaskFileService;
import com.asuka.backend.common.util.PatchDiffParser;
import com.asuka.backend.pojo.vo.DiffRowVO;
import com.asuka.backend.pojo.vo.IssueBriefVO;
import com.asuka.backend.pojo.vo.TaskFileDetailVO;
import com.asuka.backend.pojo.vo.TaskFileDiffVO;
import com.asuka.backend.pojo.vo.TaskFileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务文件查询服务实现。
 *
 * <p>列表查询显式排除 patch 大字段；只有在文件详情接口才读取 patch，
 * 避免任务文件较多时一次传输几 MB 文本。
 */
@Service
@RequiredArgsConstructor
public class TaskFileServiceImpl implements TaskFileService {

    private final TaskFileMapper taskFileMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final PullRequestMapper pullRequestMapper;
    private final TaskAccessService taskAccessService;
    private final LogicalCacheService logicalCacheService;

    @Override
    public PageResult<TaskFileVO> page(Long taskId, TaskFileQueryDTO query) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        if (!TaskStatusConstant.isFinished(task.getStatus())) {
            return buildPage(taskId, query);
        }
        String key = taskId + ":" + queryKey(query);
        return logicalCacheService.get(CacheNames.TASK_FILES, key,
                CacheNames.TASK_FILES_LOGICAL_TTL_SECONDS, () -> buildPage(taskId, query));
    }

    private PageResult<TaskFileVO> buildPage(Long taskId, TaskFileQueryDTO query) {
        LambdaQueryWrapper<TaskFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskFile::getTaskId, taskId);
        wrapper.eq(query.getStatus() != null, TaskFile::getStatus, query.getStatus());
        wrapper.eq(query.getChangeType() != null, TaskFile::getChangeType, query.getChangeType());
        if (Boolean.TRUE.equals(query.getPriorityOnly())) {
            wrapper.isNotNull(TaskFile::getPriorityReason)
                    .ne(TaskFile::getPriorityReason, "");
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(TaskFile::getFilePath, query.getKeyword().trim());
        }

        // 明确指定返回列，排除 patch
        wrapper.select(TaskFile::getId, TaskFile::getTaskId, TaskFile::getFilePath, TaskFile::getOldPath,
                TaskFile::getChangeType, TaskFile::getAddLines, TaskFile::getDeleteLines,
                TaskFile::getLanguage, TaskFile::getIsBinary, TaskFile::getStatus,
                TaskFile::getSkipReason, TaskFile::getPriorityReason, TaskFile::getSummary);

        applySort(wrapper, query.getSort());

        Page<TaskFile> page = new Page<>(query.getPage(), query.getPageSize());
        Page<TaskFile> result = taskFileMapper.selectPage(page, wrapper);
        List<TaskFileVO> records = result.getRecords().stream().map(file -> {
            TaskFileVO vo = new TaskFileVO();
            fillBaseVO(file, vo);
            return vo;
        }).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public TaskFileDetailVO getDetail(Long taskId, Long fileId) {
        taskAccessService.requireAccessibleTask(taskId);
        return buildFileDetail(taskId, fileId);
    }

    private TaskFileDetailVO buildFileDetail(Long taskId, Long fileId) {
        AnalysisTask task = taskAccessService.requireTask(taskId);
        TaskFile file = taskFileMapper.selectById(fileId);
        if (file == null || !taskId.equals(file.getTaskId())) {
            throw new BusinessException(404, "文件不存在或不属于该任务");
        }

        PullRequest pr = pullRequestMapper.selectById(task.getPrId());
        TaskFileDetailVO vo = new TaskFileDetailVO();
        fillBaseVO(file, vo);
        vo.setPatch(file.getPatch());
        vo.setGithubUrl(buildGithubUrl(task, pr, file));
        vo.setIssues(reviewIssueMapper.selectBriefByFile(taskId, fileId));
        return vo;
    }

    @Override
    public TaskFileDiffVO getDiff(Long taskId, Long fileId) {
        AnalysisTask task = taskAccessService.requireAccessibleTask(taskId);
        if (!TaskStatusConstant.isFinished(task.getStatus())) {
            return buildDiff(taskId, fileId);
        }
        return logicalCacheService.get(CacheNames.TASK_DIFF, taskId + ":" + fileId,
                CacheNames.TASK_DIFF_LOGICAL_TTL_SECONDS, () -> buildDiff(taskId, fileId));
    }

    private TaskFileDiffVO buildDiff(Long taskId, Long fileId) {
        TaskFileDetailVO detail = buildFileDetail(taskId, fileId);
        TaskFileDiffVO vo = new TaskFileDiffVO();
        BeanUtils.copyProperties(detail, vo);

        PatchDiffParser.Parsed parsed = PatchDiffParser.parse(detail.getPatch());
        List<IssueBriefVO> issues = detail.getIssues() == null ? List.of() : detail.getIssues();
        List<DiffRowVO> rows = new ArrayList<>(parsed.rows().size());
        for (PatchDiffParser.Row row : parsed.rows()) {
            DiffRowVO rowVO = new DiffRowVO();
            rowVO.setType(row.type());
            rowVO.setOldLine(row.oldLine());
            rowVO.setNewLine(row.newLine());
            rowVO.setContent(row.content());
            List<Long> issueIds = new ArrayList<>();
            Integer highestSeverity = null;
            if (row.newLine() != null) {
                for (IssueBriefVO issue : issues) {
                    if (issue.getStartLine() == null) {
                        continue;
                    }
                    int start = issue.getStartLine();
                    int end = issue.getEndLine() == null ? start : issue.getEndLine();
                    if (row.newLine() >= start && row.newLine() <= end) {
                        issueIds.add(issue.getIssueId());
                        highestSeverity = highest(highestSeverity, issue.getSeverity());
                    }
                }
            }
            rowVO.setIssueIds(issueIds);
            rowVO.setHighestSeverity(highestSeverity);
            rows.add(rowVO);
        }

        vo.setPatchAvailable(StringUtils.hasText(detail.getPatch()));
        vo.setRows(rows);
        vo.setAddedLines(parsed.addedLines());
        vo.setHunkLines(parsed.hunkLines());
        return vo;
    }

    private Integer highest(Integer current, Integer next) {
        if (next == null) {
            return current;
        }
        return current == null ? next : Math.max(current, next);
    }

    /**
     * 删除文件没有 blob 页面，跳转到 PR 的 files 页面；其余文件定位到任务固定提交。
     */
    private String buildGithubUrl(AnalysisTask task, PullRequest pr, TaskFile file) {
        if (pr == null) {
            return null;
        }
        if (file.getChangeType() != null && file.getChangeType() == TaskFileConstant.CHANGE_DELETE) {
            return GitHubUrlUtil.pullFilesUrl(pr.getRepoFullName(), pr.getPrNumber());
        }
        return GitHubUrlUtil.blobUrl(pr.getRepoFullName(), task.getHeadSha(), file.getFilePath(), null, null);
    }

    /**
     * 文件列表缓存 key：拼接查询条件，避免把 DTO 对象地址带入 key。
     */
    private String queryKey(TaskFileQueryDTO query) {
        String keyword = StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : "";
        return "page=" + query.getPage()
                + "&pageSize=" + query.getPageSize()
                + "&status=" + query.getStatus()
                + "&changeType=" + query.getChangeType()
                + "&priorityOnly=" + Boolean.TRUE.equals(query.getPriorityOnly())
                + "&keyword=" + keyword
                + "&sort=" + query.getSort();
    }

    private void applySort(LambdaQueryWrapper<TaskFile> wrapper, String sort) {
        if ("addLines_desc".equals(sort)) {
            wrapper.orderByDesc(TaskFile::getAddLines).orderByAsc(TaskFile::getFilePath);
        } else if ("deleteLines_desc".equals(sort)) {
            wrapper.orderByDesc(TaskFile::getDeleteLines).orderByAsc(TaskFile::getFilePath);
        } else {
            wrapper.orderByAsc(TaskFile::getFilePath).orderByAsc(TaskFile::getId);
        }
    }

    /**
     * 实体到 VO 的公共字段转换。
     * is_binary 在数据库是 TINYINT，在接口中返回 boolean。
     */
    private void fillBaseVO(TaskFile file, TaskFileVO vo) {
        vo.setFileId(file.getId());
        vo.setFilePath(file.getFilePath());
        vo.setOldPath(file.getOldPath());
        vo.setChangeType(file.getChangeType());
        vo.setAddLines(file.getAddLines());
        vo.setDeleteLines(file.getDeleteLines());
        vo.setLanguage(file.getLanguage());
        vo.setIsBinary(file.getIsBinary() != null && file.getIsBinary() == 1);
        vo.setStatus(file.getStatus());
        vo.setSkipReason(file.getSkipReason());
        vo.setPriorityReason(file.getPriorityReason());
        vo.setSummary(file.getSummary());
    }
}
