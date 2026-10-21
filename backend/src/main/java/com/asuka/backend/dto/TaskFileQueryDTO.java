package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务文件列表查询条件。
 */
@Data
@Schema(description = "任务文件查询条件")
public class TaskFileQueryDTO {

    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 200, message = "每页数量不能超过 200")
    @Schema(description = "每页数量", example = "100")
    private Integer pageSize = 100;

    @Min(value = 1, message = "文件状态非法")
    @Max(value = 4, message = "文件状态非法")
    @Schema(description = "文件状态：1 待分析，2 已分析，3 已跳过，4 分析失败")
    private Integer status;

    @Min(value = 1, message = "变更类型非法")
    @Max(value = 5, message = "变更类型非法")
    @Schema(description = "变更类型：1 新增，2 修改，3 删除，4 重命名，5 复制")
    private Integer changeType;

    @Schema(description = "只看重点文件", example = "false")
    private Boolean priorityOnly = false;

    @Size(max = 200, message = "文件路径关键字长度不能超过 200")
    @Schema(description = "按文件路径模糊搜索")
    private String keyword;

    @Pattern(regexp = "path_asc|addLines_desc|deleteLines_desc", message = "排序方式非法")
    @Schema(description = "排序：path_asc / addLines_desc / deleteLines_desc", example = "path_asc")
    private String sort = "path_asc";
}
