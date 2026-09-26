package com.lifebranch.server.error;

/** 错误码常量，与 contracts/types.ts 的 ErrorCode 保持一致。 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // 400
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INVALID_OPTION = "INVALID_OPTION";
    // 404
    public static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    // 409
    public static final String REQUEST_CONFLICT = "REQUEST_CONFLICT";
    public static final String SESSION_BUSY = "SESSION_BUSY";
    public static final String REQUEST_EXPIRED = "REQUEST_EXPIRED";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String RECEIPT_ALREADY_CONFIRMED = "RECEIPT_ALREADY_CONFIRMED";
    // 503
    public static final String AI_NOT_CONFIGURED = "AI_NOT_CONFIGURED";
    // 快照内 error.code
    public static final String AI_TIMEOUT = "AI_TIMEOUT";
    public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    public static final String AI_FORMAT_ERROR = "AI_FORMAT_ERROR";
    // 500（契约外的兜底，不应出现）
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
