package com.asuka.backend.integration.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub Contents API 目录项。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubContentItem {

    private String name;

    private String path;

    /** file / dir / symlink / submodule */
    private String type;

    private Long size;

    @JsonProperty("download_url")
    private String downloadUrl;
}
