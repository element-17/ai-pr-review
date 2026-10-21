package com.asuka.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 历史任务分页查询的行结果。
 *
 * <p>Mapper XML 直接返回该对象，再由服务层组装成前端 VO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskListRow {

    private Long taskId;

    private String repoFullName;

    private Integer prNumber;

    private String prTitle;

    private String headSha;

    private Integer status;

    private Integer stage;

    private Integer progress;

    private String reportLanguage;

    /** 任务 head_sha 与 PR 当前 head_sha 是否不一致 */
    private Boolean isExpired;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
