package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * PR 预览结果。
 */
@Data
@Schema(description = "PR 预览结果")
public class PrPreviewVO {

    @Schema(description = "PR 基本信息")
    private PrVO pr;

    @Schema(description = "当前用户可复用的同版本任务；游客或没有时为空")
    private ReusableTaskVO reusableTask;
}
