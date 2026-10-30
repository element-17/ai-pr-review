package com.asuka.backend.common.constant;

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

    /** 任务创建后 PR 又产生新提交，当前任务不再对应最新 head */
    public static final int PR_CHANGED = 9;

    /** 已绑定用户的 GitHub 授权失效，需要重新授权 */
    public static final int GITHUB_USER_TOKEN_INVALID = 10;

    /** 服务端公用 GitHub Token 无效 */
    public static final int GITHUB_SHARED_TOKEN_INVALID = 11;

    private TaskErrorCodeConstant() {
    }
}
