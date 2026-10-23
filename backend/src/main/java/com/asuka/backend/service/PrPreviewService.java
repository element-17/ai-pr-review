package com.asuka.backend.service;

import com.asuka.backend.pojo.vo.PrPreviewVO;

/**
 * GitHub PR 预览服务。
 */
public interface PrPreviewService {

    PrPreviewVO preview(String url);
}
