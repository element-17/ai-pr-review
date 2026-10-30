package com.asuka.backend.service.impl;

import com.asuka.backend.common.constant.ModelProviderConstant;
import com.asuka.backend.common.crypto.SecretCipher;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.AiProperties;
import com.asuka.backend.common.util.BaseUrlValidator;
import com.asuka.backend.core.agent.ai.ModelProbeService;
import com.asuka.backend.core.agent.ai.ModelRuntimeConfig;
import com.asuka.backend.mapper.AiModelConfigMapper;
import com.asuka.backend.pojo.dto.ModelConfigCreateDTO;
import com.asuka.backend.pojo.dto.ModelConfigTestDTO;
import com.asuka.backend.pojo.dto.ModelConfigUpdateDTO;
import com.asuka.backend.pojo.entity.AiModelConfig;
import com.asuka.backend.pojo.vo.ModelConfigVO;
import com.asuka.backend.pojo.vo.ModelOptionVO;
import com.asuka.backend.pojo.vo.ModelTestResultVO;
import com.asuka.backend.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型配置服务实现。
 *
 * <p>API Key 只以 AES-GCM 密文保存，列表/详情返回末 4 位掩码；
 * 默认模型始终只保留一个，停用默认模型时自动提升另一个 enabled 模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    private final SecretCipher secretCipher;
    private final ModelProbeService modelProbeService;
    private final AiProperties aiProperties;

    @Override
    public List<ModelConfigVO> list(Boolean enabled) {
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(AiModelConfig::getEnabled, enabled ? 1 : 0);
        }
        wrapper.orderByDesc(AiModelConfig::getIsDefault)
                .orderByAsc(AiModelConfig::getId);
        List<AiModelConfig> configs = modelConfigMapper.selectList(wrapper);
        return configs.stream().map(this::toVO).toList();
    }

    @Override
    public ModelConfigVO get(Long id) {
        return toVO(requireConfig(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ModelConfigCreateDTO dto) {
        validateProviderAndUrl(dto.getProvider(), dto.getBaseUrl());
        boolean enabled = dto.getEnabled() == null || dto.getEnabled();
        boolean requestDefault = Boolean.TRUE.equals(dto.getIsDefault());
        if (requestDefault && !enabled) {
            throw new BusinessException(400, "停用的模型不能设为默认模型");
        }

        boolean shouldDefault = requestDefault;
        if (!shouldDefault && enabled && countEnabledDefaults() == 0) {
            // 第一个启用的模型自动成为默认，避免任务无处可去
            shouldDefault = true;
        }
        if (shouldDefault) {
            clearDefaults();
        }

        AiModelConfig config = new AiModelConfig();
        config.setDisplayName(dto.getDisplayName().trim());
        config.setProvider(dto.getProvider());
        config.setBaseUrl(normalizeUrl(dto.getBaseUrl()));
        config.setModelId(dto.getModelId().trim());
        config.setApiKeyCiphertext(secretCipher.encrypt(dto.getApiKey().trim()));
        config.setApiKeyHint(mask(dto.getApiKey().trim()));
        config.setMaxOutputTokens(dto.getMaxOutputTokens());
        config.setTemperature(dto.getTemperature());
        config.setEnabled(enabled ? 1 : 0);
        config.setIsDefault(shouldDefault ? 1 : 0);
        modelConfigMapper.insert(config);
        log.info("管理员新增模型配置：id={}, provider={}", config.getId(), config.getProvider());
        return config.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ModelConfigUpdateDTO dto) {
        AiModelConfig config = requireConfig(id);
        if (StringUtils.hasText(dto.getProvider())) {
            validateProviderAndUrl(dto.getProvider(),
                    StringUtils.hasText(dto.getBaseUrl()) ? dto.getBaseUrl() : config.getBaseUrl());
            config.setProvider(dto.getProvider());
        } else if (StringUtils.hasText(dto.getBaseUrl())) {
            validateProviderAndUrl(config.getProvider(), dto.getBaseUrl());
        }
        if (StringUtils.hasText(dto.getDisplayName())) {
            config.setDisplayName(dto.getDisplayName().trim());
        }
        if (StringUtils.hasText(dto.getBaseUrl())) {
            config.setBaseUrl(normalizeUrl(dto.getBaseUrl()));
        }
        if (StringUtils.hasText(dto.getModelId())) {
            config.setModelId(dto.getModelId().trim());
        }
        if (StringUtils.hasText(dto.getApiKey())) {
            config.setApiKeyCiphertext(secretCipher.encrypt(dto.getApiKey().trim()));
            config.setApiKeyHint(mask(dto.getApiKey().trim()));
        }
        if (dto.getMaxOutputTokens() != null) {
            config.setMaxOutputTokens(dto.getMaxOutputTokens());
        }
        if (dto.getTemperature() != null) {
            config.setTemperature(dto.getTemperature());
        }
        modelConfigMapper.updateById(config);
        log.info("管理员更新模型配置：id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, boolean enabled) {
        AiModelConfig config = requireConfig(id);
        config.setEnabled(enabled ? 1 : 0);
        boolean wasDefault = config.getIsDefault() != null && config.getIsDefault() == 1;
        if (!enabled && wasDefault) {
            config.setIsDefault(0);
        }
        modelConfigMapper.updateById(config);

        if (!enabled && wasDefault) {
            promoteFirstEnabled(id);
        } else if (enabled && countEnabledDefaults() == 0) {
            clearDefaults();
            config.setIsDefault(1);
            modelConfigMapper.updateById(config);
        }
        log.info("管理员{}模型配置：id={}", enabled ? "启用" : "停用", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        AiModelConfig config = requireConfig(id);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException(400, "停用的模型不能设为默认模型");
        }
        clearDefaults();
        config.setIsDefault(1);
        modelConfigMapper.updateById(config);
        log.info("管理员设置默认模型：id={}", id);
    }

    @Override
    public ModelTestResultVO testSaved(Long id) {
        AiModelConfig config = requireConfig(id);
        BaseUrlValidator.validate(config.getBaseUrl(), aiProperties.isAllowPrivateBaseUrl());
        String apiKey = secretCipher.decrypt(config.getApiKeyCiphertext());
        return modelProbeService.test(runtimeConfig(config, apiKey, true));
    }

    @Override
    public ModelTestResultVO testDraft(ModelConfigTestDTO dto) {
        validateProviderAndUrl(dto.getProvider(), dto.getBaseUrl());
        ModelRuntimeConfig runtime = ModelRuntimeConfig.builder()
                .provider(dto.getProvider())
                .baseUrl(normalizeUrl(dto.getBaseUrl()))
                .apiKey(dto.getApiKey().trim())
                .modelId(dto.getModelId().trim())
                .maxOutputTokens(dto.getMaxOutputTokens())
                .temperature(dto.getTemperature())
                .connectTimeoutMs(aiProperties.getDeepseek().getConnectTimeoutMs())
                .readTimeoutMs(Math.min(15000, aiProperties.getDeepseek().getReadTimeoutMs()))
                .sendThinking(false)
                .build();
        return modelProbeService.test(runtime);
    }

    @Override
    public List<ModelOptionVO> listEnabledOptions() {
        return modelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .eq(AiModelConfig::getEnabled, 1)
                        .orderByDesc(AiModelConfig::getIsDefault)
                        .orderByAsc(AiModelConfig::getId))
                .stream().map(this::toOption).toList();
    }

    @Override
    public AiModelConfig requireEnabled(Long id) {
        AiModelConfig config = requireConfig(id);
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BusinessException(400, "模型不存在或已停用");
        }
        return config;
    }

    @Override
    public AiModelConfig findDefaultEnabled() {
        return modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1)
                .eq(AiModelConfig::getIsDefault, 1)
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT 1"));
    }

    private AiModelConfig requireConfig(Long id) {
        if (id == null) {
            throw new BusinessException(400, "模型配置 ID 不能为空");
        }
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "模型配置不存在");
        }
        return config;
    }

    private void validateProviderAndUrl(String provider, String baseUrl) {
        if (!ModelProviderConstant.isValid(provider)) {
            throw new BusinessException(400, "provider 只能是 OPENAI_CHAT 或 ANTHROPIC_MESSAGES");
        }
        BaseUrlValidator.validate(baseUrl, aiProperties.isAllowPrivateBaseUrl());
    }

    private String normalizeUrl(String baseUrl) {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String mask(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    private long countEnabledDefaults() {
        return modelConfigMapper.selectCount(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1)
                .eq(AiModelConfig::getIsDefault, 1));
    }

    private void clearDefaults() {
        modelConfigMapper.update(null, new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getIsDefault, 1)
                .set(AiModelConfig::getIsDefault, 0));
    }

    private void promoteFirstEnabled(Long excludedId) {
        AiModelConfig next = modelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getEnabled, 1)
                .ne(AiModelConfig::getId, excludedId)
                .orderByAsc(AiModelConfig::getId)
                .last("LIMIT 1"));
        if (next != null) {
            next.setIsDefault(1);
            modelConfigMapper.updateById(next);
        }
    }

    private ModelRuntimeConfig runtimeConfig(AiModelConfig config, String apiKey, boolean shortTimeout) {
        BigDecimal temperature = config.getTemperature();
        return ModelRuntimeConfig.builder()
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .modelId(config.getModelId())
                .maxOutputTokens(config.getMaxOutputTokens())
                .temperature(temperature)
                .connectTimeoutMs(aiProperties.getDeepseek().getConnectTimeoutMs())
                .readTimeoutMs(shortTimeout ? Math.min(15000, aiProperties.getDeepseek().getReadTimeoutMs())
                        : aiProperties.getDeepseek().getReadTimeoutMs())
                .sendThinking(false)
                .build();
    }

    private ModelConfigVO toVO(AiModelConfig config) {
        ModelConfigVO vo = new ModelConfigVO();
        vo.setId(config.getId());
        vo.setDisplayName(config.getDisplayName());
        vo.setProvider(config.getProvider());
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModelId(config.getModelId());
        vo.setApiKeyConfigured(StringUtils.hasText(config.getApiKeyCiphertext()));
        vo.setApiKeyHint(config.getApiKeyHint());
        vo.setMaxOutputTokens(config.getMaxOutputTokens());
        vo.setTemperature(config.getTemperature());
        vo.setEnabled(config.getEnabled() != null && config.getEnabled() == 1);
        vo.setIsDefault(config.getIsDefault() != null && config.getIsDefault() == 1);
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

    private ModelOptionVO toOption(AiModelConfig config) {
        ModelOptionVO vo = new ModelOptionVO();
        vo.setId(config.getId());
        vo.setDisplayName(config.getDisplayName());
        vo.setProvider(config.getProvider());
        vo.setIsDefault(config.getIsDefault() != null && config.getIsDefault() == 1);
        return vo;
    }
}
