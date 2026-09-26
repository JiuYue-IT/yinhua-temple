package com.lifebranch.server.model;

import jakarta.validation.constraints.NotNull;

/** POST /api/sessions/:id/choice 请求体。optionId 合法性（A/B 且存在）在 SessionService 中检查。 */
public record ChoiceRequest(@NotNull(message = "缺少 optionId。") String optionId) {
}
