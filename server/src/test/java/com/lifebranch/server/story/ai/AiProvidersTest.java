package com.lifebranch.server.story.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.error.ErrorCodes;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.story.PresetCatalog;
import com.lifebranch.server.story.StoryGenerationException;
import com.lifebranch.server.story.StoryValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用本地假 HTTP 服务验证两种协议的请求格式与响应处理（不访问真实 AI）。 */
class AiProvidersTest {

    private static final Input INPUT = new Input("我在两份工作之间犹豫。", "留在本地", "去外地工作", "希望有成长空间");

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String base;
    private StoryPrompt prompt;
    private String storyJson;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
    private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody;
    private volatile long delayMs;

    @BeforeEach
    void setUp() throws Exception {
        PresetCatalog catalog = new PresetCatalog(mapper, new StoryValidator());
        prompt = new StoryPrompt(catalog);
        storyJson = mapper.writeValueAsString(catalog.find("team-project").orElseThrow().story());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            calls.incrementAndGet();
            lastPath.set(ex.getRequestURI().getPath());
            ex.getRequestHeaders().forEach((k, v) -> lastHeaders.put(k.toLowerCase(), String.join(",", v)));
            lastBody.set(mapper.readTree(ex.getRequestBody().readAllBytes()));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private AppProperties.Ai cfg(AppProperties.AiProvider provider, String endpoint, String auth,
                                 boolean structured, int timeoutSeconds) {
        return new AppProperties.Ai(provider, endpoint, "test-key", "claude-opus-5", auth, "low",
                structured, true, 16000, timeoutSeconds, 30);
    }

    private String anthropicResponse(String text, String stopReason) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "id", "msg_1", "type", "message", "role", "assistant", "model", "claude-opus-5",
                "content", java.util.List.of(Map.of("type", "text", "text", text)),
                "stop_reason", stopReason,
                "usage", Map.of("input_tokens", 10, "output_tokens", 20)));
    }

    // ------------------------------------------------------------ Anthropic

    @Test
    void directReadingUsesOneRequestAndOwnSchemaForBothProtocols() throws Exception {
        var readingPrompt = new ReadingPrompt();
        var wish = new com.lifebranch.server.model.WishInput("希望把项目做好", null, null, null, null);
        String json = mapper.writeValueAsString(readingPrompt.preset());
        responseBody = anthropicResponse(json, "end_turn");
        try (var p = new AnthropicStoryProvider(cfg(AppProperties.AiProvider.ANTHROPIC, base, "x-api-key", true, 5), prompt)) {
            var result = p.generateContent(readingPrompt.userMessage(wish), readingPrompt);
            assertThat(result.summary().title()).isEqualTo("先行一小步");
            assertThat(result.detail().understanding()).isNotBlank();
        }
        assertThat(lastBody.get().at("/output_config/format/schema/required").toString()).contains("summary", "detail");
        assertThat(lastBody.get().path("messages").toString()).contains("concern");
        assertThat(calls.get()).isEqualTo(1);
        responseBody = mapper.writeValueAsString(Map.of("choices", java.util.List.of(Map.of("finish_reason", "stop", "message", Map.of("content", json)))));
        var p = new OpenAiStoryProvider(cfg(AppProperties.AiProvider.OPENAI, base + "/v1", "bearer", true, 5), prompt);
        var result = p.generateContent(readingPrompt.userMessage(wish), readingPrompt);
        assertThat(result.detail().nextStep()).isNotBlank();
        assertThat(lastBody.get().path("messages").toString()).contains("summary", "concern");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void anthropicRequestShapeAndSuccess() throws Exception {
        responseBody = anthropicResponse(storyJson, "end_turn");
        try (AnthropicStoryProvider p = new AnthropicStoryProvider(
                cfg(AppProperties.AiProvider.ANTHROPIC, base + "/v1/", "x-api-key", true, 5), prompt)) {
            Story s = p.generate(INPUT);
            assertThat(s.title()).isEqualTo("同行");
        }
        assertThat(lastPath.get()).isEqualTo("/v1/messages");
        assertThat(lastHeaders.get("x-api-key")).isEqualTo("test-key");
        assertThat(lastHeaders.get("anthropic-version")).isNotBlank();
        assertThat(lastHeaders.get("anthropic-beta")).contains(AnthropicStoryProvider.FALLBACK_BETA);

        JsonNode body = lastBody.get();
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5");
        assertThat(body.path("fallbacks").asText()).isEqualTo("default");
        assertThat(body.path("output_config").path("effort").asText()).isEqualTo("low");
        assertThat(body.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(body.path("output_config").path("format").path("schema").path("required").toString())
                .contains("options");
        assertThat(body.has("thinking")).isFalse();
        assertThat(body.path("system").toString()).contains("未选之路");
        // 用户输入作为 JSON 数据放在用户消息里，而不是系统提示词里
        assertThat(body.path("system").toString()).doesNotContain("两份工作");
        assertThat(body.path("messages").path(0).toString()).contains("两份工作").contains("unchosenPath");
    }

    @Test
    void anthropicBearerAuthAndOptionalFieldsOff() throws Exception {
        responseBody = anthropicResponse("```json\n" + storyJson + "\n```", "end_turn");
        AppProperties.Ai c = new AppProperties.Ai(AppProperties.AiProvider.ANTHROPIC, base, "test-key",
                "claude-opus-5", "bearer", "", false, false, 16000, 5, 30);
        try (AnthropicStoryProvider p = new AnthropicStoryProvider(c, prompt)) {
            assertThat(p.generate(INPUT).options()).hasSize(2); // 代码块包裹也能解析
        }
        assertThat(lastHeaders.get("authorization")).isEqualTo("Bearer test-key");
        assertThat(lastHeaders).doesNotContainKey("x-api-key");
        JsonNode body = lastBody.get();
        assertThat(body.has("output_config")).isFalse();
        assertThat(body.has("fallbacks")).isFalse();
    }

    @Test
    void anthropicStopReasonsAndErrors() throws Exception {
        try (AnthropicStoryProvider p = new AnthropicStoryProvider(
                cfg(AppProperties.AiProvider.ANTHROPIC, base, "x-api-key", true, 5), prompt)) {
            responseBody = anthropicResponse("", "refusal");
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_UNAVAILABLE);

            responseBody = anthropicResponse("{\"title\":\"截", "max_tokens");
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_FORMAT_ERROR);

            responseBody = anthropicResponse("抱歉，我无法生成。", "end_turn");
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_FORMAT_ERROR);

            status = 401;
            responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"bad key\"}}";
            int before = calls.get();
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_UNAVAILABLE);
            status = 500;
            responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}";
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_UNAVAILABLE);
            assertThat(calls.get() - before).as("不自动重试").isEqualTo(2);
        }
    }

    @Test
    void anthropicTimeoutMapsToAiTimeout() throws Exception {
        responseBody = anthropicResponse(storyJson, "end_turn");
        delayMs = 2500;
        try (AnthropicStoryProvider p = new AnthropicStoryProvider(
                cfg(AppProperties.AiProvider.ANTHROPIC, base, "x-api-key", true, 1), prompt)) {
            assertCode(() -> p.generate(INPUT), ErrorCodes.AI_TIMEOUT);
        }
    }

    @Test
    void baseUrlNormalization() {
        assertThat(AnthropicStoryProvider.baseUrl("https://relay.example.com/v1/messages/"))
                .isEqualTo("https://relay.example.com");
        assertThat(AnthropicStoryProvider.baseUrl("https://relay.example.com/v1")).isEqualTo("https://relay.example.com");
        assertThat(AnthropicStoryProvider.baseUrl("https://relay.example.com/api"))
                .isEqualTo("https://relay.example.com/api");
        assertThat(OpenAiStoryProvider.completionsUrl("https://relay.example.com/v1/"))
                .isEqualTo("https://relay.example.com/v1/chat/completions");
        assertThat(OpenAiStoryProvider.completionsUrl("https://relay.example.com/v1/chat/completions"))
                .isEqualTo("https://relay.example.com/v1/chat/completions");
    }

    // ------------------------------------------------------------ OpenAI 兼容

    private String openAiResponse(String content, String finish) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "id", "c1", "model", "claude-opus-5",
                "choices", java.util.List.of(Map.of("index", 0, "finish_reason", finish,
                        "message", Map.of("role", "assistant", "content", content))),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20)));
    }

    @Test
    void openAiRequestShapeAndSuccess() throws Exception {
        responseBody = openAiResponse(storyJson, "stop");
        OpenAiStoryProvider p = new OpenAiStoryProvider(
                cfg(AppProperties.AiProvider.OPENAI, base + "/v1", "bearer", true, 5), prompt);
        assertThat(p.generate(INPUT).title()).isEqualTo("同行");
        assertThat(lastPath.get()).isEqualTo("/v1/chat/completions");
        assertThat(lastHeaders.get("authorization")).isEqualTo("Bearer test-key");
        JsonNode body = lastBody.get();
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(1).path("content").asText()).contains("两份工作");
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.has("thinking")).isFalse();
    }

    @Test
    void optionalOpenAiThinkingPreservesStructuredReading() throws Exception {
        var readingPrompt = new ReadingPrompt();
        var wish = new com.lifebranch.server.model.WishInput("希望把项目做好", null, null, null, null);
        responseBody = openAiResponse(mapper.writeValueAsString(readingPrompt.preset()), "stop");
        for (String thinking : java.util.List.of("disabled", "enabled")) {
            var config = new AppProperties.Ai(AppProperties.AiProvider.OPENAI, base + "/v1", "test-key", "kimi-k2.6",
                    "bearer", "low", true, true, 16000, 5, 30, thinking);
            var provider = new OpenAiStoryProvider(config, prompt);
            var reading = provider.generateContent(readingPrompt.userMessage(wish), readingPrompt);
            assertThat(lastBody.get().at("/thinking/type").asText()).isEqualTo(thinking);
            assertThat(lastBody.get().at("/response_format/type").asText()).isEqualTo("json_object");
            assertThat(com.lifebranch.server.story.ReadingValidator.validate(reading)).isEmpty();
        }
    }

    @Test
    void openAiErrors() throws Exception {
        OpenAiStoryProvider p = new OpenAiStoryProvider(
                cfg(AppProperties.AiProvider.OPENAI, base + "/v1", "bearer", false, 1), prompt);
        responseBody = openAiResponse("{\"title\":", "length");
        assertCode(() -> p.generate(INPUT), ErrorCodes.AI_FORMAT_ERROR);

        status = 429;
        responseBody = "{\"error\":{\"message\":\"rate\"}}";
        assertCode(() -> p.generate(INPUT), ErrorCodes.AI_UNAVAILABLE);

        status = 200;
        responseBody = openAiResponse(storyJson, "stop");
        delayMs = 2500;
        assertCode(() -> p.generate(INPUT), ErrorCodes.AI_TIMEOUT);
    }

    private static void assertCode(ThrowingCall call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(StoryGenerationException.class)
                .extracting(e -> ((StoryGenerationException) e).code()).isEqualTo(code);
    }

    @FunctionalInterface
    interface ThrowingCall {
        void run() throws Exception;
    }
}
