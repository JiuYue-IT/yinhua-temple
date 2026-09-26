package com.lifebranch.server.model;

/** HTTP 错误响应体：{"error":{"code":"...","message":"..."}}。 */
public record ErrorResponse(ApiError error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ApiError(code, message));
    }
}
