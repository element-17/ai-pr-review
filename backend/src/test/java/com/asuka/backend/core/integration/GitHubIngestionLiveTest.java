package com.asuka.backend.core.integration;

import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.core.integration.github.GitHubClient;
import com.asuka.backend.core.integration.github.dto.GitHubPrResponse;
import com.asuka.backend.mapper.AnalysisTaskMapper;
import com.asuka.backend.mapper.PullRequestMapper;
import com.asuka.backend.mapper.TaskFileMapper;
import com.asuka.backend.service.PullRequestService;
import com.asuka.backend.service.TaskFileIngestionService;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 真实 GitHub 文件拉取验证。
 *
 * <p>默认跳过；需要网络时用 GITHUB_LIVE_TEST=true 运行：
 * .\mvnw.cmd -q -Dtest=GitHubIngestionLiveTest test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "GITHUB_LIVE_TEST", matches = "true")
class GitHubIngestionLiveTest {

    @Autowired
    private GitHubClient gitHubClient;

    @Autowired
    private PullRequestService pullRequestService;

    @Autowired
    private TaskFileIngestionService taskFileIngestionService;

    @Autowired
    private AnalysisTaskMapper analysisTaskMapper;

    @Autowired
    private PullRequestMapper pullRequestMapper;

    @Autowired
    private TaskFileMapper taskFileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestFilesFromPublicPr() {
        GitHubPrResponse response = gitHubClient.getPullRequest("octocat", "Hello-World", 11596);
        PullRequest pr = pullRequestService.upsert("octocat/Hello-World", response);

        AnalysisTask task = new AnalysisTask();
        task.setPrId(pr.getId());
        task.setHeadSha(response.getHead().getSha());
        task.setReportLanguage("zh-CN");
        task.setStatus(1);
        task.setStage(1);
        task.setProgress(0);
        task.setModelName("deepseek-flash");
        task.setIsDeleted(0);
        analysisTaskMapper.insert(task);

        try {
            AnalysisConfigVO config = new AnalysisConfigVO();
            config.setMaxFileCount(200);
            config.setMaxDiffLines(20000);
            List<TaskFile> files = taskFileIngestionService.ingest(task, pr, config);

            assertFalse(files.isEmpty(), "公开 PR 应至少有一个变更文件");
            Long count = taskFileMapper.selectCount(new LambdaQueryWrapper<TaskFile>()
                    .eq(TaskFile::getTaskId, task.getId()));
            assertEquals(files.size(), count.intValue(), "任务文件应全部落库");
        } finally {
            jdbcTemplate.update("DELETE FROM task_file WHERE task_id = ?", task.getId());
            jdbcTemplate.update("DELETE FROM analysis_task WHERE id = ?", task.getId());
            jdbcTemplate.update("DELETE FROM pull_request WHERE id = ?", pr.getId());
        }
    }
}
