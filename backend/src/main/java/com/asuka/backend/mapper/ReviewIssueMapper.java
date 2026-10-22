package com.asuka.backend.mapper;

import com.asuka.backend.pojo.dto.IssueExportRow;
import com.asuka.backend.pojo.dto.IssueQueryParam;
import com.asuka.backend.pojo.dto.StatusCountRow;
import com.asuka.backend.pojo.entity.ReviewIssue;
import com.asuka.backend.pojo.vo.IssueBriefVO;
import com.asuka.backend.pojo.vo.IssueVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 评审问题 Mapper。
 *
 * <p>问题列表要做文件关联、关键词搜索、组合筛选和排序，使用 XML 原生 SQL；
 * 单条更新和插入仍走 MyBatis-Plus。
 */
@Mapper
public interface ReviewIssueMapper extends BaseMapper<ReviewIssue> {

    /**
     * 问题组合筛选分页。
     */
    IPage<IssueVO> selectIssuePage(Page<IssueVO> page,
                                   @Param("taskId") Long taskId,
                                   @Param("param") IssueQueryParam param);

    /**
     * 按处理状态聚合，用于任务级处理进度。
     */
    List<StatusCountRow> countByFeedbackStatus(@Param("taskId") Long taskId);

    /**
     * 按严重度聚合，用于风险分布。
     */
    List<StatusCountRow> countBySeverity(@Param("taskId") Long taskId);

    /**
     * 查询某个文件上的问题摘要，供 Diff 行标记使用。
     */
    List<IssueBriefVO> selectBriefByFile(@Param("taskId") Long taskId, @Param("fileId") Long fileId);

    /**
     * 导出 Markdown 时读取完整问题清单。
     */
    List<IssueExportRow> selectForExport(@Param("taskId") Long taskId);
}
