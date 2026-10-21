package com.asuka.backend.service;

import com.asuka.backend.dto.GeneratedIssueDTO;
import com.asuka.backend.dto.ReviewIssueQueryDTO;
import com.asuka.backend.dto.ReviewIssueUpdateDTO;
import com.asuka.backend.vo.IssueDetailVO;
import com.asuka.backend.vo.IssueListVO;

import java.util.List;

/**
 * 评审问题服务。
 */
public interface ReviewIssueService {

    /**
     * 问题组合筛选、排序、搜索和分页。
     */
    IssueListVO page(Long taskId, ReviewIssueQueryDTO query);

    /**
     * 问题详情。
     */
    IssueDetailVO getDetail(Long taskId, Long issueId);

    /**
     * 更新处理状态、理由或建议。
     */
    IssueDetailVO update(Long taskId, Long issueId, ReviewIssueUpdateDTO dto);

    /**
     * 撤销人工处理：状态回到待确认，理由清空，建议恢复模型原文。
     */
    IssueDetailVO undo(Long taskId, Long issueId);

    /**
     * 校验并保存 AI 分析产出的候选问题。
     *
     * <p>这是 F08/F12 分析结果入库的公共入口，不对外暴露 HTTP 接口。
     *
     * @return 实际入库数量（重复问题会被合并）
     */
    int saveGeneratedIssues(Long taskId, List<GeneratedIssueDTO> issues);
}
