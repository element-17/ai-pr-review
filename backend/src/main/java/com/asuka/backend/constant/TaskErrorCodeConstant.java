package com.asuka.backend.constant;

/**
 * 分析任务失败原因编码，写入 analysis_task.error_code。
 *
 * <p>与接口响应体里的业务码是两套体系，不要混用。
 */
public final class TaskErrorCodeConstant {

    /** PR 无效或不存在 */
    public static final int PR_INVALID = 1;

    /** GitHub 限流 */
    public static final int GITHUB_RATE_LIMITED = 2;

    /** 无仓库权限 */
    public static final int GITHUB_FORBIDDEN = 3;

    /** GitHub 请求失败或超时 */
    public static final int GITHUB_FAILED = 4;

    /** Diff 过大 */
    public static final int DIFF_TOO_LARGE = 5;

    /** 模型调用超时 */
    public static final int MODEL_TIMEOUT = 6;

    /** 模型输出格式错误 */
    public static final int MODEL_FORMAT_ERROR = 7;

    /** 内部错误 */
    public static final int INTERNAL_ERROR = 8;

    private TaskErrorCodeConstant() {
    }
}
