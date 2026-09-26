package com.lifebranch.server.model;

import com.lifebranch.server.validation.Text;
import com.lifebranch.server.validation.TextRules;

/** 用户输入（文档 04 §3.1）。长度按码点计数，去首尾空白后校验。 */
public record Input(
        @Text(max = 400, message = "请填写背景（最多 400 字）。") String background,
        @Text(max = 100, message = "请填写已经选择的道路（最多 100 字）。") String chosenPath,
        @Text(max = 100, message = "请填写未选择的道路（最多 100 字）。") String unchosenPath,
        @Text(max = 150, message = "请填写你在意的目标（最多 150 字）。") String priority) {

    public static final int BACKGROUND_MAX = 400;
    public static final int PATH_MAX = 100;
    public static final int PRIORITY_MAX = 150;

    /** 返回去首尾空白后的副本，会话中保存这个版本。 */
    public Input trimmed() {
        return new Input(TextRules.trim(background), TextRules.trim(chosenPath),
                TextRules.trim(unchosenPath), TextRules.trim(priority));
    }
}
