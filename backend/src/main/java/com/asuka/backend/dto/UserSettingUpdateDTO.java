package com.asuka.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户设置更新请求。
 *
 * <p>支持部分更新，至少传一个字段；数组字段传空数组会被拒绝。
 */
@Data
@Schema(description = "用户设置更新请求")
public class UserSettingUpdateDTO {

    @Min(value = 1, message = "主题非法")
    @Max(value = 3, message = "主题非法")
    @Schema(description = "主题：1 浅色，2 深色，3 跟随系统")
    private Integer theme;

    @Pattern(regexp = "zh-CN|en-US", message = "界面语言非法")
    @Schema(description = "界面语言：zh-CN / en-US")
    private String language;

    @Size(max = 7, message = "检查类别数量非法")
    @Schema(description = "重点检查类别，1-7 的不重复数组")
    private List<@Min(value = 1, message = "检查类别非法") @Max(value = 7, message = "检查类别非法")
            Integer> checkCategories;

    @Size(max = 50, message = "忽略目录不能超过 50 个")
    @Schema(description = "忽略目录；相对目录前缀，不允许以 / 开头或包含 ..")
    private List<@Size(min = 1, max = 100, message = "单个忽略目录长度必须在 1-100 之间")
            String> ignoreDirs;

    @Min(value = 1, message = "最大文件数不能小于 1")
    @Max(value = 2000, message = "最大文件数不能超过 2000")
    @Schema(description = "单次分析最大文件数")
    private Integer maxFileCount;

    @Min(value = 1, message = "最大 Diff 行数不能小于 1")
    @Max(value = 200000, message = "最大 Diff 行数不能超过 200000")
    @Schema(description = "单次分析最大 Diff 行数")
    private Integer maxDiffLines;
}
