package com.lifebranch.server.model;

import com.lifebranch.server.validation.TextRules;
import com.lifebranch.server.error.ApiException;

/** 直接问签的结构化输入；只有 concern 必填，其他信息不由模型编造。 */
public record WishInput(String concern, String background, String chosenPath,
                        String unchosenPath, String priority) {
    public WishInput normalized() {
        if (!TextRules.fits(concern, 400)) throw ApiException.validation("请写下心事或愿望（最多 400 字）。");
        return new WishInput(concern.strip(), optional(background, 400, "背景"),
                optional(chosenPath, 100, "已选道路"), optional(unchosenPath, 100, "未选道路"),
                optional(priority, 150, "在意的目标"));
    }
    private static String optional(String value, int max, String name) {
        if (value == null || value.isBlank()) return null;
        if (!TextRules.fits(value, max)) throw ApiException.validation(name + "最多 " + max + " 字。");
        return value.strip();
    }
}
