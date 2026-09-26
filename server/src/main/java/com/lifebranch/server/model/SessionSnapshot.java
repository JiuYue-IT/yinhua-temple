package com.lifebranch.server.model;

/**
 * 前端轮询得到的会话快照（文档 04 §3.3）。不内嵌设备状态，设备信息走 /api/health。
 * story / selectedOptionId / receipt / error 在未产生时为 null，并且必须序列化输出。
 */
public record SessionSnapshot(
        String id,
        SessionMode mode,
        SessionStatus status,
        Input input,
        Story story,
        String selectedOptionId,
        Receipt receipt,
        ApiError error) {
}
