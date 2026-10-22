package com.asuka.backend.analysis;

import com.asuka.backend.vo.AnalysisConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 分析配置快照解析器。
 *
 * <p>analysis_config 是 JSON 文本，只用于展示；解析失败时返回 null，
 * 不影响任务详情和报告主体，也不会把原始 JSON 打到日志里。
 *
 * <p>这里使用独立的 Jackson 2 实例，不依赖 Spring Boot 4 的 HTTP 序列化器，
 * 避免 analysis_config 的解析行为随 Web 层 Jackson 版本变化。
 */
@Slf4j
@Component
public class AnalysisConfigParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public AnalysisConfigVO parse(String json, Long taskId) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, AnalysisConfigVO.class);
        } catch (Exception ex) {
            log.warn("任务 {} 的 analysis_config 解析失败", taskId);
            return null;
        }
    }
}
