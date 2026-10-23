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
 * 任务变更文件快照。
 *
 * <p>patch 是大文本，列表查询不返回；只有文件详情接口按需读取。
 */
@Data
@TableName("task_file")
public class TaskFile implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String filePath;

    /** 重命名前的路径 */
    private String oldPath;

    /** 1 新增，2 修改，3 删除，4 重命名，5 复制 */
    private Integer changeType;

    private Integer addLines;

    private Integer deleteLines;

    private String language;

    /** 0 否，1 是 */
    private Integer isBinary;

    /** 文件 diff 片段 */
    private String patch;

    /** 1 待分析，2 已分析，3 已跳过，4 分析失败 */
    private Integer status;

    private String skipReason;

    private String priorityReason;

    private String summary;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
