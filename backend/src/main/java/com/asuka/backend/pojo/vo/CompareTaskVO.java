package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本对比中的单条任务信息。
 */
@Data
@Schema(description = "对比任务信息")
public class CompareTaskVO {

    private Long taskId;

    private String headSha;

    private Integer status;

    private String reportLanguage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishTime;

    private TaskStatsVO stats;
}
