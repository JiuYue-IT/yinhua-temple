package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 设备桥接模式：serial 真实串口，dryrun 仅模拟记录。 */
public enum DeviceMode {
    SERIAL, DRYRUN;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
