package com.asuka.backend.controller.user;

import com.asuka.backend.result.Result;
import com.asuka.backend.dto.ReviewIssueQueryDTO;
import com.asuka.backend.dto.ReviewIssueUpdateDTO;
import com.asuka.backend.service.ReviewIssueService;
import com.asuka.backend.vo.IssueDetailVO;
import com.asuka.backend.vo.IssueListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评审问题列表、详情与人工反馈接口。
 */
@Tag(name = "评审问题")
@RestController
@RequestMapping("/api/tasks/{taskId}/issues")
@RequiredArgsConstructor
@Validated
public class ReviewIssueController {

    private final ReviewIssueService reviewIssueService;

    @Operation(summary = "问题列表", description = "支持严重度、类别、文件、处理状态组合筛选和关键词搜索")
    @GetMapping
    public Result<IssueListVO> page(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Valid ReviewIssueQueryDTO query) {
        return Result.success(reviewIssueService.page(taskId, query));
    }

    @Operation(summary = "问题详情")
    @GetMapping("/{issueId}")
    public Result<IssueDetailVO> detail(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Parameter(description = "问题 ID") @PathVariable @Min(value = 1, message = "问题 ID 非法")
            Long issueId) {
        return Result.success(reviewIssueService.getDetail(taskId, issueId));
    }

    @Operation(summary = "更新处理状态、理由或建议", description = "标记误报时必须填写理由")
    @PatchMapping("/{issueId}")
    public Result<IssueDetailVO> update(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Parameter(description = "问题 ID") @PathVariable @Min(value = 1, message = "问题 ID 非法")
            Long issueId,
            @Valid @RequestBody ReviewIssueUpdateDTO dto) {
        return Result.success(reviewIssueService.update(taskId, issueId, dto));
    }

    @Operation(summary = "撤销人工处理", description = "状态回到待确认，理由清空，建议恢复模型原文")
    @PostMapping("/{issueId}/undo")
    public Result<IssueDetailVO> undo(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Parameter(description = "问题 ID") @PathVariable @Min(value = 1, message = "问题 ID 非法")
            Long issueId) {
        return Result.success(reviewIssueService.undo(taskId, issueId));
    }
}
