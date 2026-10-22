package com.asuka.backend.core.integration.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub Pull Request 元数据。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubPrResponse {

    private Integer number;

    private String title;

    private String body;

    private String state;

    private Boolean merged;

    private User user;

    private Branch base;

    private Branch head;

    private Integer additions;

    private Integer deletions;

    @JsonProperty("changed_files")
    private Integer changedFiles;

    @JsonProperty("html_url")
    private String htmlUrl;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        private String login;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Branch {
        private String ref;
        private String sha;
    }
}
