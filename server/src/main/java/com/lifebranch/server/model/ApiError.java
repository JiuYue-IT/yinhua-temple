package com.lifebranch.server.model;

/** 错误对象：既用于快照中的 error 字段，也用于 HTTP 错误体 {"error":{...}}。 */
public record ApiError(String code, String message) {
}
