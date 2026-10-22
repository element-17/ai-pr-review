package com.asuka.backend.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 两个版本的总结变化。
 */
@Data
@Schema(description = "总结变化")
public class CompareSummaryVO {

    private CompareFieldVO purpose;

    private CompareFieldVO changes;

    private CompareFieldVO impacts;
}
