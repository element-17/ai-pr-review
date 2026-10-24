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
 * 用户设置。
 *
 * <p>主键直接使用 user_id，与 user 一对一。
 * check_categories、ignore_dirs 用逗号分隔存储，读取时转换成数组。
 */
@Data
@TableName("user_setting")
public class UserSetting implements Serializable {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 主题：1 浅色，2 深色，3 跟随系统 */
    private Integer theme;

    /** 界面语言：zh-CN / en-US */
    private String language;

    /** 重点检查类别，逗号分隔 */
    private String checkCategories;

    /** 忽略目录，逗号分隔 */
    private String ignoreDirs;

    /** 单次分析最大文件数 */
    private Integer maxFileCount;

    /** 单次分析最大 Diff 行数 */
    private Integer maxDiffLines;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
