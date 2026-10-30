package com.asuka.backend.core.github.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * GitHub 邮箱列表项。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubOAuthEmail {

    private String email;

    private Boolean primary;

    private Boolean verified;
}
