package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 GitHub 账号绑定。
 *
 * <p>一个本地用户最多一行、一个 GitHub 数字 id 最多绑定一个本地用户。
 * access_token_ciphertext 只保存 AES-GCM 密文，接口和日志永远不返回明文。
 */
@Data
@TableName("user_github_binding")
public class UserGitHubBinding implements Serializable {

    @TableId("user_id")
    private Long userId;

    /** GitHub 数字用户 ID，唯一标识 */
    private Long githubId;

    /** GitHub 登录名，仅作展示，可变 */
    private String githubLogin;

    private String avatarUrl;

    private String email;

    /** AES-GCM 加密后的 GitHub access token */
    private String accessTokenCiphertext;

    /** GitHub 返回的授权 scope */
    private String scope;

    /** 1 有效，2 失效，3 已撤销 */
    private Integer tokenStatus;

    private LocalDateTime authorizedAt;

    private LocalDateTime lastValidatedAt;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
