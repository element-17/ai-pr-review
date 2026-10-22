package com.asuka.backend.common.constant;

/**
 * 任务文件变更类型与分析状态常量。
 */
public final class TaskFileConstant {

    /** 新增 */
    public static final int CHANGE_ADD = 1;

    /** 修改 */
    public static final int CHANGE_MODIFY = 2;

    /** 删除 */
    public static final int CHANGE_DELETE = 3;

    /** 重命名 */
    public static final int CHANGE_RENAME = 4;

    /** 复制 */
    public static final int CHANGE_COPY = 5;

    /** 待分析 */
    public static final int STATUS_PENDING = 1;

    /** 已分析 */
    public static final int STATUS_ANALYZED = 2;

    /** 已跳过 */
    public static final int STATUS_SKIPPED = 3;

    /** 分析失败 */
    public static final int STATUS_FAILED = 4;

    /** 是否为合法的变更类型 */
    public static boolean isValidChangeType(Integer changeType) {
        return changeType != null && changeType >= CHANGE_ADD && changeType <= CHANGE_COPY;
    }

    /** 是否为合法的文件分析状态 */
    public static boolean isValidStatus(Integer status) {
        return status != null && status >= STATUS_PENDING && status <= STATUS_FAILED;
    }

    private TaskFileConstant() {
    }
}
