package com.asuka.backend.controller.user;

import com.asuka.backend.common.result.PageResult;
import com.asuka.backend.common.result.Result;
import com.asuka.backend.dto.TaskQueryDTO;
import com.asuka.backend.service.TaskService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
