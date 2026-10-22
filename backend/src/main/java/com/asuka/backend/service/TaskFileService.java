package com.asuka.backend.service;

import com.asuka.backend.result.PageResult;
import com.asuka.backend.dto.TaskFileQueryDTO;
import com.asuka.backend.vo.TaskFileDetailVO;
import com.asuka.backend.vo.TaskFileDiffVO;
import com.asuka.backend.vo.TaskFileVO;

/**
 * 任务文件查询服务。
 */
public interface TaskFileService {

    /**
     * 文件清单，支持状态、变更类型、重点文件、路径关键字和排序。
     */
    PageResult<TaskFileVO> page(Long taskId, TaskFileQueryDTO query);

    /**
     * 文件详情，按需返回 patch、GitHub 链接和关联问题。
     */
    TaskFileDetailVO getDetail(Long taskId, Long fileId);

    /**
     * 结构化 Diff：解析 patch 行号，并把文件问题映射到对应新文件行。
     */
    TaskFileDiffVO getDiff(Long taskId, Long fileId);
}
