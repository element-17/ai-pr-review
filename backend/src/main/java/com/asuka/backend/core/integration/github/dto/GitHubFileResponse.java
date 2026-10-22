package com.asuka.backend.core.integration.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub Pull Request 文件变更。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubFileResponse {

    private String filename;

    @JsonProperty("previous_filename")
    private String previousFilename;

    /** added / modified / removed / renamed / copied / changed / unchanged */
    private String status;

    private Integer additions;

    private Integer deletions;

    private Integer changes;

    /** 文件 diff 片段；二进制或过大时 GitHub 返回 null */
    private String patch;

    private String sha;

    @JsonProperty("blob_url")
    private String blobUrl;

    @JsonProperty("raw_url")
    private String rawUrl;

    @JsonProperty("contents_url")
    private String contentsUrl;
}
