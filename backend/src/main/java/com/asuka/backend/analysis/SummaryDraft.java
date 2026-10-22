package com.asuka.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * agent submit_summary 参数。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummaryDraft {

    private String purpose;

    private String changes;

    private String impacts;

    private List<FileSummary> files;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileSummary {
        private String path;
        private String summary;
        private String priorityReason;
    }
}
