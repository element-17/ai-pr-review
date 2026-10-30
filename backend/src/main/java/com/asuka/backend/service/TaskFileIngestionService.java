package com.asuka.backend.service;

import com.asuka.backend.core.github.GitHubAuth;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.entity.PullRequest;
import com.asuka.backend.pojo.entity.TaskFile;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;

import java.util.List;

/**
 * PR 文件变更入库服务。
 */
public interface TaskFileIngestionService {

    List<TaskFile> ingest(AnalysisTask task, PullRequest pr, AnalysisConfigVO config, GitHubAuth auth);
}
