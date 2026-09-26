package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 设备连接状态。只有 hello 握手成功且连接有效才是 online。 */
public enum DeviceStatus {
    ONLINE, OFFLINE, DRYRUN;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
