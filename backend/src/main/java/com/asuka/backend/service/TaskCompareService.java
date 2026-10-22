package com.asuka.backend.service;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.pojo.dto.IssueExportRow;
import com.asuka.backend.pojo.entity.AnalysisReport;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.mapper.AnalysisReportMapper;
import com.asuka.backend.mapper.ReviewIssueMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.pojo.vo.CompareFieldVO;
import com.asuka.backend.pojo.vo.CompareIssueVO;
import com.asuka.backend.pojo.vo.CompareIssuesVO;
import com.asuka.backend.pojo.vo.CompareMatchingVO;
import com.asuka.backend.pojo.vo.CompareSummaryVO;
import com.asuka.backend.pojo.vo.CompareTaskVO;
import com.asuka.backend.pojo.vo.TaskCompareVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 同一 PR 两个分析版本的对比。
 *
 * <p>问题按文件路径（含重命名前后路径）、类别和规范化标题匹配；
 * 精确匹配失败时，允许同路径+类别+行号重叠的低置信匹配，并提示行号偏移。
 */
@Service
@RequiredArgsConstructor
public class TaskCompareService {

    private static final String REPORT_LEVEL = "<report>";

    private final TaskAccessService taskAccessService;
    private final AnalysisReportMapper analysisReportMapper;
    private final TaskFileMapper taskFileMapper;
    private final ReviewIssueMapper reviewIssueMapper;
    private final TaskStatsService taskStatsService;

    public TaskCompareVO compare(Long baseTaskId, Long targetTaskId) {
        if (Objects.equals(baseTaskId, targetTaskId)) {
            throw new BusinessException(409, "两个版本不能相同");
        }
        AnalysisTask base = taskAccessService.requireOwnedTask(baseTaskId);
        AnalysisTask target = taskAccessService.requireOwnedTask(targetTaskId);
        if (!Objects.equals(base.getPrId(), target.getPrId())) {
            throw new BusinessException(409, "两个版本不属于同一个 PR");
        }
        if (!comparable(base.getStatus()) || !comparable(target.getStatus())) {
            throw new BusinessException(409, "只有已完成或部分完成的任务可以对比");
        }

        AnalysisReport baseReport = findReport(baseTaskId);
        AnalysisReport targetReport = findReport(targetTaskId);
        List<IssueExportRow> baseIssues = reviewIssueMapper.selectForExport(baseTaskId);
        List<IssueExportRow> targetIssues = reviewIssueMapper.selectForExport(targetTaskId);
        if (baseReport == null && targetReport == null && baseIssues.isEmpty() && targetIssues.isEmpty()) {
            throw new BusinessException(409, "两个版本都没有可对比的报告或问题");
        }

        TaskCompareVO vo = new TaskCompareVO();
        vo.setBase(toCompareTask(base));
        vo.setTarget(toCompareTask(target));
        vo.setSummary(buildSummary(baseReport, targetReport));
        vo.setIssues(matchIssues(baseTaskId, targetTaskId, baseIssues, targetIssues));

        CompareMatchingVO matching = new CompareMatchingVO();
        matching.setMethod("filePath+category+normalizedTitle");
        matching.setLineShiftWarning(true);
        matching.setNote("行号变化可能导致同名问题匹配不准确，请人工核对。");
        vo.setMatching(matching);
        return vo;
    }

    private boolean comparable(Integer status) {
        return status != null && (status == 3 || status == 4);
    }

    private AnalysisReport findReport(Long taskId) {
        return analysisReportMapper.selectOne(new LambdaQueryWrapper<AnalysisReport>()
                .eq(AnalysisReport::getTaskId, taskId)
                .last("LIMIT 1"));
    }

    private CompareTaskVO toCompareTask(AnalysisTask task) {
        CompareTaskVO vo = new CompareTaskVO();
        vo.setTaskId(task.getId());
        vo.setHeadSha(task.getHeadSha());
        vo.setStatus(task.getStatus());
        vo.setReportLanguage(task.getReportLanguage());
        vo.setCreateTime(task.getCreateTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setStats(taskStatsService.buildTaskStats(task.getId()));
        return vo;
    }

    private CompareSummaryVO buildSummary(AnalysisReport base, AnalysisReport target) {
        CompareSummaryVO vo = new CompareSummaryVO();
        vo.setPurpose(field(base == null ? null : base.getPurpose(), target == null ? null : target.getPurpose()));
        vo.setChanges(field(base == null ? null : base.getChanges(), target == null ? null : target.getChanges()));
        vo.setImpacts(field(base == null ? null : base.getImpacts(), target == null ? null : target.getImpacts()));
        return vo;
    }

    private CompareFieldVO field(String base, String target) {
        CompareFieldVO vo = new CompareFieldVO();
        vo.setBase(base);
        vo.setTarget(target);
        vo.setChanged(!Objects.equals(base, target));
        return vo;
    }

    private CompareIssuesVO matchIssues(Long baseTaskId, Long targetTaskId,
                                        List<IssueExportRow> baseIssues,
                                        List<IssueExportRow> targetIssues) {
        Map<String, Set<String>> baseAliases = pathAliases(baseTaskId);
        Map<String, Set<String>> targetAliases = pathAliases(targetTaskId);

        Map<String, List<IssueExportRow>> targetIndex = new LinkedHashMap<>();
        for (IssueExportRow issue : targetIssues) {
            for (String alias : aliases(issue.getFilePath(), targetAliases)) {
                targetIndex.computeIfAbsent(key(alias, issue), ignored -> new ArrayList<>()).add(issue);
            }
        }

        Set<Long> usedTargetIds = new HashSet<>();
        List<CompareIssueVO> remaining = new ArrayList<>();
        List<IssueExportRow> unmatchedBase = new ArrayList<>();

        for (IssueExportRow base : baseIssues) {
            IssueExportRow matched = null;
            for (String alias : aliases(base.getFilePath(), baseAliases)) {
                List<IssueExportRow> candidates = targetIndex.getOrDefault(key(alias, base), List.of());
                for (IssueExportRow candidate : candidates) {
                    if (!usedTargetIds.contains(candidate.getIssueId())) {
                        matched = candidate;
                        break;
                    }
                }
                if (matched != null) {
                    break;
                }
            }
            if (matched == null) {
                unmatchedBase.add(base);
            } else {
                usedTargetIds.add(matched.getIssueId());
                remaining.add(toRemaining(base, matched, null));
            }
        }

        List<IssueExportRow> unmatchedTarget = targetIssues.stream()
                .filter(issue -> !usedTargetIds.contains(issue.getIssueId()))
                .toList();

        // 低置信匹配：同类别、路径别名相交、行号重叠，标题不完全一致
        List<IssueExportRow> stillUnmatchedBase = new ArrayList<>();
        for (IssueExportRow base : unmatchedBase) {
            IssueExportRow matched = unmatchedTarget.stream()
                    .filter(target -> !usedTargetIds.contains(target.getIssueId()))
                    .filter(target -> Objects.equals(base.getCategory(), target.getCategory()))
                    .filter(target -> aliasesIntersect(base.getFilePath(), baseAliases,
                            target.getFilePath(), targetAliases))
                    .filter(target -> rangesOverlap(base.getStartLine(), base.getEndLine(),
                            target.getStartLine(), target.getEndLine()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                stillUnmatchedBase.add(base);
            } else {
                usedTargetIds.add(matched.getIssueId());
                remaining.add(toRemaining(base, matched, "low"));
            }
        }

        List<CompareIssueVO> resolved = stillUnmatchedBase.stream()
                .map(this::toResolved)
                .toList();
        List<CompareIssueVO> added = targetIssues.stream()
                .filter(issue -> !usedTargetIds.contains(issue.getIssueId()))
                .map(this::toAdded)
                .toList();

        CompareIssuesVO vo = new CompareIssuesVO();
        vo.setAdded(added);
        vo.setRemaining(remaining);
        vo.setResolved(resolved);
        return vo;
    }

    private Map<String, Set<String>> pathAliases(Long taskId) {
        Map<String, Set<String>> aliases = new HashMap<>();
        for (TaskFile file : taskFileMapper.selectList(new LambdaQueryWrapper<TaskFile>()
                .eq(TaskFile::getTaskId, taskId))) {
            Set<String> values = new HashSet<>();
            values.add(file.getFilePath());
            if (StringUtils.hasText(file.getOldPath())) {
                values.add(file.getOldPath());
            }
            aliases.put(file.getFilePath(), values);
            if (StringUtils.hasText(file.getOldPath())) {
                aliases.put(file.getOldPath(), values);
            }
        }
        return aliases;
    }

    private Set<String> aliases(String path, Map<String, Set<String>> aliases) {
        if (!StringUtils.hasText(path)) {
            return Set.of(REPORT_LEVEL);
        }
        return aliases.getOrDefault(path, Set.of(path));
    }

    private String key(String alias, IssueExportRow issue) {
        return alias + "|" + issue.getCategory() + "|" + normalizeTitle(issue.getTitle());
    }

    private boolean aliasesIntersect(String basePath, Map<String, Set<String>> baseAliases,
                                     String targetPath, Map<String, Set<String>> targetAliases) {
        Set<String> base = aliases(basePath, baseAliases);
        Set<String> target = aliases(targetPath, targetAliases);
        for (String value : base) {
            if (target.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean rangesOverlap(Integer baseStart, Integer baseEnd,
                                  Integer targetStart, Integer targetEnd) {
        if (baseStart == null && targetStart == null) {
            return true;
        }
        if (baseStart == null || targetStart == null) {
            return false;
        }
        int baseFrom = Math.min(baseStart, baseEnd == null ? baseStart : baseEnd);
        int baseTo = Math.max(baseStart, baseEnd == null ? baseStart : baseEnd);
        int targetFrom = Math.min(targetStart, targetEnd == null ? targetStart : targetEnd);
        int targetTo = Math.max(targetStart, targetEnd == null ? targetStart : targetEnd);
        return baseFrom <= targetTo && targetFrom <= baseTo;
    }

    private CompareIssueVO toRemaining(IssueExportRow base, IssueExportRow target, String matchConfidence) {
        CompareIssueVO vo = toBase(target);
        vo.setIssueId(target.getIssueId());
        vo.setBaseIssueId(base.getIssueId());
        vo.setTargetIssueId(target.getIssueId());
        vo.setLineShifted(!Objects.equals(base.getStartLine(), target.getStartLine())
                || !Objects.equals(base.getEndLine(), target.getEndLine()));
        vo.setMatchConfidence(matchConfidence);
        return vo;
    }

    private CompareIssueVO toResolved(IssueExportRow issue) {
        CompareIssueVO vo = toBase(issue);
        vo.setIssueId(issue.getIssueId());
        vo.setLineShifted(false);
        return vo;
    }

    private CompareIssueVO toAdded(IssueExportRow issue) {
        CompareIssueVO vo = toBase(issue);
        vo.setIssueId(issue.getIssueId());
        vo.setLineShifted(false);
        return vo;
    }

    private CompareIssueVO toBase(IssueExportRow issue) {
        CompareIssueVO vo = new CompareIssueVO();
        vo.setTitle(issue.getTitle());
        vo.setCategory(issue.getCategory());
        vo.setSeverity(issue.getSeverity());
        vo.setConfidence(issue.getConfidence());
        vo.setFilePath(issue.getFilePath());
        vo.setStartLine(issue.getStartLine());
        vo.setEndLine(issue.getEndLine());
        vo.setFeedbackStatus(issue.getFeedbackStatus());
        return vo;
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
