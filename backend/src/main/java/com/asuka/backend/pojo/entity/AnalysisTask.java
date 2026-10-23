package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * PR 分析任务。
 *
 * <p>报告、文件和问题都通过 task_id 关联到同一条任务，
 * 从数据层面保证同一个 head_sha 下的结论一致。
 */
@Data
@TableName("analysis_task")
public class AnalysisTask implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联 pull_request.id */
    private Long prId;

    /** 提交用户；游客任务为空 */
    private Long userId;

    /** 本次分析固定的 head commit SHA */
    private String headSha;

    /** 报告语言：zh-CN / en-US */
    private String reportLanguage;

    /** 1 排队，2 分析中，3 已完成，4 部分完成，5 失败，6 已取消 */
    private Integer status;

    /** 1 排队，2 获取 PR，3 获取变更，4 补充上下文，5 AI 分析，6 校验入库，7 结束 */
    private Integer stage;

    /** 进度 0-100 */
    private Integer progress;

    private String modelName;

    /** 本次分析的非敏感配置快照，JSON 字符串 */
    private String analysisConfig;

    private Integer errorCode;

    private String errorMessage;

    /** 游客访问令牌，登录用户任务为空 */
    private String guestToken;

    /** 重试来源任务 ID；正常创建为空 */
    private Long retryOfTaskId;

    /** 游客任务过期时间 */
    private LocalDateTime expireTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    /**
     * 逻辑删除标记。
     * 表列名是 is_deleted，与 sys_user 的 deleted 不同，这里显式指定列名。
     */
    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
