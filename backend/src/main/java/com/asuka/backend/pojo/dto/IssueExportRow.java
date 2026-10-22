package com.asuka.backend.pojo.dto;

import lombok.Data;

/**
 * Markdown 导出使用的问题行。
 */
@Data
public class IssueExportRow {

    private Long issueId;

    private String title;

    private Integer category;

    private Integer severity;

    private Integer confidence;

    private String filePath;

    private Integer startLine;

    private Integer endLine;

    private String triggerCondition;

    private String impact;

    private String evidence;

    private String suggestion;

    private Integer feedbackStatus;

    private String feedbackRemark;
}
