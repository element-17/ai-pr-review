package com.asuka.backend.service;

import com.asuka.backend.pojo.dto.UserSettingUpdateDTO;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.asuka.backend.pojo.vo.UserSettingVO;

/**
 * 用户设置服务。
 */
public interface UserSettingService {

    /**
     * 读取设置；没有设置行时返回默认值。
     */
    UserSettingVO getSettings(Long userId);

    /**
     * 部分更新设置；没有设置行时先创建。
     */
    UserSettingVO updateSettings(Long userId, UserSettingUpdateDTO dto);

    /**
     * 注册后初始化默认设置。
     */
    void initDefault(Long userId);

    /**
     * 生成任务创建时使用的非敏感配置快照，供 F03 分析任务接入。
     */
    AnalysisConfigVO buildAnalysisConfig(Long userId);

    /**
     * 游客任务使用的默认非敏感配置。
     */
    AnalysisConfigVO buildGuestAnalysisConfig();
}
