package com.lifebranch.server.web;

import com.lifebranch.server.device.DeviceBridge;
import com.lifebranch.server.device.DryRunDeviceBridge;
import com.lifebranch.server.model.DeviceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 边界：托管 web/dist、本机 CORS、live 模式 AI 失败进入 error 快照、error 后重置可重新开始。
 * AI 指向一个不存在的本地端口，使 live 请求确定地失败为 AI_UNAVAILABLE。
 */
@SpringBootTest(properties = {
        "app.preset.delay-ms=0",
        "app.ai.endpoint=http://127.0.0.1:9",
        "app.ai.api-key=dummy",
        "app.ai.model=claude-opus-4-8",
        "app.ai.request-timeout-seconds=3"})
@AutoConfigureMockMvc
class EdgeCasesTest {

    @TempDir
    static Path dist;

    @DynamicPropertySource
    static void webDist(DynamicPropertyRegistry r) throws IOException {
        Files.writeString(dist.resolve("index.html"), "<!doctype html><title>人生支线</title>", StandardCharsets.UTF_8);
        r.add("app.web.dist", () -> dist.toAbsolutePath().toString().replace('\\', '/') + "/");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    DeviceBridge device;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/reset")).andExpect(status().isOk());
        ((DryRunDeviceBridge) device).clearHistory();
    }

    @Test
    void servesBuiltFrontendAtRoot() throws Exception {
        String html = mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("人生支线");
        // 根路径是欢迎页转发（MockMvc 不跟随转发，真实 Tomcat 会返回 index.html）
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));
    }

    @Test
    void corsAllowsLocalDevServers() throws Exception {
        mvc.perform(options("/api/sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/sessions")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void liveFailureBecomesErrorSnapshotThenResetAllowsPreset() throws Exception {
        String live = """
                {"requestId":"%s","mode":"live","input":{"background":"背景","chosenPath":"拒绝","unchosenPath":"接受","priority":"目标"}}
                """.formatted(UUID.randomUUID());
        String body = mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(live))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("generating"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        String status = "generating";
        for (int i = 0; i < 200 && status.equals("generating"); i++) {
            Thread.sleep(25);
            String snap = mvc.perform(get("/api/sessions/" + id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            status = snap.replaceAll(".*\"status\":\"([^\"]+)\".*", "$1");
        }
        mvc.perform(get("/api/sessions/" + id))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.input.background").value("背景")) // 保留输入，前端可提示重试
                .andExpect(jsonPath("$.story").isEmpty());

        // error 状态下不能选择；新会话需先 reset
        mvc.perform(post("/api/sessions/" + id + "/choice").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":\"A\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        String preset = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"mode\":\"preset\",\"caseId\":\"team-project\"}";
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(preset))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SESSION_BUSY"));

        mvc.perform(post("/api/reset")).andExpect(status().isOk());
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(preset))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.mode").value("preset"));

        assertThat(((DryRunDeviceBridge) device).history())
                .containsSubsequence(DeviceEvent.DRAW, DeviceEvent.ERROR, DeviceEvent.RESET, DeviceEvent.DRAW);
    }

    @Test
    void inputLengthCountsCodePointsAtTheBoundary() throws Exception {
        String ok = "😀".repeat(100);   // 100 码点 = 200 个 UTF-16 单元，应通过
        String tooLong = "😀".repeat(101);
        String tpl = """
                {"requestId":"%s","mode":"live","input":{"background":"背景","chosenPath":"%s","unchosenPath":"接受","priority":"目标"}}
                """;
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(tpl.formatted(UUID.randomUUID(), tooLong)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(tpl.formatted(UUID.randomUUID(), ok)))
                .andExpect(status().isAccepted());
    }

    @Test
    void unknownApiPathIsJsonError() throws Exception {
        mvc.perform(get("/api/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/reset")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }
}
