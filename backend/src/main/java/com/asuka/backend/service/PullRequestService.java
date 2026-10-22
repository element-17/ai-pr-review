package com.asuka.backend.service;

import com.asuka.backend.core.github.dto.GitHubPrResponse;
import com.asuka.backend.pojo.entity.PullRequest;

/**
 * PR 元数据持久化服务。
 */
public interface PullRequestService {

    PullRequest upsert(String repoFullName, GitHubPrResponse response);
}
