package com.asuka.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户设置返回。
 */
@Data
@Schema(description = "用户设置")
public class UserSettingVO {

    @Schema(description = "主题：1 浅色，2 深色，3 跟随系统", example = "3")
    private Integer theme;

    @Schema(description = "界面语言：zh-CN / en-US", example = "zh-CN")
    private String language;

    @Schema(description = "重点检查类别")
    private List<Integer> checkCategories;

    @Schema(description = "忽略目录")
    private List<String> ignoreDirs;

    @Schema(description = "单次分析最大文件数", example = "200")
    private Integer maxFileCount;

    @Schema(description = "单次分析最大 Diff 行数", example = "20000")
    private Integer maxDiffLines;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间；从未保存过设置为空")
    private LocalDateTime updateTime;
}
