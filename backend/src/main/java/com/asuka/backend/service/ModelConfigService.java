package com.asuka.backend.service;

import com.asuka.backend.pojo.dto.ModelConfigCreateDTO;
import com.asuka.backend.pojo.dto.ModelConfigTestDTO;
import com.asuka.backend.pojo.dto.ModelConfigUpdateDTO;
import com.asuka.backend.pojo.entity.AiModelConfig;
import com.asuka.backend.pojo.vo.ModelConfigVO;
import com.asuka.backend.pojo.vo.ModelOptionVO;
import com.asuka.backend.pojo.vo.ModelTestResultVO;

import java.util.List;

/**
 * 管理员模型配置与安全模型选项读取。
 */
public interface ModelConfigService {

    List<ModelConfigVO> list(Boolean enabled);

    ModelConfigVO get(Long id);

    Long create(ModelConfigCreateDTO dto);

    void update(Long id, ModelConfigUpdateDTO dto);

    void changeStatus(Long id, boolean enabled);

    void setDefault(Long id);

    ModelTestResultVO testSaved(Long id);

    ModelTestResultVO testDraft(ModelConfigTestDTO dto);

    List<ModelOptionVO> listEnabledOptions();

    AiModelConfig requireEnabled(Long id);

    AiModelConfig findDefaultEnabled();
}
