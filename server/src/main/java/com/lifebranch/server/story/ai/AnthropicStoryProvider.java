package com.lifebranch.server.story.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.story.StoryGenerationException;
import com.lifebranch.server.story.StoryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Anthropic Messages 协议（官方 Java SDK）。支持中转站：baseUrl 指向中转站，鉴权头可选 x-api-key 或 Bearer。
 * <ul>
 *   <li>不自动重试（maxRetries=0），单次请求超时 requestTimeoutSeconds。</li>
 *   <li>thinking 不显式设置：Claude Opus 5 默认自适应；用 output_config.effort 控制深度与延迟。</li>
 *   <li>structuredOutput=true 时用 output_config.format 的 JSON Schema 约束输出。</li>
 *   <li>refusalFallback=true 时启用服务端回退（beta server-side-fallback-2026-07-01，fallbacks: "default"）。</li>
 *   <li>先检查 stop_reason：refusal → 不可用；max_tokens → 格式错误（输出被截断）。</li>
 * </ul>
 */
public class AnthropicStoryProvider implements StoryProvider, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AnthropicStoryProvider.class);
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AppProperties.Ai cfg;
    private final StoryPrompt prompt;
    private final AnthropicClient client;

    public AnthropicStoryProvider(AppProperties.Ai cfg, StoryPrompt prompt) {
        this.cfg = cfg;
        this.prompt = prompt;
        AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                .baseUrl(baseUrl(cfg.endpoint()))
                .timeout(Duration.ofSeconds(cfg.requestTimeoutSeconds()))
                .maxRetries(0);
        if ("bearer".equalsIgnoreCase(cfg.authHeader())) {
            b.authToken(cfg.apiKey());
        } else {
            b.apiKey(cfg.apiKey());
        }
        this.client = b.build();
    }

    /** SDK 会自行拼接 /v1/messages，所以去掉用户可能写上的 /v1 或 /v1/messages。 */
    static String baseUrl(String endpoint) {
        String s = endpoint.strip();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        for (String suffix : new String[]{"/v1/messages", "/v1"}) {
            if (s.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length());
            }
        }
        return s;
    }

    MessageCreateParams buildParams(Input input) {
        MessageCreateParams.Builder p = MessageCreateParams.builder()
                .model(cfg.model())
                .maxTokens(cfg.maxTokens())
                .system(prompt.systemPrompt())
                .addUserMessage(prompt.userMessage(input));

        OutputConfig.Builder oc = null;
        OutputConfig.Effort effort = parseEffort(cfg.effort());
        if (effort != null) {
            oc = OutputConfig.builder().effort(effort);
        }
        if (cfg.structuredOutput()) {
            if (oc == null) {
                oc = OutputConfig.builder();
            }
            oc.format(JsonOutputFormat.builder().schema(schema(prompt.schema())).build());
        }
        if (oc != null) {
            p.outputConfig(oc.build());
        }
        if (cfg.refusalFallback()) {
            p.putAdditionalHeader("anthropic-beta", FALLBACK_BETA);
            p.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return p.build();
    }

    @Override
    public Story generate(Input input) throws StoryGenerationException, InterruptedException {
        MessageCreateParams params = buildParams(input);
        CompletableFuture<Message> future = client.async().messages().create(params);
        Message msg;
        try {
            msg = future.get();
        } catch (InterruptedException e) {
            future.cancel(true); // 超时或重置：放弃等待
            throw e;
        } catch (CancellationException e) {
            throw StoryGenerationException.unavailable();
        } catch (ExecutionException e) {
            throw map(e.getCause());
        }

        StopReason stop = msg.stopReason().orElse(null);
        log.info("AI 返回：model={} stop={} input_tokens={} output_tokens={}", msg.model().asString(),
                stop == null ? "null" : stop.asString(), msg.usage().inputTokens(), msg.usage().outputTokens());
        if (StopReason.REFUSAL.equals(stop)) {
            log.warn("AI 拒绝了本次请求（refusal），category={}。若换任何输入都被拒绝，多半是中转站上该模型不可用，"
                            + "请改 AI_MODEL（例如 claude-opus-4-8）",
                    msg.stopDetails().flatMap(d -> d.category()).map(Object::toString).orElse("?"));
            throw StoryGenerationException.unavailable();
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            log.warn("AI 输出达到 max_tokens 被截断");
            throw StoryGenerationException.formatError();
        }
        String text = msg.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
        return prompt.parse(text);
    }

    /** 把 SDK 异常映射为三种业务错误码，并只记录状态与类型，不记录请求内容。 */
    static StoryGenerationException map(Throwable t) {
        if (t instanceof UnauthorizedException || t instanceof PermissionDeniedException) {
            log.warn("AI 鉴权失败（{}）：检查 AI_API_KEY，以及 AI_AUTH 是否应为 bearer", status(t));
            return StoryGenerationException.unavailable();
        }
        if (t instanceof NotFoundException) {
            log.warn("AI 返回 404：检查 AI_ENDPOINT（不要带 /v1/messages）和 AI_MODEL 是否被中转站支持");
            return StoryGenerationException.unavailable();
        }
        if (t instanceof BadRequestException) {
            log.warn("AI 返回 400：{}。若中转站不支持 structured output / effort / fallbacks，"
                    + "可分别设置 AI_STRUCTURED_OUTPUT=false、AI_EFFORT=、AI_REFUSAL_FALLBACK=false", t.getMessage());
            return StoryGenerationException.unavailable();
        }
        if (t instanceof RateLimitException) {
            log.warn("AI 限流（429）");
            return StoryGenerationException.unavailable();
        }
        if (t instanceof AnthropicServiceException e) {
            log.warn("AI 服务错误 {} {}", e.statusCode(), e.errorType().map(Object::toString).orElse(""));
            return StoryGenerationException.unavailable();
        }
        if (t instanceof AnthropicIoException) {
            if (isTimeout(t)) {
                log.warn("AI 请求超时");
                return StoryGenerationException.timeout();
            }
            log.warn("AI 网络错误：{}", rootMessage(t));
            return StoryGenerationException.unavailable();
        }
        if (isTimeout(t)) {
            log.warn("AI 请求超时");
            return StoryGenerationException.timeout();
        }
        log.warn("AI 调用异常：{}", t == null ? "null" : t.toString());
        return StoryGenerationException.unavailable();
    }

    private static boolean isTimeout(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof java.net.SocketTimeoutException || c instanceof java.io.InterruptedIOException
                    || c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            String m = c.getMessage();
            if (m != null && m.toLowerCase(Locale.ROOT).contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName() + ": " + c.getMessage();
    }

    private static String status(Throwable t) {
        return t instanceof AnthropicServiceException e ? String.valueOf(e.statusCode()) : "?";
    }

    private static OutputConfig.Effort parseEffort(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return switch (s.strip().toLowerCase(Locale.ROOT)) {
            case "low" -> OutputConfig.Effort.LOW;
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> throw new IllegalStateException("AI_EFFORT 只能是 low/medium/high/xhigh/max 或留空：" + s);
        };
    }

    private static JsonOutputFormat.Schema schema(JsonNode node) {
        JsonOutputFormat.Schema.Builder b = JsonOutputFormat.Schema.builder();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            b.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue()));
        }
        return b.build();
    }

    @Override
    public void close() {
        client.close();
    }
}
