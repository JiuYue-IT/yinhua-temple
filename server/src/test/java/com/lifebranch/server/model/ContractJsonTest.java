package com.lifebranch.server.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 确认 JSON 形状与文档 04 §3 一致：null 字段保留、枚举小写、日期为 ISO 字符串。 */
@JsonTest(properties = {
        "spring.jackson.default-property-inclusion=always",
        "spring.jackson.serialization.write-dates-as-timestamps=false"})
class ContractJsonTest {

    @Autowired
    ObjectMapper mapper;

    @Test
    void generatingSnapshotKeepsNullFields() throws Exception {
        Input in = new Input("背景", "拒绝邀请", "接受邀请", "目标");
        SessionSnapshot s = new SessionSnapshot("s-001", SessionMode.LIVE, SessionStatus.GENERATING,
                in, null, null, null, null, null);
        JsonNode j = mapper.readTree(mapper.writeValueAsString(s));
        assertThat(j.get("mode").asText()).isEqualTo("live");
        assertThat(j.get("status").asText()).isEqualTo("generating");
        for (String f : List.of("story", "selectedOptionId", "sign", "receipt", "error")) {
            assertThat(j.has(f)).as(f).isTrue();
            assertThat(j.get(f).isNull()).as(f).isTrue();
        }
    }

    @Test
    void receiptDateIsIsoUtcString() throws Exception {
        Receipt r = new Receipt("s-001", Instant.parse("2026-09-26T10:00:00Z"), SessionMode.PRESET,
                "拒绝邀请", "接受邀请", List.of("假设"), "发现", "下一步");
        JsonNode j = mapper.readTree(mapper.writeValueAsString(r));
        assertThat(j.get("createdAt").asText()).isEqualTo("2026-09-26T10:00:00Z");
        assertThat(j.get("mode").asText()).isEqualTo("preset");
    }

    @Test
    void createRequestParsesLowercaseMode() throws Exception {
        CreateSessionRequest req = mapper.readValue("""
                {"requestId":"ca8a6bde-f26a-4b43-83a1-ac0d9d864b54","mode":"preset","caseId":"team-project"}
                """, CreateSessionRequest.class);
        assertThat(req.mode()).isEqualTo(SessionMode.PRESET);
        assertThat(req.input()).isNull();
    }
}
