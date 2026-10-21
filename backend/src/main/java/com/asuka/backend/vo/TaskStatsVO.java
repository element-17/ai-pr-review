package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务总览统计。
 */
@Data
@Schema(description = "任务总览统计")
public class TaskStatsVO {

    @Schema(description = "文件统计")
    private FileStatsVO files;

    @Schema(description = "问题统计")
    private IssueStatsVO issues;
}
