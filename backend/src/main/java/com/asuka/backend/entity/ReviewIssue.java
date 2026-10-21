package com.asuka.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评审问题与人工反馈。
 *
 * <p>人工处理结果直接覆盖在问题行上；ai_suggestion 保留模型原文，
 * suggestion 是当前展示和导出的版本，撤销时把原文写回。
 */
@Data
@TableName("review_issue")
public class ReviewIssue implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** 关联 task_file.id；报告级提示为空 */
    private Long fileId;

    private String title;

    /** 1-7，见 ReviewIssueConstant */
    private Integer category;

    /** 1 低，2 中，3 高，4 严重 */
    private Integer severity;

    /** 置信度 0-100 */
    private Integer confidence;

    private Integer startLine;

    private Integer endLine;

    private String triggerCondition;

    private String impact;

    private String evidence;

    /** 模型原始建议 */
    private String aiSuggestion;

    /** 当前建议，人工编辑后覆盖 */
    private String suggestion;

    /** 1 待确认，2 已采纳，3 已解决，4 误报 */
    private Integer feedbackStatus;

    private String feedbackRemark;

    /** 最后反馈操作人；游客为空 */
    private Long feedbackUserId;

    /** 最后反馈操作时间 */
    private LocalDateTime feedbackTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
