package com.asuka.backend.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * agent submit_issues 参数。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentIssues {

    private List<AgentIssue> issues;
}
