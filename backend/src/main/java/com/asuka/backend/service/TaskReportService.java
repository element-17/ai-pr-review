package com.asuka.backend.service;

import com.asuka.backend.vo.MarkdownExportVO;
import com.asuka.backend.vo.ReportVO;

/**
 * 分析报告服务。
 */
public interface TaskReportService {

    /**
     * 报告总结与覆盖范围。
     */
    ReportVO getReport(Long taskId);

    /**
     * 生成 Markdown 报告。
     *
     * @param format 当前只支持 markdown
     */
    MarkdownExportVO exportMarkdown(Long taskId, String format);
}
