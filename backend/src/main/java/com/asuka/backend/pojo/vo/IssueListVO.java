package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 问题列表与任务级统计。
 *
 * <p>stats 不受当前筛选条件影响，方便前端展示整体处理进度。
 */
@Data
@Schema(description = "问题列表")
public class IssueListVO {

    @Schema(description = "当前筛选条件下的总条数")
    private Long total;

    @Schema(description = "当前页问题")
    private List<IssueVO> records;

    @Schema(description = "任务级问题统计")
    private IssueStatsVO stats;
}
