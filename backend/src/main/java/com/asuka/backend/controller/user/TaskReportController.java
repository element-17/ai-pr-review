package com.asuka.backend.controller.user;

import com.asuka.backend.common.result.Result;
import com.asuka.backend.service.TaskReportService;
import com.asuka.backend.vo.MarkdownExportVO;
import com.asuka.backend.vo.ReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 分析报告与 Markdown 导出接口。
 */
@Tag(name = "分析报告")
@RestController
@RequestMapping("/api/tasks/{taskId}")
@RequiredArgsConstructor
@Validated
public class TaskReportController {

    private final TaskReportService taskReportService;

    @Operation(summary = "报告总结", description = "返回总结、覆盖统计、模型和非敏感配置")
    @GetMapping("/report")
    public Result<ReportVO> report(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId) {
        return Result.success(taskReportService.getReport(taskId));
    }

    @Operation(summary = "导出 Markdown", description = "导出内容沿用任务创建时固定的报告语言")
    @GetMapping("/export")
    public ResponseEntity<String> export(
            @Parameter(description = "任务 ID") @PathVariable @Min(value = 1, message = "任务 ID 非法")
            Long taskId,
            @Parameter(description = "导出格式，当前只支持 markdown")
            @RequestParam(required = false, defaultValue = "markdown") String format) {
        MarkdownExportVO export = taskReportService.exportMarkdown(taskId, format);
        MediaType mediaType = new MediaType("text", "markdown", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + export.getFilename() + "\"")
                .body(export.getContent());
    }
}
