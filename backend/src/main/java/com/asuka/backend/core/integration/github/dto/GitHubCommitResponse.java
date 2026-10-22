package com.asuka.backend.core.integration.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GitHub Pull Request 提交信息。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubCommitResponse {

    private String sha;

    private Commit commit;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Commit {
        private String message;
        private Author author;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String name;
        private String date;
    }
}
