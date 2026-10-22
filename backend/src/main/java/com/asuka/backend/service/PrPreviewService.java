package com.asuka.backend.service;

import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.GitHubProperties;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.core.integration.github.GitHubClient;
import com.asuka.backend.core.integration.github.GitHubErrorMapper;
import com.asuka.backend.core.integration.github.GitHubPrUrlParser;
import com.asuka.backend.core.integration.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.pojo.vo.PrPreviewVO;
import com.asuka.backend.pojo.vo.PrVO;
import com.asuka.backend.pojo.vo.ReusableTaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * PR 预览服务。
 *
 * <p>预览不落任务，只刷新 pull_request 元数据并返回 PR 信息；
 * 登录用户额外查询同版本已完成/部分完成的任务，提示可复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrPreviewService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GitHubClient gitHubClient;
    private final PullRequestService pullRequestService;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final TaskAccessService taskAccessService;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate stringRedisTemplate;
    private final GitHubProperties gitHubProperties;

    public PrPreviewVO preview(String url) {
        rateLimitService.checkPreview();
        GitHubPrUrlParser.PrUrl ref = GitHubPrUrlParser.parse(url);

        GitHubPrResponse response;
        try {
            response = loadPullRequest(ref);
        } catch (com.asuka.backend.common.exception.GitHubApiException ex) {
            throw GitHubErrorMapper.toBusinessException(ex);
        }

        PullRequest pr = pullRequestService.upsert(ref.repoFullName(), response);
        Long userId = taskAccessService.requireOptionalLogin();

        PrPreviewVO vo = new PrPreviewVO();
        vo.setPr(toPrVO(pr));
        if (userId != null) {
            vo.setReusableTask(findReusableTask(userId, pr));
        }
        return vo;
    }

    /**
     * 预览缓存 30–60 秒；任务创建时必须重新拉取 head SHA。
     */
    private GitHubPrResponse loadPullRequest(GitHubPrUrlParser.PrUrl ref) {
        String key = "pr:preview:" + ref.repoFullName() + ":" + ref.number();
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.hasText(cached)) {
                return OBJECT_MAPPER.readValue(cached, GitHubPrResponse.class);
            }
        } catch (Exception ex) {
            log.debug("读取 PR 预览缓存失败：{}", ref.repoFullName());
        }

        GitHubPrResponse response = gitHubClient.getPullRequest(ref.owner(), ref.repo(), ref.number());
        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(key, json,
                    Duration.ofSeconds(Math.max(30, gitHubProperties.getPreviewCacheSeconds())));
        } catch (Exception ex) {
            log.debug("写入 PR 预览缓存失败：{}", ref.repoFullName());
        }
        return response;
    }

    private ReusableTaskVO findReusableTask(Long userId, PullRequest pr) {
        AnalysisTask task = analysisTaskMapper.selectOne(new LambdaQueryWrapper<AnalysisTask>()
                .eq(AnalysisTask::getUserId, userId)
                .eq(AnalysisTask::getPrId, pr.getId())
                .eq(AnalysisTask::getHeadSha, pr.getHeadSha())
                .in(AnalysisTask::getStatus, 3, 4)
                .orderByDesc(AnalysisTask::getCreateTime)
                .last("LIMIT 1"));
        if (task == null) {
            return null;
        }
        ReusableTaskVO vo = new ReusableTaskVO();
        vo.setTaskId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setReportLanguage(task.getReportLanguage());
        vo.setCreateTime(task.getCreateTime());
        vo.setFinishTime(task.getFinishTime());
        return vo;
    }

    private PrVO toPrVO(PullRequest pr) {
        PrVO vo = new PrVO();
        vo.setRepoFullName(pr.getRepoFullName());
        vo.setPrNumber(pr.getPrNumber());
        vo.setTitle(pr.getTitle());
        vo.setAuthor(pr.getAuthor());
        vo.setState(pr.getState());
        vo.setBaseBranch(pr.getBaseBranch());
        vo.setHeadBranch(pr.getHeadBranch());
        vo.setHeadSha(pr.getHeadSha());
        vo.setAddLines(pr.getAddLines());
        vo.setDeleteLines(pr.getDeleteLines());
        vo.setFileCount(pr.getFileCount());
        return vo;
    }
}
