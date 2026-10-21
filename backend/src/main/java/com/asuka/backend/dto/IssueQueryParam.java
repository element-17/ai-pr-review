package com.asuka.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 问题查询的内部参数。
 *
 * <p>把前端传入的逗号分隔字符串解析成列表，避免在 Mapper XML 中做字符串拆分。
 */
@Data
public class IssueQueryParam {

    private List<Integer> severities;

    private List<Integer> categories;

    private List<Integer> feedbackStatuses;

    private Long fileId;

    private String keyword;

    private String sort;
}
