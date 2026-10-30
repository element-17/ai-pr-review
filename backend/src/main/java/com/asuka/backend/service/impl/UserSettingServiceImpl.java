package com.asuka.backend.service.impl;

import com.asuka.backend.common.cache.CacheNames;
import com.asuka.backend.common.constant.ReportLanguageConstant;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.pojo.dto.UserSettingUpdateDTO;
import com.asuka.backend.pojo.entity.UserSetting;
import com.asuka.backend.mapper.UserSettingMapper;
import com.asuka.backend.service.UserSettingService;
import com.asuka.backend.pojo.vo.AnalysisConfigVO;
import com.asuka.backend.pojo.vo.UserSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 用户设置服务实现。
 *
 * <p>默认值集中在这里维护；设置行采用懒创建，注册时会主动初始化，
 * 老账号第一次读取时也能拿到默认值而不会报错。
 */
@Service
@RequiredArgsConstructor
public class UserSettingServiceImpl implements UserSettingService {

    private static final int DEFAULT_THEME = 3;
    private static final String DEFAULT_LANGUAGE = ReportLanguageConstant.ZH_CN;
    private static final String DEFAULT_CHECK_CATEGORIES = "1,2,3,4,5,6";
    private static final String DEFAULT_IGNORE_DIRS = "node_modules,dist,target,.git";
    private static final int DEFAULT_MAX_FILE_COUNT = 200;
    private static final int DEFAULT_MAX_DIFF_LINES = 20000;

    private final UserSettingMapper userSettingMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.USER_SETTING, key = "#userId", sync = true)
    public UserSettingVO getSettings(Long userId) {
        UserSetting setting = userSettingMapper.selectById(userId);
        if (setting == null) {
            setting = defaultSetting(userId);
        }
        return toVO(setting);
    }

    @Override
    @CacheEvict(cacheNames = {CacheNames.USER_SETTING, CacheNames.USER_SETTING_CONFIG}, key = "#userId")
    public UserSettingVO updateSettings(Long userId, UserSettingUpdateDTO dto) {
        if (dto == null || (dto.getTheme() == null && dto.getLanguage() == null
                && dto.getCheckCategories() == null && dto.getIgnoreDirs() == null
                && dto.getMaxFileCount() == null && dto.getMaxDiffLines() == null)) {
            throw new BusinessException(400, "至少需要提交一个设置项");
        }

        UserSetting setting = userSettingMapper.selectById(userId);
        boolean insert = setting == null;
        if (insert) {
            setting = defaultSetting(userId);
        }

        if (dto.getTheme() != null) {
            setting.setTheme(dto.getTheme());
        }
        if (dto.getLanguage() != null) {
            if (!ReportLanguageConstant.isValid(dto.getLanguage())) {
                throw new BusinessException(400, "界面语言只能是 zh-CN 或 en-US");
            }
            setting.setLanguage(dto.getLanguage());
        }
        if (dto.getCheckCategories() != null) {
            setting.setCheckCategories(joinCheckCategories(dto.getCheckCategories()));
        }
        if (dto.getIgnoreDirs() != null) {
            setting.setIgnoreDirs(joinIgnoreDirs(dto.getIgnoreDirs()));
        }
        if (dto.getMaxFileCount() != null) {
            setting.setMaxFileCount(dto.getMaxFileCount());
        }
        if (dto.getMaxDiffLines() != null) {
            setting.setMaxDiffLines(dto.getMaxDiffLines());
        }

        if (insert) {
            userSettingMapper.insert(setting);
        } else {
            userSettingMapper.updateById(setting);
        }
        return toVO(userSettingMapper.selectById(userId));
    }

    @Override
    @CacheEvict(cacheNames = {CacheNames.USER_SETTING, CacheNames.USER_SETTING_CONFIG}, key = "#userId")
    public void initDefault(Long userId) {
        if (userSettingMapper.selectById(userId) == null) {
            userSettingMapper.insert(defaultSetting(userId));
        }
    }

    @Override
    @Cacheable(cacheNames = CacheNames.USER_SETTING_CONFIG, key = "#userId", sync = true)
    public AnalysisConfigVO buildAnalysisConfig(Long userId) {
        return toConfig(getSettings(userId));
    }

    @Override
    public AnalysisConfigVO buildGuestAnalysisConfig() {
        return toConfig(toVO(defaultSetting(null)));
    }

    private AnalysisConfigVO toConfig(UserSettingVO setting) {
        AnalysisConfigVO config = new AnalysisConfigVO();
        config.setCheckCategories(setting.getCheckCategories());
        config.setIgnoreDirs(setting.getIgnoreDirs());
        config.setMaxFileCount(setting.getMaxFileCount());
        config.setMaxDiffLines(setting.getMaxDiffLines());
        return config;
    }

    /**
     * 构造默认设置；用于没有设置行的老账号和注册初始化。
     */
    private UserSetting defaultSetting(Long userId) {
        UserSetting setting = new UserSetting();
        setting.setUserId(userId);
        setting.setTheme(DEFAULT_THEME);
        setting.setLanguage(DEFAULT_LANGUAGE);
        setting.setCheckCategories(DEFAULT_CHECK_CATEGORIES);
        setting.setIgnoreDirs(DEFAULT_IGNORE_DIRS);
        setting.setMaxFileCount(DEFAULT_MAX_FILE_COUNT);
        setting.setMaxDiffLines(DEFAULT_MAX_DIFF_LINES);
        return setting;
    }

    /**
     * 检查类别：去重、升序，拒绝空数组和越界值。
     */
    private String joinCheckCategories(List<Integer> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new BusinessException(400, "重点检查类别不能为空");
        }
        Set<Integer> unique = new TreeSet<>();
        for (Integer category : categories) {
            if (category == null || category < 1 || category > 7) {
                throw new BusinessException(400, "重点检查类别必须在 1-7 之间");
            }
            unique.add(category);
        }
        return unique.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }

    /**
     * 忽略目录：去重、保留顺序，只允许相对目录前缀。
     */
    private String joinIgnoreDirs(List<String> ignoreDirs) {
        if (ignoreDirs == null || ignoreDirs.isEmpty()) {
            throw new BusinessException(400, "忽略目录不能为空");
        }
        if (ignoreDirs.size() > 50) {
            throw new BusinessException(400, "忽略目录不能超过 50 个");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String raw : ignoreDirs) {
            if (!StringUtils.hasText(raw)) {
                throw new BusinessException(400, "忽略目录不能包含空值");
            }
            String dir = raw.trim().replace('\\', '/');
            if (dir.length() > 100) {
                throw new BusinessException(400, "单个忽略目录不能超过 100 个字符");
            }
            if (dir.startsWith("/") || dir.contains("..") || dir.matches("^[A-Za-z]:.*")) {
                throw new BusinessException(400, "忽略目录必须是相对目录前缀");
            }
            unique.add(dir);
        }
        return String.join(",", unique);
    }

    /**
     * 实体转 VO，逗号分隔文本转数组。
     */
    private UserSettingVO toVO(UserSetting setting) {
        UserSettingVO vo = new UserSettingVO();
        vo.setTheme(setting.getTheme());
        vo.setLanguage(setting.getLanguage());
        vo.setCheckCategories(parseIntList(setting.getCheckCategories()));
        vo.setIgnoreDirs(parseStringList(setting.getIgnoreDirs()));
        vo.setMaxFileCount(setting.getMaxFileCount());
        vo.setMaxDiffLines(setting.getMaxDiffLines());
        vo.setUpdateTime(setting.getUpdateTime());
        return vo;
    }

    private List<Integer> parseIntList(String raw) {
        List<Integer> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                try {
                    result.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 历史脏数据直接忽略，避免设置接口整体不可用
                }
            }
        }
        return result;
    }

    private List<String> parseStringList(String raw) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
