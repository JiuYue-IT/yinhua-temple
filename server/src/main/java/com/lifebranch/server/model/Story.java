package com.lifebranch.server.model;

import java.util.List;

/**
 * 一次生成的完整最小故事（文档 04 §3.2）：一个决策节点、两个选项。
 * 真实 AI 与预置案例都必须满足 StoryValidator 的约束。
 */
public record Story(
        String title,
        String question,
        List<String> assumptions,
        String opening,
        String decision,
        List<StoryOption> options) {

    public StoryOption option(String id) {
        if (options == null || id == null) {
            return null;
        }
        return options.stream().filter(o -> id.equals(o.id())).findFirst().orElse(null);
    }
}
