package com.asuka.backend.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问题处理状态与建议更新请求。
 *
 * <p>至少传一个字段；feedbackStatus=4（误报）时必须有处理理由。
 */
@Data
@Schema(description = "问题处理更新请求")
public class ReviewIssueUpdateDTO {

    @Min(value = 1, message = "处理状态非法")
    @Max(value = 4, message = "处理状态非法")
    @Schema(description = "处理状态：1 待确认，2 已采纳，3 已解决，4 误报")
    private Integer feedbackStatus;

    @Size(max = 500, message = "处理理由不能超过 500 字")
    @Schema(description = "处理理由；标记误报时必填")
    private String feedbackRemark;

    @Size(max = 5000, message = "建议不能超过 5000 字")
    @Schema(description = "编辑后的建议；空字符串不允许")
    private String suggestion;
}
