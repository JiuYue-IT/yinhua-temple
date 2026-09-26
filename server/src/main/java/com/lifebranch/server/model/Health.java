package com.lifebranch.server.model;

/** GET /api/health 响应（文档 04 §4.1）。 */
public record Health(String service, boolean aiConfigured, DeviceInfo device) {

    /**
     * lastEvent 初始为 null；lastAck 初始为 null。
     * online 只在 hello 握手成功且连接有效时出现。
     */
    public record DeviceInfo(DeviceMode mode, DeviceStatus status, DeviceEvent lastEvent, AckStatus lastAck) {
    }
}
