package com.asuka.backend.constant;

/**
 * 分析任务状态与阶段常量。
 *
 * <p>状态和阶段都用 TINYINT 存编码，前端根据编码做国际化展示。
 */
public final class TaskStatusConstant {

    /** 排队中 */
    public static final int QUEUED = 1;

    /** 分析中 */
    public static final int RUNNING = 2;

    /** 已完成 */
    public static final int COMPLETED = 3;

    /** 部分完成 */
    public static final int PARTIAL = 4;

    /** 失败 */
    public static final int FAILED = 5;

    /** 已取消 */
    public static final int CANCELED = 6;

    /** 阶段：排队 */
    public static final int STAGE_QUEUED = 1;

    /** 阶段：获取 PR */
    public static final int STAGE_FETCH_PR = 2;

    /** 阶段：获取变更 */
    public static final int STAGE_FETCH_FILES = 3;

    /** 阶段：补充上下文 */
    public static final int STAGE_CONTEXT = 4;

    /** 阶段：AI 分析 */
    public static final int STAGE_AI = 5;

    /** 阶段：校验入库 */
    public static final int STAGE_VALIDATE = 6;

    /** 阶段：结束 */
    public static final int STAGE_FINISHED = 7;

    /** 状态是否为执行中（排队或分析中） */
    public static boolean isRunning(Integer status) {
        return status != null && (status == QUEUED || status == RUNNING);
    }

    /** 状态是否为已结束（完成、部分完成、失败、取消） */
    public static boolean isFinished(Integer status) {
        return status != null
                && (status == COMPLETED || status == PARTIAL || status == FAILED || status == CANCELED);
    }

    private TaskStatusConstant() {
    }
}
