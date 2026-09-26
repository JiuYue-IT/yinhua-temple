package com.lifebranch.server.story;

import com.lifebranch.server.error.ErrorCodes;
import com.lifebranch.server.model.ApiError;

/** 生成失败：写入快照 status=error，code 为 AI_TIMEOUT / AI_UNAVAILABLE / AI_FORMAT_ERROR。 */
public class StoryGenerationException extends Exception {

    private final String code;

    public StoryGenerationException(String code, String userMessage) {
        super(userMessage);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public ApiError toApiError() {
        return new ApiError(code, getMessage());
    }

    public static StoryGenerationException timeout() {
        return new StoryGenerationException(ErrorCodes.AI_TIMEOUT, "生成超时，请重试或选择预置案例。");
    }

    public static StoryGenerationException unavailable() {
        return new StoryGenerationException(ErrorCodes.AI_UNAVAILABLE, "AI 服务暂时不可用，请重试或选择预置案例。");
    }

    public static StoryGenerationException formatError() {
        return new StoryGenerationException(ErrorCodes.AI_FORMAT_ERROR, "生成内容格式不正确，请重试或选择预置案例。");
    }
}
