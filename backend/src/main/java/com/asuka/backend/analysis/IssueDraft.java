package com.asuka.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * agent 返回的单条候选问题。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueDraft {

    private String title;

    private Integer category;

    private Integer severity;

    private Integer confidence;

    private String filePath;

    private Integer startLine;

    private Integer endLine;

    private String triggerCondition;

    private String impact;

    private String evidence;

    private String suggestion;
}
