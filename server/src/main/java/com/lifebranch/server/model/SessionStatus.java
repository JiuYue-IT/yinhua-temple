package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** 会话状态：generating → ready → reflecting / ending → sign_drawing → sign_ready → complete；失败为 error。 */
public enum SessionStatus {
    GENERATING, READY, REFLECTING, ENDING, SIGN_DRAWING, SIGN_READY, COMPLETE, ERROR;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
