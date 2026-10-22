package com.asuka.backend.service;

import com.asuka.backend.common.constant.TaskFileConstant;
import com.asuka.backend.common.util.BinaryDetector;
import com.asuka.backend.common.util.LanguageDetector;
import com.asuka.backend.entity.AnalysisTask;
import com.asuka.backend.entity.PullRequest;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.integration.github.GitHubClient;
import com.asuka.backend.integration.github.dto.GitHubFileResponse;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 GitHub PR 文件变更转换为任务文件快照。
 *
 * <p>用户设置的 maxFileCount / maxDiffLines 在这里生效；
 * 超限、二进制、无 patch、文件过大都保留文件行，标记为已跳过并写原因。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskFileIngestionService {

    private static final int DEFAULT_MAX_FILES = 200;
    private static final int DEFAULT_MAX_DIFF_LINES = 20000;
    private static final int MAX_PATCH_CHARS = 200_000;
    private static final int MAX_PATH_LENGTH = 512;

    private final GitHubClient gitHubClient;
    private final TaskFileMapper taskFileMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<TaskFile> ingest(AnalysisTask task, PullRequest pr, AnalysisConfigVO config) {
        String[] repo = pr.getRepoFullName().split("/", 2);
        if (repo.length != 2) {
            throw new IllegalArgumentException("仓库全名格式错误");
        }

        List<GitHubFileResponse> files = gitHubClient.listPullRequestFiles(
                repo[0], repo[1], pr.getPrNumber());

        // 重新生成任务文件快照，保证重复执行不产生重复行
        taskFileMapper.delete(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, task.getId()));

        int maxFiles = positiveOrDefault(config == null ? null : config.getMaxFileCount(), DEFAULT_MAX_FILES);
        int maxLines = positiveOrDefault(config == null ? null : config.getMaxDiffLines(), DEFAULT_MAX_DIFF_LINES);

        int included = 0;
        int diffLines = 0;
        int skipped = 0;
        List<TaskFile> saved = new ArrayList<>();

        for (GitHubFileResponse file : files) {
            if (file == null || !StringUtils.hasText(file.getFilename())) {
                continue;
            }
            String path = file.getFilename();
            if (path.length() > MAX_PATH_LENGTH) {
                TaskFile row = baseRow(task, file, null, 0);
                markSkipped(row, "文件路径过长");
                taskFileMapper.insert(row);
                saved.add(row);
                skipped++;
                continue;
            }

            int addLines = defaultZero(file.getAdditions());
            int deleteLines = defaultZero(file.getDeletions());
            int fileLines = addLines + deleteLines;
            boolean binary = BinaryDetector.isBinary(path);
            String patch = file.getPatch();
            boolean tooLarge = patch != null && patch.length() > MAX_PATCH_CHARS;

            TaskFile row = baseRow(task, file, LanguageDetector.detect(path), addLines);
            row.setDeleteLines(deleteLines);
            row.setIsBinary(binary ? 1 : 0);

            if (included >= maxFiles) {
                markSkipped(row, "超过最大文件数");
            } else if (diffLines + fileLines > maxLines) {
                markSkipped(row, "超过最大 Diff 行数");
            } else {
                included++;
                diffLines += fileLines;
                if (binary) {
                    markSkipped(row, "二进制文件，不分析 Diff");
                } else if (!StringUtils.hasText(patch)) {
                    markSkipped(row, "GitHub 未返回 patch");
                } else if (tooLarge) {
                    markSkipped(row, "文件 patch 超过存储上限");
                } else {
                    row.setPatch(patch);
                    row.setStatus(TaskFileConstant.STATUS_PENDING);
                }
            }

            // 超限但 patch 已拿到时仍保留，供页面查看；二进制和过大不保留
            if (StringUtils.hasText(patch) && !binary && !tooLarge && row.getStatus() != TaskFileConstant.STATUS_PENDING) {
                row.setPatch(patch);
            }

            taskFileMapper.insert(row);
            saved.add(row);
            if (row.getStatus() == TaskFileConstant.STATUS_SKIPPED) {
                skipped++;
            }
        }

        log.info("任务 {} 文件快照完成：total={}, included={}, skipped={}, diffLines={}",
                task.getId(), saved.size(), included, skipped, diffLines);
        return saved;
    }

    private TaskFile baseRow(AnalysisTask task, GitHubFileResponse file, String language, int addLines) {
        TaskFile row = new TaskFile();
        row.setTaskId(task.getId());
        row.setFilePath(file.getFilename());
        row.setOldPath(StringUtils.hasText(file.getPreviousFilename()) ? file.getPreviousFilename() : null);
        row.setChangeType(resolveChangeType(file.getStatus()));
        row.setAddLines(addLines);
        row.setDeleteLines(defaultZero(file.getDeletions()));
        row.setLanguage(language);
        row.setIsBinary(0);
        row.setStatus(TaskFileConstant.STATUS_PENDING);
        return row;
    }

    private void markSkipped(TaskFile row, String reason) {
        row.setStatus(TaskFileConstant.STATUS_SKIPPED);
        row.setSkipReason(reason);
        row.setPatch(null);
    }

    private int resolveChangeType(String status) {
        if (status == null) {
            return TaskFileConstant.CHANGE_MODIFY;
        }
        return switch (status) {
            case "added" -> TaskFileConstant.CHANGE_ADD;
            case "removed" -> TaskFileConstant.CHANGE_DELETE;
            case "renamed" -> TaskFileConstant.CHANGE_RENAME;
            case "copied" -> TaskFileConstant.CHANGE_COPY;
            default -> TaskFileConstant.CHANGE_MODIFY;
        };
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}
