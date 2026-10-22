package com.asuka.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * agent submit_issues 参数。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IssueDraftList {

    private List<IssueDraft> issues;
}
