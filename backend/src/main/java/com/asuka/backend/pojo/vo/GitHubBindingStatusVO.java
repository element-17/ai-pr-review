package com.asuka.backend.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前用户 GitHub 绑定状态。
 *
 * <p>只返回展示字段和状态，不返回 access token、密文或 OAuth 内部信息。
 */
@Data
@Schema(description = "GitHub 绑定状态")
public class GitHubBindingStatusVO {

    @Schema(description = "是否已绑定")
    private Boolean bound;

    @Schema(description = "GitHub 登录名")
    private String githubLogin;

    @Schema(description = "GitHub 头像")
    private String avatarUrl;

    @Schema(description = "GitHub 主邮箱")
    private String email;

    @Schema(description = "授权 scope")
    private String scope;

    @Schema(description = "令牌状态：1 有效，2 失效，3 已撤销")
    private Integer tokenStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "授权时间")
    private LocalDateTime authorizedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近校验时间")
    private LocalDateTime lastValidatedAt;

    @Schema(description = "当前账号是否已设置可用密码")
    private Boolean passwordSet;
}
