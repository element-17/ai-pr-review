package com.asuka.backend.common.constant;

/**
 * 评审问题类别、严重度与人工处理状态常量。
 *
 * <p>严重度描述潜在影响，置信度描述证据充分程度，两者分开存储和展示。
 */
public final class ReviewIssueConstant {

    /** 逻辑错误 */
    public static final int CATEGORY_LOGIC = 1;

    /** 空值与边界 */
    public static final int CATEGORY_BOUNDARY = 2;

    /** 异常处理 */
    public static final int CATEGORY_EXCEPTION = 3;

    /** 权限与敏感信息 */
    public static final int CATEGORY_SECURITY = 4;

    /** 接口与数据兼容 */
    public static final int CATEGORY_COMPATIBILITY = 5;

    /** 缺失测试 */
    public static final int CATEGORY_TEST = 6;

    /** 其他 */
    public static final int CATEGORY_OTHER = 7;

    /** 低 */
    public static final int SEVERITY_LOW = 1;

    /** 中 */
    public static final int SEVERITY_MEDIUM = 2;

    /** 高 */
    public static final int SEVERITY_HIGH = 3;

    /** 严重 */
    public static final int SEVERITY_CRITICAL = 4;

    /** 待确认 */
    public static final int FEEDBACK_PENDING = 1;

    /** 已采纳 */
    public static final int FEEDBACK_ACCEPTED = 2;

    /** 已解决 */
    public static final int FEEDBACK_RESOLVED = 3;

    /** 误报 */
    public static final int FEEDBACK_FALSE_POSITIVE = 4;

    /** 置信度低于该值时提示需要人工重点核查 */
    public static final int LOW_CONFIDENCE_THRESHOLD = 60;

    public static boolean isValidCategory(Integer category) {
        return category != null && category >= CATEGORY_LOGIC && category <= CATEGORY_OTHER;
    }

    public static boolean isValidSeverity(Integer severity) {
        return severity != null && severity >= SEVERITY_LOW && severity <= SEVERITY_CRITICAL;
    }

    public static boolean isValidFeedbackStatus(Integer status) {
        return status != null && status >= FEEDBACK_PENDING && status <= FEEDBACK_FALSE_POSITIVE;
    }

    private ReviewIssueConstant() {
    }
}
