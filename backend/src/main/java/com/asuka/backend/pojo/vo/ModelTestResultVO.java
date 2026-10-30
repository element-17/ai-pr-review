package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模型测试连接结果。
 */
@Data
@Schema(description = "模型测试结果")
public class ModelTestResultVO {

    private Boolean success;

    private String provider;

    private String modelId;

    private Long latencyMs;

    private String message;
}
