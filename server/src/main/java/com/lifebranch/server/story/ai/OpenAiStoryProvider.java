package com.lifebranch.server.story.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.story.StoryGenerationException;
import com.lifebranch.server.story.StoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * OpenAI 兼容 chat/completions 协议（中转站备选）。Java HttpClient，Bearer 鉴权，不自动重试。
 * structuredOutput=true 时发送 response_format: json_object（兼容性最好，结构仍由 StoryValidator 检查）。
 */
public class OpenAiStoryProvider implements StoryProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiStoryProvider.class);

    private final AppProperties.Ai cfg;
    private final StoryPrompt prompt;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final URI uri;

    public OpenAiStoryProvider(AppProperties.Ai cfg, StoryPrompt prompt) {
        this.cfg = cfg;
        this.prompt = prompt;
        this.uri = URI.create(completionsUrl(cfg.endpoint()));
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(10, cfg.requestTimeoutSeconds())))
                .build();
    }

    /** 接受 .../v1 或 .../v1/chat/completions 两种写法。 */
    static String completionsUrl(String endpoint) {
        String s = endpoint.strip();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.endsWith("/chat/completions") ? s : s + "/chat/completions";
    }

    String buildBody(Input input) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.model());
        body.put("max_tokens", cfg.maxTokens());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.systemPrompt());
        messages.addObject().put("role", "user").put("content", prompt.userMessage(input));
        if (cfg.structuredOutput()) {
            body.putObject("response_format").put("type", "json_object");
        }
        return mapper.writeValueAsString(body);
    }

    @Override
    public Story generate(Input input) throws StoryGenerationException, InterruptedException {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(cfg.requestTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(buildBody(input), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        CompletableFuture<HttpResponse<String>> future =
                http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        HttpResponse<String> resp;
        try {
            resp = future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                log.warn("AI 请求超时");
                throw StoryGenerationException.timeout();
            }
            log.warn("AI 网络错误：{}", e.getCause() == null ? e.toString() : e.getCause().toString());
            throw StoryGenerationException.unavailable();
        }

        int code = resp.statusCode();
        if (code != 200) {
            switch (code) {
                case 401, 403 -> log.warn("AI 鉴权失败（{}）：检查 AI_API_KEY", code);
                case 404 -> log.warn("AI 返回 404：检查 AI_ENDPOINT（应到 /v1 为止）和 AI_MODEL");
                case 400 -> log.warn("AI 返回 400：{}。若不支持 response_format 可设 AI_STRUCTURED_OUTPUT=false",
                        truncate(resp.body()));
                case 429 -> log.warn("AI 限流（429）");
                default -> log.warn("AI 服务错误 {}", code);
            }
            throw StoryGenerationException.unavailable();
        }

        JsonNode root;
        try {
            root = mapper.readTree(resp.body());
        } catch (IOException e) {
            log.warn("AI 响应不是 JSON（HTTP 200）");
            throw StoryGenerationException.unavailable();
        }
        JsonNode choice = root.path("choices").path(0);
        String finish = choice.path("finish_reason").asText("");
        JsonNode usage = root.path("usage");
        log.info("AI 返回：model={} finish={} prompt_tokens={} completion_tokens={}",
                root.path("model").asText("?"), finish, usage.path("prompt_tokens").asText("?"),
                usage.path("completion_tokens").asText("?"));
        if ("length".equals(finish)) {
            log.warn("AI 输出达到 max_tokens 被截断");
            throw StoryGenerationException.formatError();
        }
        if ("content_filter".equals(finish)) {
            log.warn("AI 输出被内容过滤");
            throw StoryGenerationException.unavailable();
        }
        return prompt.parse(choice.path("message").path("content").asText(null));
    }

    /** 错误响应体可能包含中转站诊断信息，但不会包含用户背景；仍截断以免刷屏。 */
    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
