package com.lifebranch.server.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/sessions 请求体（文档 04 §4.2）。
 * live：必须带 input，不带 caseId；preset：必须带 caseId，不接收 input。模式相关规则在 SessionService 中检查。
 */
public record CreateSessionRequest(
        @NotNull(message = "缺少 requestId。")
        @Pattern(regexp = UUID_REGEX, message = "requestId 必须是 UUID。")
        String requestId,
        @NotNull(message = "缺少 mode（live 或 preset）。")
        SessionMode mode,
        String caseId,
        @Valid Input input) {

    public static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
}
