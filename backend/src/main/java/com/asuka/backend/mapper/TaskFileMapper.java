package com.asuka.backend.mapper;

import com.asuka.backend.pojo.dto.StatusCountRow;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.pojo.vo.PriorityFileVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务文件 Mapper。
 *
 * <p>文件列表使用 MyBatis-Plus 条件构造器；
 * 覆盖统计和重点文件聚合使用 XML，避免在 Java 里拼 SQL。
 */
@Mapper
public interface TaskFileMapper extends BaseMapper<TaskFile> {

    /**
     * 按分析状态聚合文件数量，对应 F07 覆盖统计。
     */
    List<StatusCountRow> countByStatus(@Param("taskId") Long taskId);

    /**
     * 重点阅读文件，按关联问题数倒序，最多返回 limit 条。
     */
    List<PriorityFileVO> selectPriorityFiles(@Param("taskId") Long taskId, @Param("limit") int limit);
}
