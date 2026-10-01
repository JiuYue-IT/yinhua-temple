package com.lifebranch.server.model;

/**
 * 支线内的一个选项。reflection 为 null 表示该选项不触发回望；
 * sign 为 null 表示故事没带签文（旧预置案例），抛签时由服务端生成兜底签。
 */
public record StoryOption(
        String id,
        String label,
        String outcome,
        Reflection reflection,
        ReceiptDraft receiptDraft,
        Sign sign) {
}
