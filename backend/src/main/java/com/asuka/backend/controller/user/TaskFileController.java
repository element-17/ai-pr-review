package com.asuka.backend.controller.user;

import com.asuka.backend.result.PageResult;
import com.asuka.backend.result.Result;
import com.asuka.backend.dto.TaskFileQueryDTO;
import com.asuka.backend.service.TaskFileService;
import com.asuka.backend.vo.TaskFileDetailVO;
import com.asuka.backend.vo.TaskFileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务文件与 Diff 读取接口。
 */
@Tag(name = "任务文件")
@RestController
@RequestMapping("/api/tasks/{taskId}/files")
@RequiredArgsConstructor
@Validated
public class TaskFileController {

    private final TaskFileService taskFileService;

    @Operation(summary = "文件列表", description = "支持状态、变更类型、重点文件、路径搜索和排序")
    @GetMapping
    public Result<PageResult<TaskFileVO>> page(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Valid TaskFileQueryDTO query) {
        return Result.success(taskFileService.page(taskId, query));
    }

    @Operation(summary = "文件详情", description = "按需返回 patch、GitHub 链接和关联问题")
    @GetMapping("/{fileId}")
    public Result<TaskFileDetailVO> detail(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Parameter(description = "文件 ID") @PathVariable @Min(value = 1, message = "文件 ID 非法")
            Long fileId) {
        return Result.success(taskFileService.getDetail(taskId, fileId));
    }
}
