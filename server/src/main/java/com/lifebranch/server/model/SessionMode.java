package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 会话模式：live 真实 AI，preset 预置案例。 */
public enum SessionMode {
    LIVE, PRESET;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static SessionMode of(String v) {
        return v == null ? null : valueOf(v.trim().toUpperCase());
    }
}
