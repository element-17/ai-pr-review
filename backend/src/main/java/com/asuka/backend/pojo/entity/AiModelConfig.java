package com.asuka.backend.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员模型配置。
 *
 * <p>api_key_ciphertext 只保存 AES-GCM 密文，普通查询和接口响应都不返回。
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String displayName;

    /** OPENAI_CHAT / ANTHROPIC_MESSAGES */
    private String provider;

    private String baseUrl;

    private String modelId;

    private String apiKeyCiphertext;

    /** API Key 末 4 位掩码，仅用于列表展示 */
    private String apiKeyHint;

    private Integer maxOutputTokens;

    private BigDecimal temperature;

    private Integer enabled;

    private Integer isDefault;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
