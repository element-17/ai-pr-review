package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评审问题列表查询条件。
 *
 * <p>severity、category、feedbackStatus 支持逗号分隔的多选值，
 * 由服务层解析成列表后交给 Mapper XML 的 IN 查询。
 */
@Data
@Schema(description = "评审问题查询条件")
public class ReviewIssueQueryDTO {

    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 200, message = "每页数量不能超过 200")
    @Schema(description = "每页数量", example = "50")
    private Integer pageSize = 50;

    @Pattern(regexp = "[1-4](,[1-4])*", message = "严重度筛选值非法")
    @Schema(description = "严重度，逗号分隔，如 3,4")
    private String severity;

    @Pattern(regexp = "[1-7](,[1-7])*", message = "类别筛选值非法")
    @Schema(description = "类别，逗号分隔，如 1,2")
    private String category;

    @Min(value = 1, message = "文件 ID 非法")
    @Schema(description = "只看某个文件")
    private Long fileId;

    @Pattern(regexp = "[1-4](,[1-4])*", message = "处理状态筛选值非法")
    @Schema(description = "处理状态，逗号分隔，如 1,2")
    private String feedbackStatus;

    @Size(max = 200, message = "关键词长度不能超过 200")
    @Schema(description = "搜索标题、触发条件、影响、证据")
    private String keyword;

    @Pattern(regexp = "severity_desc|severity_asc|createTime_asc|createTime_desc", message = "排序方式非法")
    @Schema(description = "排序：severity_desc / severity_asc / createTime_asc / createTime_desc",
            example = "severity_desc")
    private String sort = "severity_desc";
}
