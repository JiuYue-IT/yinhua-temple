package com.lifebranch.server.model;

/** 串口协议 v1 的业务事件（文档 03 §5.3），SIGN / SIGN_RESULT 为主殿抛签新增。JSON 中直接使用大写名称。 */
public enum DeviceEvent {
    RESET, DRAW, STORY, REFLECT, SIGN, SIGN_RESULT, RECEIPT, ERROR
}
