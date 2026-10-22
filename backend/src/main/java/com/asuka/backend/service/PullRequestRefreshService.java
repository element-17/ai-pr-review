package com.asuka.backend.service;

import com.asuka.backend.entity.PullRequest;
import com.asuka.backend.exception.GitHubApiException;
import com.asuka.backend.integration.github.GitHubClient;
import com.asuka.backend.integration.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.PullRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * PR 元数据 best-effort 刷新。
 *
 * <p>任务详情需要判断是否过期；这里用 60 秒 Redis 缓存限制 GitHub 调用，
 * 刷新失败时回退到最近一次缓存值，不影响历史报告查看。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PullRequestRefreshService {

    private final GitHubClient gitHubClient;
    private final PullRequestService pullRequestService;
    private final PullRequestMapper pullRequestMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public PullRequest refreshIfStale(Long prId) {
        PullRequest pr = pullRequestMapper.selectById(prId);
        if (pr == null) {
            return null;
        }
        String key = "pr:refresh:" + pr.getRepoFullName() + ":" + pr.getPrNumber();
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
                return pr;
            }
        } catch (Exception ex) {
            log.debug("读取 PR 刷新缓存失败：{}", pr.getRepoFullName());
        }

        try {
            String[] repo = pr.getRepoFullName().split("/", 2);
            GitHubPrResponse response = gitHubClient.getPullRequest(repo[0], repo[1], pr.getPrNumber());
            PullRequest updated = pullRequestService.upsert(pr.getRepoFullName(), response);
            try {
                stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(60));
            } catch (Exception ex) {
                log.debug("写入 PR 刷新缓存失败：{}", pr.getRepoFullName());
            }
            return updated;
        } catch (GitHubApiException ex) {
            log.debug("刷新 PR 元数据失败，使用缓存值：{}", pr.getRepoFullName());
            return pr;
        }
    }
}
