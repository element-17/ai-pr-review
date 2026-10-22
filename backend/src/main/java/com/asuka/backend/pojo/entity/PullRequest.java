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
 * GitHub Pull Request 元数据。
 *
 * <p>同一仓库同一 PR 只存一行，head_sha 保存当前最新提交；
 * 分析任务自己的 head_sha 是版本快照，不随 PR 更新。
 */
@Data
@TableName("pull_request")
public class PullRequest implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 仓库全名，owner/repo */
    private String repoFullName;

    /** PR 编号 */
    private Integer prNumber;

    private String title;

    /** PR 作者 GitHub 用户名 */
    private String author;

    /** 1 open，2 closed，3 merged */
    private Integer state;

    private String baseBranch;

    private String headBranch;

    /** PR 当前最新 head commit SHA */
    private String headSha;

    private Integer addLines;

    private Integer deleteLines;

    private Integer fileCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
