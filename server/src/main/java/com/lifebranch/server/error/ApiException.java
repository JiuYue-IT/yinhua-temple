package com.lifebranch.server.error;

import org.springframework.http.HttpStatus;

/** 业务错误：由 GlobalExceptionHandler 转为 {"error":{code,message}} 与对应 HTTP 状态。 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException validation(String message) {
        return badRequest(ErrorCodes.VALIDATION_ERROR, message);
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.SESSION_NOT_FOUND, "会话不存在或已结束，请重新开始。");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
