package com.asuka.backend.common.cache;

/**
 * 缓存名称与逻辑 TTL 常量。
 *
 * <p>缓存名最终会作为 Redis key 前缀，例如 {@code user:setting:1}。
 */
public final class CacheNames {

    /** 用户设置 */
    public static final String USER_SETTING = "user:setting";

    /** 任务创建使用的设置快照 */
    public static final String USER_SETTING_CONFIG = "user:setting:config";

    /** 终态任务详情 */
    public static final String TASK_DETAIL = "task:detail";

    /** 终态任务报告 */
    public static final String TASK_REPORT = "task:report";

    /** 终态任务文件列表 */
    public static final String TASK_FILES = "task:files";

    /** 终态任务文件结构化 Diff */
    public static final String TASK_DIFF = "task:file:diff";

    /** 任务详情逻辑 TTL：5 分钟 */
    public static final long TASK_DETAIL_LOGICAL_TTL_SECONDS = 300;

    /** 任务报告逻辑 TTL：5 分钟 */
    public static final long TASK_REPORT_LOGICAL_TTL_SECONDS = 300;

    /** 文件列表逻辑 TTL：5 分钟 */
    public static final long TASK_FILES_LOGICAL_TTL_SECONDS = 300;

    /** 文件 Diff 逻辑 TTL：30 分钟 */
    public static final long TASK_DIFF_LOGICAL_TTL_SECONDS = 1800;

    private CacheNames() {
    }
}
