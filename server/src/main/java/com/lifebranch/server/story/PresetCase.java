package com.lifebranch.server.story;

import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;

/** 预置案例：固定输入 + 固定故事。预置模式下由后端填入 input，不接收用户自定义内容。 */
public record PresetCase(String caseId, Input input, Story story) {
}
