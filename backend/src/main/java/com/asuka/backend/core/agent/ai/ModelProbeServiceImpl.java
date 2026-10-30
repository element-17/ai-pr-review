package com.asuka.backend.core.agent.ai;

import com.asuka.backend.common.constant.ModelProviderConstant;
import com.asuka.backend.common.exception.ModelException;
import com.asuka.backend.pojo.vo.ModelTestResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用一条极短消息验证模型地址、Key 和响应格式。
 *
 * <p>不发送业务代码或用户数据；失败只返回可理解的分类提示，不返回上游原始响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProbeServiceImpl implements ModelProbeService {

    private final OpenAiChatModelClient openAiClient;
    private final AnthropicMessagesModelClient anthropicClient;

    @Override
    public ModelTestResultVO test(ModelRuntimeConfig config) {
        ModelTestResultVO result = new ModelTestResultVO();
        result.setProvider(config.getProvider());
        result.setModelId(config.getModelId());
        long started = System.currentTimeMillis();
        try {
            ProviderModelClient client = ModelProviderConstant.ANTHROPIC_MESSAGES.equals(config.getProvider())
                    ? anthropicClient : openAiClient;
            ModelRequest request = new ModelRequest();
            request.setModel(config.getModelId());
            request.setMessages(List.of(ModelMessage.user("Reply with OK.")));
            request.setMaxTokens(16);
            request.setTemperature(0.0);
            request.setTools(null);
            request.setToolChoice(null);
            ModelResponse response = client.chat(request, config);
            if (response.getChoices() == null || response.getChoices().isEmpty()
                    || response.getChoices().get(0).getMessage() == null) {
                result.setSuccess(false);
                result.setMessage("模型响应为空");
            } else {
                result.setSuccess(true);
                result.setMessage("连接成功");
            }
        } catch (ModelException ex) {
            result.setSuccess(false);
            result.setMessage(message(ex.getKind()));
            log.warn("模型测试连接失败：provider={}, model={}, reason={}",
                    config.getProvider(), config.getModelId(), ex.getKind());
        } catch (Exception ex) {
            result.setSuccess(false);
            result.setMessage("连接失败");
            log.warn("模型测试连接异常：provider={}, model={}, type={}",
                    config.getProvider(), config.getModelId(), ex.getClass().getSimpleName());
        }
        result.setLatencyMs(System.currentTimeMillis() - started);
        return result;
    }

    private String message(ModelException.Kind kind) {
        return switch (kind) {
            case NOT_CONFIGURED -> "模型 API Key 未配置";
            case AUTH -> "鉴权失败，请检查 API Key";
            case RATE_LIMITED -> "上游限流，请稍后重试";
            case TIMEOUT -> "连接超时，请检查 base URL 或网络";
            case FORMAT -> "响应格式不兼容，请检查 provider 或 base URL";
            case UPSTREAM -> "上游服务返回错误";
        };
    }
}
