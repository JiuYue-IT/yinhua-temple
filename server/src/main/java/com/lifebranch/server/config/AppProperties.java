package com.lifebranch.server.config;

import com.lifebranch.server.model.DeviceMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 全部外部配置。值来自环境变量（见 application.yml），密钥不写入仓库。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Ai ai, Device device, @DefaultValue Preset preset) {

    /** 预置案例加载前的固定停顿，让「正在问签」有时间呈现；测试中设为 0。 */
    public record Preset(@DefaultValue("2000") long delayMs) {
    }

    public enum AiProvider {
        /** Anthropic Messages 协议（官方 Java SDK），默认。 */
        ANTHROPIC,
        /** OpenAI 兼容 chat/completions 协议，中转站 Anthropic 端点不可用时的备选。 */
        OPENAI
    }

    /**
     * @param endpoint         服务地址。anthropic：到域名为止（可带 /v1）；openai：到 /v1 为止（可带 /chat/completions）
     * @param authHeader       anthropic 协议的鉴权头：x-api-key（默认）或 bearer
     * @param effort           anthropic 协议的 output_config.effort：low / medium / high / xhigh / max，空字符串表示不发送
     * @param structuredOutput 是否要求服务端按 JSON Schema 约束输出（anthropic：output_config.format；openai：json_object）
     * @param refusalFallback  Claude Opus 5 / Fable 5 系列被安全分类器拒绝时，是否启用服务端自动回退
     */
    public record Ai(
            @DefaultValue("anthropic") AiProvider provider,
            String endpoint,
            String apiKey,
            @DefaultValue("claude-opus-5") String model,
            @DefaultValue("x-api-key") String authHeader,
            @DefaultValue("low") String effort,
            @DefaultValue("true") boolean structuredOutput,
            @DefaultValue("true") boolean refusalFallback,
            @DefaultValue("16000") long maxTokens,
            @DefaultValue("60") int requestTimeoutSeconds,
            @DefaultValue("65") int taskTimeoutSeconds,
            String openaiThinking) {

        @ConstructorBinding
        public Ai {
            if (openaiThinking != null && !openaiThinking.isBlank()
                    && !openaiThinking.equals("enabled") && !openaiThinking.equals("disabled")) {
                throw new IllegalArgumentException("AI_OPENAI_THINKING 必须为空、enabled 或 disabled");
            }
        }

        /** 保留已有调用；默认不发送服务商特有的 thinking 参数。 */
        public Ai(AiProvider provider, String endpoint, String apiKey, String model, String authHeader,
                  String effort, boolean structuredOutput, boolean refusalFallback, long maxTokens,
                  int requestTimeoutSeconds, int taskTimeoutSeconds) {
            this(provider, endpoint, apiKey, model, authHeader, effort, structuredOutput, refusalFallback,
                    maxTokens, requestTimeoutSeconds, taskTimeoutSeconds, null);
        }

        /** 只表示必要配置都存在，不代表服务一定可用。 */
        public boolean configured() {
            return notBlank(endpoint) && notBlank(apiKey) && notBlank(model);
        }

        /** 防止密钥进入日志。 */
        @Override
        public String toString() {
            return "Ai[provider=" + provider + ", endpoint=" + endpoint
                    + ", apiKey=" + (notBlank(apiKey) ? "***" : "<empty>") + ", model=" + model
                    + ", authHeader=" + authHeader + ", effort=" + effort + ", structuredOutput=" + structuredOutput
                    + ", refusalFallback=" + refusalFallback + ", maxTokens=" + maxTokens
                    + ", requestTimeoutSeconds=" + requestTimeoutSeconds
                    + ", taskTimeoutSeconds=" + taskTimeoutSeconds + ", openaiThinking=" + openaiThinking + "]";
        }
    }

    public record Device(
            @DefaultValue("dryrun") DeviceMode mode,
            String serialPort) {
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
