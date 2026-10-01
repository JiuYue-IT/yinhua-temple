package com.lifebranch.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.device.DeviceBridge;
import com.lifebranch.server.device.DryRunDeviceBridge;
import com.lifebranch.server.model.DeviceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 端到端走一遍 HTTP 契约（预置模式 + dryrun 设备，AI 未配置）。 */
@SpringBootTest(properties = {"app.preset.delay-ms=0", "app.ai.endpoint=", "app.ai.api-key=", "app.ai.model="})
@AutoConfigureMockMvc
class SessionControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    DeviceBridge device;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/reset")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        ((DryRunDeviceBridge) device).clearHistory();
    }

    @Test
    void fullPresetFlowOverHttp() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String body = "{\"requestId\":\"" + requestId + "\",\"mode\":\"preset\",\"caseId\":\"team-project\"}";

        JsonNode created = json(postJson("/api/sessions", body).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("generating"))
                .andExpect(jsonPath("$.mode").value("preset"))
                .andExpect(jsonPath("$.story").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty()));
        String id = created.get("id").asText();

        // 重发同一请求：同一会话
        json(postJson("/api/sessions", body).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id)));

        JsonNode ready = poll(id, "ready");
        assertThat(ready.at("/story/options/0/reflection/goal").asText()).isNotBlank();

        postJson("/api/sessions/" + id + "/choice", "{\"optionId\":\"A\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reflecting"))
                .andExpect(jsonPath("$.selectedOptionId").value("A"));
        postJson("/api/sessions/" + id + "/choice", "{\"optionId\":\"B\"}")
                .andExpect(jsonPath("$.status").value("ending"));
        postJson("/api/sessions/" + id + "/choice", "{\"optionId\":\"X\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_OPTION"));

        // 主殿抛签：先 sign_drawing，轮询到 sign_ready 后快照带 sign
        mvc.perform(post("/api/sessions/" + id + "/sign")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sign_drawing"))
                .andExpect(jsonPath("$.sign").isEmpty());
        JsonNode signReady = poll(id, "sign_ready");
        assertThat(signReady.at("/sign/title").asText()).isEqualTo("量力而行");
        assertThat(signReady.at("/sign/remedy").asText()).isNotBlank();

        postJson("/api/sessions/" + id + "/receipt", "{\"insight\":\"可以协商角色\",\"nextStep\":\"问一个小任务\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("complete"))
                .andExpect(jsonPath("$.receipt.chosenPath").value("拒绝邀请"))
                .andExpect(jsonPath("$.receipt.insight").value("可以协商角色"))
                .andExpect(jsonPath("$.receipt.createdAt").isString());
        postJson("/api/sessions/" + id + "/receipt", "{\"insight\":\"改了\",\"nextStep\":\"问一个小任务\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RECEIPT_ALREADY_CONFIRMED"));

        // 新 requestId 在未 reset 前是 SESSION_BUSY
        postJson("/api/sessions", "{\"requestId\":\"" + UUID.randomUUID() + "\",\"mode\":\"preset\",\"caseId\":\"team-project\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_BUSY"));

        mvc.perform(get("/api/health")).andExpect(jsonPath("$.device.lastEvent").value("RECEIPT"))
                .andExpect(jsonPath("$.device.lastAck").value("sent"));

        mvc.perform(post("/api/reset")).andExpect(status().isOk());
        mvc.perform(get("/api/sessions/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
        postJson("/api/sessions", body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_EXPIRED"));

        assertThat(((DryRunDeviceBridge) device).history()).containsExactly(DeviceEvent.DRAW, DeviceEvent.STORY,
                DeviceEvent.REFLECT, DeviceEvent.STORY, DeviceEvent.SIGN, DeviceEvent.SIGN_RESULT,
                DeviceEvent.RECEIPT, DeviceEvent.RESET);
    }

    @Test
    void directPresetNeedsNoChoiceAndHasStructuredTwoStageOutput() throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("requestId", UUID.randomUUID().toString(),
                "mode", "preset", "caseId", "team-project", "experience", "direct"));
        var created = json(postJson("/api/sessions", body).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.experience").value("direct")));
        String id = created.path("id").asText();
        var ready = poll(id, "ready");
        assertThat(ready.at("/reading/summary/title").asText()).isNotBlank();
        assertThat(ready.at("/reading/detail/understanding").asText()).isNotBlank();
        postJson("/api/sessions/" + id + "/choice", "{\"optionId\":\"A\"}").andExpect(status().isConflict());
        mvc.perform(post("/api/sessions/" + id + "/sign")).andExpect(status().isOk());
        poll(id, "sign_ready");
        postJson("/api/sessions/" + id + "/receipt", "{\"insight\":\"先做一小步\",\"nextStep\":\"说明时间边界\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("complete"))
                .andExpect(jsonPath("$.receipt.experience").value("direct"))
                .andExpect(jsonPath("$.receipt.concern").isNotEmpty());
    }

    @Test
    void validationErrorsUseErrorEnvelope() throws Exception {
        String missingUnchosen = """
                {"requestId":"%s","mode":"live","input":{"background":"背景","chosenPath":"拒绝","unchosenPath":"   ","priority":"目标"}}
                """.formatted(UUID.randomUUID());
        postJson("/api/sessions", missingUnchosen).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("请填写未选择的道路（最多 100 字）。"));

        postJson("/api/sessions", "{\"requestId\":\"not-a-uuid\",\"mode\":\"preset\",\"caseId\":\"team-project\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        postJson("/api/sessions", "{\"requestId\":\"" + UUID.randomUUID() + "\",\"mode\":\"weird\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        postJson("/api/sessions", "not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(((DryRunDeviceBridge) device).history()).isEmpty();
    }

    @Test
    void liveWithoutAiConfigIs503() throws Exception {
        String live = """
                {"requestId":"%s","mode":"live","input":{"background":"背景","chosenPath":"拒绝","unchosenPath":"接受","priority":"目标"}}
                """.formatted(UUID.randomUUID());
        postJson("/api/sessions", live).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"));
        assertThat(((DryRunDeviceBridge) device).history()).isEmpty();
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode poll(String id, String wanted) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode s = json(mvc.perform(get("/api/sessions/" + id)).andExpect(status().isOk()));
            if (wanted.equals(s.get("status").asText())) {
                return s;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("未等到状态 " + wanted);
    }
}
