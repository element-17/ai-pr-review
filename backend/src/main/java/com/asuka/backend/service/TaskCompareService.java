package com.asuka.backend.service;

import com.asuka.backend.pojo.vo.TaskCompareVO;

/**
 * 同 PR 版本对比服务。
 */
public interface TaskCompareService {

    TaskCompareVO compare(Long baseTaskId, Long targetTaskId);
}
