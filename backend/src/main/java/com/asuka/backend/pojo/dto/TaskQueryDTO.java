package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 历史任务查询条件。
 */
@Data
@Schema(description = "历史任务查询条件")
public class TaskQueryDTO {

    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    @Schema(description = "每页数量", example = "20")
    private Integer pageSize = 20;

    @Size(max = 150, message = "仓库全名长度不能超过 150")
    @Schema(description = "仓库全名，如 owner/repo", example = "octocat/Hello-World")
    private String repo;

    @Min(value = 1, message = "PR 编号不能小于 1")
    @Schema(description = "PR 编号；传了它必须同时传 repo", example = "1347")
    private Integer prNumber;

    @Min(value = 1, message = "任务状态非法")
    @Max(value = 6, message = "任务状态非法")
    @Schema(description = "任务状态：1 排队，2 分析中，3 已完成，4 部分完成，5 失败，6 已取消")
    private Integer status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "创建时间起，yyyy-MM-dd", example = "2026-10-01")
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "创建时间止，包含当天", example = "2026-10-31")
    private LocalDate endDate;

    @Pattern(regexp = "createTime_desc|createTime_asc", message = "排序方式非法")
    @Schema(description = "排序：createTime_desc / createTime_asc", example = "createTime_desc")
    private String sort = "createTime_desc";
}
