package com.lifebranch.server.model;

import java.time.Instant;
import java.util.List;

/**
 * 确认后的收据（文档 04 §3.4）。由后端组合：已选/未选道路来自原输入，不让模型修改。
 * createdAt 序列化为 ISO 8601 UTC 字符串。
 */
public record Receipt(
        String sessionId,
        Instant createdAt,
        SessionMode mode,
        String chosenPath,
        String unchosenPath,
        List<String> assumptions,
        String insight,
        String nextStep,
        ExperienceMode experience,
        String concern) {
    public Receipt(String sessionId, Instant createdAt, SessionMode mode, String chosenPath,
                   String unchosenPath, List<String> assumptions, String insight, String nextStep) {
        this(sessionId, createdAt, mode, chosenPath, unchosenPath, assumptions, insight, nextStep,
                ExperienceMode.EXPLORE, null);
    }
}
