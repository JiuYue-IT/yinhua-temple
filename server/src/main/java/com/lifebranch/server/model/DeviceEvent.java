package com.lifebranch.server.model;

/** 串口协议 v1 的六种业务事件（文档 03 §5.3）。JSON 中直接使用大写名称。 */
public enum DeviceEvent {
    RESET, DRAW, STORY, REFLECT, RECEIPT, ERROR
}
