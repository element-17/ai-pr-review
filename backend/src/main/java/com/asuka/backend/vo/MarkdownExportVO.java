package com.asuka.backend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Markdown 导出结果。
 *
 * <p>Controller 直接把 content 写入响应体，filename 放到 Content-Disposition。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkdownExportVO {

    private String filename;

    private String content;
}
