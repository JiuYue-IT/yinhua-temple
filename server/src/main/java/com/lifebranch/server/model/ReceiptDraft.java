package com.lifebranch.server.model;

/** 每个选项自带的收据草稿，用户确认前可编辑。 */
public record ReceiptDraft(String insight, String nextStep) {
}
