package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务详情与工作台总览数据。
 */
@Data
@Schema(description = "任务详情")
public class TaskDetailVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "当前阶段")
    private Integer stage;

    @Schema(description = "进度 0-100")
    private Integer progress;

    @Schema(description = "任务固定的 head SHA")
    private String headSha;

    @Schema(description = "报告语言")
    private String reportLanguage;

    @Schema(description = "使用的模型")
    private String modelName;

    @Schema(description = "模型显示名称")
    private String modelDisplayName;

    @Schema(description = "模型请求格式")
    private String modelProvider;

    @Schema(description = "本次分析的非敏感配置")
    private AnalysisConfigVO analysisConfig;

    @Schema(description = "失败或部分完成的原因编码")
    private Integer errorCode;

    @Schema(description = "失败或部分完成的原因摘要")
    private String errorMessage;

    @Schema(description = "是否游客任务")
    private Boolean isGuest;

    @Schema(description = "PR 有新提交后旧任务为 true")
    private Boolean isExpired;

    @Schema(description = "是否可以取消")
    private Boolean canCancel;

    @Schema(description = "是否可以重试")
    private Boolean canRetry;

    @Schema(description = "是否可以删除")
    private Boolean canDelete;

    @Schema(description = "是否已生成报告")
    private Boolean hasReport;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;

    @Schema(description = "PR 信息")
    private PrVO pr;

    @Schema(description = "任务统计")
    private TaskStatsVO stats;

    @Schema(description = "重点文件")
    private List<PriorityFileVO> priorityFiles;
}
