package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 最近一条命令的回执状态（health.device.lastAck）。
 * sent 已写出；accepted 固件已接收；done 定时结束（不证明实物摇动成功）；
 * unknown 超时无回执，不能冒充完成。
 */
public enum AckStatus {
    SENT, ACCEPTED, DONE, CANCELLED, ERROR, UNKNOWN;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
