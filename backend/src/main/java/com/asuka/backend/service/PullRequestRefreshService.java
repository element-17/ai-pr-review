package com.asuka.backend.service;

import com.asuka.backend.pojo.entity.PullRequest;

/**
 * PR 元数据刷新服务。
 */
public interface PullRequestRefreshService {

    PullRequest refreshIfStale(Long prId);
}
