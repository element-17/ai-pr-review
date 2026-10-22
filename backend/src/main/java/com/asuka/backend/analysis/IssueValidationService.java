package com.asuka.backend.analysis;

import com.asuka.backend.constant.ReviewIssueConstant;
import com.asuka.backend.utils.PatchLineIndex;
import com.asuka.backend.dto.GeneratedIssueDTO;
import com.asuka.backend.entity.TaskFile;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.service.ReviewIssueService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 产出问题的校验和入库。
 *
 * <p>校验文件归属、字段范围、有效变更行、证据和重复；无法定位的问题降置信度或降级为报告级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueValidationService {

    private final TaskFileMapper taskFileMapper;
    private final ReviewIssueService reviewIssueService;

    public IssueValidationResult save(Long taskId, List<IssueDraft> issues) {
        if (issues == null || issues.isEmpty()) {
            return new IssueValidationResult(0, 0, 0);
        }

        Map<String, TaskFile> filesByPath = new HashMap<>();
        for (TaskFile file : taskFileMapper.selectList(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, taskId))) {
            filesByPath.put(file.getFilePath(), file);
        }

        List<GeneratedIssueDTO> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int dropped = 0;
        int degraded = 0;

        for (IssueDraft issue : issues) {
            if (issue == null) {
                dropped++;
                continue;
            }
            String title = trimToNull(issue.getTitle());
            if (title == null || title.length() > 300
                    || !ReviewIssueConstant.isValidCategory(issue.getCategory())
                    || !ReviewIssueConstant.isValidSeverity(issue.getSeverity())
                    || issue.getConfidence() == null
                    || issue.getConfidence() < 0 || issue.getConfidence() > 100
                    || !StringUtils.hasText(issue.getTriggerCondition())
                    || !StringUtils.hasText(issue.getImpact())
                    || !StringUtils.hasText(issue.getSuggestion())) {
                dropped++;
                continue;
            }

            Long fileId = null;
            Integer startLine = null;
            Integer endLine = null;
            int confidence = issue.getConfidence();

            if (StringUtils.hasText(issue.getFilePath())) {
                TaskFile file = filesByPath.get(issue.getFilePath().trim());
                if (file == null) {
                    dropped++;
                    continue;
                }
                if (issue.getStartLine() == null || issue.getStartLine() < 1) {
                    dropped++;
                    continue;
                }
                startLine = issue.getStartLine();
                endLine = issue.getEndLine() == null ? startLine : issue.getEndLine();
                if (endLine < startLine) {
                    dropped++;
                    continue;
                }

                PatchLineIndex index = PatchLineIndex.parse(file.getPatch());
                if (index.getAddedLines().isEmpty()) {
                    // 删除文件或纯删除变更没有新文件侧行号，降级为报告级
                    fileId = null;
                    startLine = null;
                    endLine = null;
                    degraded++;
                } else {
                    fileId = file.getId();
                    if (!index.overlapsAdded(startLine, endLine)) {
                        confidence = Math.min(confidence, 50);
                        degraded++;
                        if (!index.overlapsHunk(startLine, endLine)) {
                            confidence = Math.min(confidence, 40);
                        }
                    }
                }
            }

            String evidence = trimToNull(issue.getEvidence());
            if (evidence == null) {
                confidence = Math.min(confidence, ReviewIssueConstant.LOW_CONFIDENCE_THRESHOLD);
                degraded++;
            }

            String key = (fileId == null ? "report" : fileId) + "|"
                    + issue.getCategory() + "|" + normalizeTitle(title);
            if (!seen.add(key)) {
                dropped++;
                continue;
            }

            GeneratedIssueDTO dto = new GeneratedIssueDTO();
            dto.setFileId(fileId);
            dto.setTitle(title);
            dto.setCategory(issue.getCategory());
            dto.setSeverity(issue.getSeverity());
            dto.setConfidence(confidence);
            dto.setStartLine(startLine);
            dto.setEndLine(endLine);
            dto.setTriggerCondition(issue.getTriggerCondition().trim());
            dto.setImpact(issue.getImpact().trim());
            dto.setEvidence(evidence);
            dto.setSuggestion(issue.getSuggestion().trim());
            valid.add(dto);
        }

        int inserted = reviewIssueService.saveGeneratedIssues(taskId, valid);
        if (inserted < valid.size()) {
            dropped += valid.size() - inserted;
        }
        log.info("任务 {} AI 问题校验：valid={}, inserted={}, dropped={}, degraded={}",
                taskId, valid.size(), inserted, dropped, degraded);
        return new IssueValidationResult(inserted, dropped, degraded);
    }

    private String normalizeTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
