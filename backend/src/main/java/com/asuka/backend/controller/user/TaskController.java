package com.asuka.backend.controller.user;

import cn.dev33.satoken.annotation.SaIgnore;
import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.TaskCreateDTO;
import com.asuka.backend.dto.TaskQueryDTO;
import com.asuka.backend.service.TaskCompareService;
import com.asuka.backend.service.TaskCreateService;
import com.asuka.backend.service.TaskLifecycleService;
import com.asuka.backend.service.TaskService;
import com.asuka.backend.vo.TaskActionVO;
import com.asuka.backend.vo.TaskCompareVO;
import com.asuka.backend.vo.TaskCreateVO;
import com.asuka.backend.vo.TaskDeleteVO;
import com.asuka.backend.vo.TaskDetailVO;
import com.asuka.backend.vo.TaskListItemVO;
import com.asuka.backend.vo.TaskProgressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分析任务查询接口。
 *
 * <p>历史列表要求登录；详情和进度允许登录用户或有效游客令牌访问。
 */
@Tag(name = "分析任务")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;
    private final TaskCreateService taskCreateService;
    private final TaskLifecycleService taskLifecycleService;
    private final TaskCompareService taskCompareService;

    @Operation(summary = "创建分析任务", description = "允许游客；创建后异步拉取变更并分析")
    @SaIgnore
    @PostMapping
    public Result<TaskCreateVO> create(
            @Valid @RequestBody TaskCreateDTO dto,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(taskCreateService.create(dto, idempotencyKey));
    }

    @Operation(summary = "我的任务列表", description = "按仓库、PR 编号、状态和时间筛选")
    @SecurityRequirement(name = "sa-token")
    @GetMapping
    public Result<PageResult<TaskListItemVO>> page(@Valid TaskQueryDTO query) {
        return Result.success(taskService.pageMyTasks(query));
    }

    @Operation(summary = "任务详情", description = "返回 PR 信息、状态、统计、重点文件和下钻 ID")
    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> detail(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId) {
        return Result.success(taskService.getDetail(taskId));
    }

    @Operation(summary = "任务进度", description = "轻量轮询接口，只返回状态字段")
    @GetMapping("/{taskId}/progress")
    public Result<TaskProgressVO> progress(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId) {
        return Result.success(taskService.getProgress(taskId));
    }

    @Operation(summary = "取消任务", description = "登录用户或有效游客令牌")
    @PostMapping("/{taskId}/cancel")
    public Result<TaskActionVO> cancel(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId) {
        return Result.success(taskLifecycleService.cancel(taskId));
    }

    @Operation(summary = "重试任务", description = "失败、部分完成或已取消的任务可重试，新建任务")
    @PostMapping("/{taskId}/retry")
    public Result<TaskCreateVO> retry(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(taskCreateService.retry(taskId, idempotencyKey));
    }

    @Operation(summary = "删除任务", description = "仅登录本人，逻辑删除已结束任务")
    @DeleteMapping("/{taskId}")
    public Result<TaskDeleteVO> delete(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId) {
        return Result.success(taskLifecycleService.delete(taskId));
    }

    @Operation(summary = "版本对比", description = "对比同一 PR 两个已完成/部分完成版本的问题和总结")
    @SecurityRequirement(name = "sa-token")
    @GetMapping("/{baseTaskId}/compare/{targetTaskId}")
    public Result<TaskCompareVO> compare(
            @Parameter(description = "基线任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long baseTaskId,
            @Parameter(description = "目标任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long targetTaskId) {
        return Result.success(taskCompareService.compare(baseTaskId, targetTaskId));
    }
}
