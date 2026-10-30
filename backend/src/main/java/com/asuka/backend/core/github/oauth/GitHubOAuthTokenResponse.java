package com.asuka.backend.core.github.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * GitHub OAuth access_token 交换响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubOAuthTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    private String scope;

    private String error;

    @JsonProperty("error_description")
    private String errorDescription;
}
