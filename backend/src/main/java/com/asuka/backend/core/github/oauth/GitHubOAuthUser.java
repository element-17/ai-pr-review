package com.asuka.backend.core.github.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub 用户资料。id 是唯一标识；login/avatar 只用于展示。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubOAuthUser {

    private Long id;

    private String login;

    private String name;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String email;
}
