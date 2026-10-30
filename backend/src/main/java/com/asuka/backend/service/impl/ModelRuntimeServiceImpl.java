package com.asuka.backend.service.impl;

import com.asuka.backend.common.constant.ModelProviderConstant;
import com.asuka.backend.common.constant.ModelSourceConstant;
import com.asuka.backend.common.crypto.SecretCipher;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.common.properties.AiProperties;
import com.asuka.backend.core.agent.ai.ModelRuntimeConfig;
import com.asuka.backend.core.agent.ai.ResolvedModel;
import com.asuka.backend.pojo.entity.AiModelConfig;
import com.asuka.backend.pojo.entity.AnalysisTask;
import com.asuka.backend.pojo.vo.ModelSnapshotVO;
import com.asuka.backend.service.ModelConfigService;
import com.asuka.backend.service.ModelRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 模型解析实现。
 *
 * <p>数据库配置的 Key 在服务端解密后只进入内存运行时配置；
 * 快照只保存 configId/displayName/provider/modelId/source。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRuntimeServiceImpl implements ModelRuntimeService {

    private final ModelConfigService modelConfigService;
    private final SecretCipher secretCipher;
    private final AiProperties aiProperties;

    @Override
    public ResolvedModel resolveForUser(Long userId) {
        return resolveDefaultOrEnv();
    }

    @Override
    public ResolvedModel resolveForGuest() {
        return resolveDefaultOrEnv();
    }

    @Override
    public ResolvedModel resolveForExecution(AnalysisTask task) {
        if (task != null && task.getModelConfigId() != null) {
            try {
                AiModelConfig config = modelConfigService.requireEnabled(task.getModelConfigId());
                return fromConfig(config, ModelSourceConstant.ORIGINAL_TASK);
            } catch (BusinessException ex) {
                log.warn("任务 {} 的原模型配置已停用，回退默认模型", task.getId());
            }
        }
        return resolveDefaultOrEnv();
    }

    @Override
    public ResolvedModel resolveForRetry(AnalysisTask original) {
        if (original != null && original.getModelConfigId() != null) {
            try {
                AiModelConfig config = modelConfigService.requireEnabled(original.getModelConfigId());
                return fromConfig(config, ModelSourceConstant.ORIGINAL_TASK);
            } catch (BusinessException ex) {
                log.info("重试任务 {} 的原模型已停用，回退当前默认模型", original.getId());
            }
        }
        return resolveDefaultOrEnv();
    }

    @Override
    public ModelSnapshotVO toSnapshot(ResolvedModel model) {
        if (model == null) {
            return null;
        }
        ModelSnapshotVO snapshot = new ModelSnapshotVO();
        snapshot.setConfigId(model.getModelConfigId());
        snapshot.setDisplayName(model.getDisplayName());
        snapshot.setProvider(model.getProvider());
        snapshot.setModelId(model.getModelId());
        snapshot.setSource(model.getSource());
        return snapshot;
    }

    private ResolvedModel resolveDefaultOrEnv() {
        AiModelConfig config = modelConfigService.findDefaultEnabled();
        if (config != null) {
            return fromConfig(config, ModelSourceConstant.ADMIN_DEFAULT);
        }
        return envFallback();
    }

    private ResolvedModel fromConfig(AiModelConfig config, String source) {
        String apiKey = secretCipher.decrypt(config.getApiKeyCiphertext());
        ModelRuntimeConfig runtime = ModelRuntimeConfig.builder()
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKey(apiKey)
                .modelId(config.getModelId())
                .maxOutputTokens(config.getMaxOutputTokens())
                .temperature(config.getTemperature())
                .connectTimeoutMs(aiProperties.getDeepseek().getConnectTimeoutMs())
                .readTimeoutMs(aiProperties.getDeepseek().getReadTimeoutMs())
                .sendThinking(false)
                .build();
        return ResolvedModel.builder()
                .modelConfigId(config.getId())
                .displayName(config.getDisplayName())
                .provider(config.getProvider())
                .modelId(config.getModelId())
                .source(source)
                .runtimeConfig(runtime)
                .build();
    }

    private ResolvedModel envFallback() {
        AiProperties.DeepSeek deepseek = aiProperties.getDeepseek();
        String provider = aiProperties.getProvider();
        if ("fake".equalsIgnoreCase(provider)) {
            ModelRuntimeConfig runtime = ModelRuntimeConfig.builder()
                    .provider(ModelProviderConstant.FAKE)
                    .baseUrl(null)
                    .apiKey("")
                    .modelId("fake")
                    .maxOutputTokens(0)
                    .temperature(BigDecimal.valueOf(0.1))
                    .connectTimeoutMs(deepseek.getConnectTimeoutMs())
                    .readTimeoutMs(deepseek.getReadTimeoutMs())
                    .sendThinking(false)
                    .build();
            return ResolvedModel.builder()
                    .displayName("Fake Provider")
                    .provider(ModelProviderConstant.FAKE)
                    .modelId("fake")
                    .source(ModelSourceConstant.ENV_FALLBACK)
                    .runtimeConfig(runtime)
                    .build();
        }
        if (!StringUtils.hasText(provider) || "deepseek".equalsIgnoreCase(provider)) {
            String modelId = StringUtils.hasText(deepseek.getModel())
                    ? deepseek.getModel() : "unknown-model";
            ModelRuntimeConfig runtime = ModelRuntimeConfig.builder()
                    .provider(ModelProviderConstant.OPENAI_CHAT)
                    .baseUrl(deepseek.getBaseUrl())
                    .apiKey(deepseek.getApiKey())
                    .modelId(modelId)
                    .maxOutputTokens(deepseek.getMaxTokens())
                    .temperature(BigDecimal.valueOf(deepseek.getTemperature()))
                    .connectTimeoutMs(deepseek.getConnectTimeoutMs())
                    .readTimeoutMs(deepseek.getReadTimeoutMs())
                    .sendThinking(true)
                    .build();
            return ResolvedModel.builder()
                    .displayName("环境兜底 " + modelId)
                    .provider(ModelProviderConstant.OPENAI_CHAT)
                    .modelId(modelId)
                    .source(ModelSourceConstant.ENV_FALLBACK)
                    .runtimeConfig(runtime)
                    .build();
        }
        throw new BusinessException(500, "环境模型 provider 配置错误");
    }
}
