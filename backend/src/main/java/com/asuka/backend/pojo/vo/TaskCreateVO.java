package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建/重试任务返回。
 */
@Data
@Schema(description = "任务创建结果")
public class TaskCreateVO {

    @Schema(description = "任务 ID")
    private Long taskId;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "当前阶段")
    private Integer stage;

    @Schema(description = "进度 0-100")
    private Integer progress;

    @Schema(description = "报告语言")
    private String reportLanguage;

    @Schema(description = "是否游客任务")
    private Boolean isGuest;

    @Schema(description = "游客令牌；登录用户为空")
    private String guestToken;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "游客任务过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "游客分享链接")
    private String shareUrl;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "重试来源任务 ID")
    private Long retryOfTaskId;
}
