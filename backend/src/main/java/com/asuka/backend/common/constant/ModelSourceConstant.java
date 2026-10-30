package com.asuka.backend.common.constant;

/**
 * 任务模型来源，写入 analysis_config 快照。
 */
public final class ModelSourceConstant {

    public static final String USER_PREFERENCE = "USER_PREFERENCE";

    public static final String ADMIN_DEFAULT = "ADMIN_DEFAULT";

    public static final String ENV_FALLBACK = "ENV_FALLBACK";

    public static final String TASK_OVERRIDE = "TASK_OVERRIDE";

    public static final String ORIGINAL_TASK = "ORIGINAL_TASK";

    private ModelSourceConstant() {
    }
}
