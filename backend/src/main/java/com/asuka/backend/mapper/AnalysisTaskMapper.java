package com.asuka.backend.mapper;

import com.asuka.backend.pojo.dto.TaskListRow;
import com.asuka.backend.pojo.dto.TaskQueryDTO;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 分析任务 Mapper。
 *
 * <p>历史列表需要关联 pull_request 做仓库、PR 编号筛选和过期判断，
 * 因此使用 XML 原生 SQL；单表读写仍走 BaseMapper。
 */
@Mapper
public interface AnalysisTaskMapper extends BaseMapper<AnalysisTask> {

    /**
     * 当前用户的历史任务分页。
     *
     * @param page   分页参数
     * @param userId 当前登录用户
     * @param query  筛选条件
     */
    IPage<TaskListRow> selectTaskPage(Page<TaskListRow> page,
                                      @Param("userId") Long userId,
                                      @Param("query") TaskQueryDTO query);
}
