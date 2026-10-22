package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分析报告总结。
 *
 * <p>三个总结字段允许为空，用于表达部分完成时未生成的章节。
 */
@Data
@TableName("analysis_report")
public class AnalysisReport implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** PR 修改目的 */
    private String purpose;

    /** 主要变更 */
    private String changes;

    /** 可能影响的模块 */
    private String impacts;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
