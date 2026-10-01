package com.lifebranch.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 业务流程，独立于 live / preset 数据来源。旧请求默认 explore。 */
public enum ExperienceMode {
    DIRECT, EXPLORE;
    @JsonValue public String json() { return name().toLowerCase(); }
    @JsonCreator public static ExperienceMode of(String value) {
        return value == null ? null : valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
