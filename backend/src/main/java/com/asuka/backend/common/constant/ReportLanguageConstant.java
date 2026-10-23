package com.asuka.backend.common.constant;

import org.springframework.util.StringUtils;

/**
 * 报告语言常量。
 *
 * <p>报告语言在任务创建时固定，写入 analysis_task.report_language；
 * 后续切换界面语言不会改写已生成的报告和导出内容。
 */
public final class ReportLanguageConstant {

    /** 简体中文 */
    public static final String ZH_CN = "zh-CN";

    /** 英文 */
    public static final String EN_US = "en-US";

    /** 默认报告语言 */
    public static final String DEFAULT_LANGUAGE = ZH_CN;

    public static boolean isValid(String language) {
        return ZH_CN.equals(language) || EN_US.equals(language);
    }

    /**
     * 归一化语言参数：空值返回默认语言，非法值返回 null 由调用方决定是否报错。
     */
    public static String normalize(String language) {
        if (!StringUtils.hasText(language)) {
            return DEFAULT_LANGUAGE;
        }
        String value = language.trim();
        return isValid(value) ? value : null;
    }

    private ReportLanguageConstant() {
    }
}
