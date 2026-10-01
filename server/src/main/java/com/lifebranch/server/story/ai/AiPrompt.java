package com.lifebranch.server.story.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifebranch.server.story.StoryGenerationException;

/** 共享同一 AI 传输层，业务提示词与返回类型分别定义。 */
public interface AiPrompt<T> {
    String systemPrompt();
    JsonNode schema();
    T parse(String text) throws StoryGenerationException;
}
