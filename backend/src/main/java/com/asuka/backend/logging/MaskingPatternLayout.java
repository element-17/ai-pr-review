package com.asuka.backend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 在日志格式化完成后统一脱敏。
 *
 * <p>覆盖控制台、滚动文件和错误文件，避免各 appender 行为不一致。
 */
public class MaskingPatternLayout extends PatternLayout {

    @Override
    public String doLayout(ILoggingEvent event) {
        return LogSanitizer.sanitize(super.doLayout(event));
    }
}
