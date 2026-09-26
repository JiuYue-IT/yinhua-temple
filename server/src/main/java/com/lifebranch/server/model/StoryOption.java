package com.lifebranch.server.model;

/** 支线内的一个选项。reflection 为 null 表示该选项不触发回望。 */
public record StoryOption(
        String id,
        String label,
        String outcome,
        Reflection reflection,
        ReceiptDraft receiptDraft) {
}
