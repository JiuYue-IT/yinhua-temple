package com.lifebranch.server.story;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.story.ai.AnthropicStoryProvider;
import com.lifebranch.server.story.ai.OpenAiStoryProvider;
import com.lifebranch.server.story.ai.StoryPrompt;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 真实 AI 生成入口：按 AI_PROVIDER 选择 Anthropic（默认）或 OpenAI 兼容协议。
 * 未配置时不创建客户端；SessionService 会在创建会话时直接返回 503 AI_NOT_CONFIGURED。
 */
@Component
public class LiveStoryProvider implements StoryProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveStoryProvider.class);

    private final StoryProvider delegate;

    public LiveStoryProvider(AppProperties props, StoryPrompt prompt) {
        AppProperties.Ai ai = props.ai();
        if (!ai.configured()) {
            log.info("AI 未配置（需要 AI_ENDPOINT、AI_API_KEY、AI_MODEL），真实模式不可用，预置案例可用");
            this.delegate = null;
            return;
        }
        this.delegate = switch (ai.provider()) {
            case ANTHROPIC -> new AnthropicStoryProvider(ai, prompt);
            case OPENAI -> new OpenAiStoryProvider(ai, prompt);
        };
        log.info("AI 已配置：{}", ai); // toString 已隐藏密钥
    }

    @Override
    public com.lifebranch.server.model.Story generate(com.lifebranch.server.model.Input input)
            throws StoryGenerationException, InterruptedException {
        if (delegate == null) {
            throw StoryGenerationException.unavailable();
        }
        return delegate.generate(input);
    }

    @PreDestroy
    void close() throws Exception {
        if (delegate instanceof AutoCloseable c) {
            c.close();
        }
    }
}
