package com.lifebranch.server.story;

import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;
import org.springframework.stereotype.Component;

/**
 * 真实 AI 生成。M2 阶段为占位实现：一律返回 AI_UNAVAILABLE；模块 M4 实现真实调用。
 */
@Component
public class LiveStoryProvider implements StoryProvider {

    @Override
    public Story generate(Input input) throws StoryGenerationException {
        throw StoryGenerationException.unavailable();
    }
}
