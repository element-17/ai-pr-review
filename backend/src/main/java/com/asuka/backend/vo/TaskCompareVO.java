package com.asuka.backend.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 同一 PR 两个分析版本的对比结果。
 */
@Data
@Schema(description = "版本对比结果")
public class TaskCompareVO {

    private CompareTaskVO base;

    private CompareTaskVO target;

    private CompareSummaryVO summary;

    private CompareIssuesVO issues;

    private CompareMatchingVO matching;
}
