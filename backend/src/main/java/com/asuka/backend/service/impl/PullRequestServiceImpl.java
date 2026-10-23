package com.asuka.backend.service.impl;

import com.asuka.backend.service.PullRequestService;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.core.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.PullRequestMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * PR 元数据入库服务。
 *
 * <p>pull_request 是共享缓存表，按 repo_full_name + pr_number 唯一。
 * 每次预览/创建任务都会刷新当前状态和 head_sha，但不会改动历史任务的 head_sha。
 */
@Service
@RequiredArgsConstructor
public class PullRequestServiceImpl implements PullRequestService {

    private final PullRequestMapper pullRequestMapper;

    @Transactional(rollbackFor = Exception.class)
    public PullRequest upsert(String repoFullName, GitHubPrResponse response) {
        if (response == null || response.getHead() == null
                || !StringUtils.hasText(response.getHead().getSha())) {
            throw new BusinessException(502, "GitHub 返回的 PR 数据不完整");
        }

        PullRequest entity = pullRequestMapper.selectOne(new LambdaQueryWrapper<PullRequest>()
                .eq(PullRequest::getRepoFullName, repoFullName)
                .eq(PullRequest::getPrNumber, response.getNumber())
                .last("LIMIT 1"));
        boolean insert = entity == null;
        if (insert) {
            entity = new PullRequest();
            entity.setRepoFullName(repoFullName);
            entity.setPrNumber(response.getNumber());
        }

        entity.setTitle(defaultString(response.getTitle(), ""));
        entity.setAuthor(response.getUser() == null
                ? "" : defaultString(response.getUser().getLogin(), ""));
        entity.setState(resolveState(response));
        entity.setBaseBranch(response.getBase() == null
                ? "" : defaultString(response.getBase().getRef(), ""));
        entity.setHeadBranch(response.getHead() == null
                ? "" : defaultString(response.getHead().getRef(), ""));
        entity.setHeadSha(response.getHead().getSha());
        entity.setAddLines(response.getAdditions() == null ? 0 : response.getAdditions());
        entity.setDeleteLines(response.getDeletions() == null ? 0 : response.getDeletions());
        entity.setFileCount(response.getChangedFiles() == null ? 0 : response.getChangedFiles());

        if (insert) {
            pullRequestMapper.insert(entity);
        } else {
            pullRequestMapper.updateById(entity);
        }
        return entity;
    }

    /**
     * open=1，closed=2，merged=3。
     */
    private int resolveState(GitHubPrResponse response) {
        if (Boolean.TRUE.equals(response.getMerged())) {
            return 3;
        }
        if ("closed".equalsIgnoreCase(response.getState())) {
            return 2;
        }
        return 1;
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
