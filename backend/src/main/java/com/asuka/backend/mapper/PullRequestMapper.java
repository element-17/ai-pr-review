package com.asuka.backend.mapper;

import com.asuka.backend.pojo.entity.PullRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * PR 元数据 Mapper。
 */
@Mapper
public interface PullRequestMapper extends BaseMapper<PullRequest> {
}
