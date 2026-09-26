package com.lifebranch.server.story;

import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;

/**
 * 真实模式的故事来源（AI）。在后台线程同步调用；SessionService 负责总超时、结构校验与迟到结果丢弃。
 * 实现应响应线程中断（超时或重置时会被取消）。
 */
public interface StoryProvider {

    Story generate(Input input) throws StoryGenerationException, InterruptedException;
}
