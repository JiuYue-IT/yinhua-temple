package com.lifebranch.server.session;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.device.DeviceBridge;
import com.lifebranch.server.error.ApiException;
import com.lifebranch.server.error.ErrorCodes;
import com.lifebranch.server.model.ApiError;
import com.lifebranch.server.model.CreateSessionRequest;
import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.Receipt;
import com.lifebranch.server.model.ReceiptRequest;
import com.lifebranch.server.model.SessionMode;
import com.lifebranch.server.model.SessionSnapshot;
import com.lifebranch.server.model.SessionStatus;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.model.StoryOption;
import com.lifebranch.server.story.PresetCase;
import com.lifebranch.server.story.PresetCatalog;
import com.lifebranch.server.story.PresetStoryProvider;
import com.lifebranch.server.story.StoryGenerationException;
import com.lifebranch.server.story.StoryProvider;
import com.lifebranch.server.story.StoryValidator;
import com.lifebranch.server.validation.TextRules;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单会话状态机（文档 04 §4、§6）。
 *
 * <ul>
 *   <li>所有状态转换与设备事件在同一把锁内发生，保证顺序一致；DeviceBridge.send 必须非阻塞。</li>
 *   <li>后台生成任务捕获 generation 代号；写回前确认仍是当前代号且仍在 generating，否则丢弃（防迟到回写）。</li>
 *   <li>reset 先使旧任务失效，再清数据并发送 RESET；旧 requestId 记为过期。</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int EXPIRED_REQUEST_IDS_MAX = 256;

    private final AppProperties props;
    private final PresetCatalog presets;
    private final PresetStoryProvider presetProvider;
    private final StoryProvider liveProvider;
    private final StoryValidator validator;
    private final DeviceBridge device;

    private final ExecutorService workers = Executors.newCachedThreadPool(daemon("story-worker"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(daemon("story-timeout"));

    private final Object lock = new Object();
    /** 当前活动会话；null 表示空闲。 */
    private Session current;
    /** 每次创建或重置都递增，用于识别过期的后台任务。 */
    private long generation;
    /** 已重置会话的 requestId，只保存 ID，不保存输入内容。 */
    private final Set<String> expiredRequestIds = new LinkedHashSet<>();

    public SessionService(AppProperties props, PresetCatalog presets, PresetStoryProvider presetProvider,
                          StoryProvider liveProvider, StoryValidator validator, DeviceBridge device) {
        this.props = props;
        this.presets = presets;
        this.presetProvider = presetProvider;
        this.liveProvider = liveProvider;
        this.validator = validator;
        this.device = device;
    }

    // ------------------------------------------------------------------ 创建

    /**
     * POST /api/sessions。新建或幂等重发命中时都返回当前快照（HTTP 202）；
     * 只有真正新建时发送一次 DRAW。校验失败、AI 未配置或已有其他会话时不发 DRAW。
     */
    public SessionSnapshot create(CreateSessionRequest req) {
        Input input;
        PresetCase presetCase = null;
        if (req.mode() == SessionMode.PRESET) {
            if (req.input() != null) {
                throw ApiException.validation("预置案例模式不接收自定义输入。");
            }
            presetCase = presets.find(req.caseId())
                    .orElseThrow(() -> ApiException.validation("未知的预置案例：" + req.caseId()));
            input = presetCase.input();
        } else {
            if (req.input() == null) {
                throw ApiException.validation("请填写背景、已选道路、未选道路和目标。");
            }
            if (req.caseId() != null) {
                throw ApiException.validation("自定义模式不接收 caseId。");
            }
            input = req.input().trimmed();
        }
        Fingerprint fp = new Fingerprint(req.mode(), presetCase == null ? null : presetCase.caseId(), input);

        synchronized (lock) {
            if (current != null && current.requestId.equals(req.requestId())) {
                if (current.fingerprint.equals(fp)) {
                    return current.snapshot();
                }
                throw ApiException.conflict(ErrorCodes.REQUEST_CONFLICT, "同一请求编号对应了不同的内容。");
            }
            if (expiredRequestIds.contains(req.requestId())) {
                throw ApiException.conflict(ErrorCodes.REQUEST_EXPIRED, "这个请求属于已结束的体验，请重新开始。");
            }
            if (current != null) {
                throw ApiException.conflict(ErrorCodes.SESSION_BUSY, "当前还有一段体验未结束，请先重置。");
            }
            if (req.mode() == SessionMode.LIVE && !props.ai().configured()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.AI_NOT_CONFIGURED,
                        "AI 服务未配置，可以选择预置案例演示。");
            }

            long gen = ++generation;
            Session s = new Session(newSessionId(), req.requestId(), req.mode(), input, fp, gen);
            current = s;
            device.send(DeviceEvent.DRAW);
            startGeneration(s, presetCase);
            log.info("会话 {} 已创建（mode={}）", s.id, s.mode.json());
            return s.snapshot();
        }
    }

    private void startGeneration(Session s, PresetCase presetCase) {
        final long gen = s.generation;
        final Input input = s.input;
        Future<?> task = workers.submit(() -> {
            try {
                Story story = presetCase != null ? presetProvider.load(presetCase) : liveProvider.generate(input);
                List<String> problems = validator.validate(story);
                if (!problems.isEmpty()) {
                    // 只记录字段问题，不记录模型原文与用户背景
                    log.warn("生成结果未通过校验：{}", problems);
                    fail(gen, StoryGenerationException.formatError().toApiError());
                    return;
                }
                succeed(gen, story);
            } catch (StoryGenerationException e) {
                fail(gen, e.toApiError());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 已超时或已重置，由对应路径处理
            } catch (RuntimeException e) {
                log.warn("生成任务异常：{}", e.toString());
                fail(gen, StoryGenerationException.unavailable().toApiError());
            }
        });
        ScheduledFuture<?> timeout = timer.schedule(() -> {
            if (fail(gen, StoryGenerationException.timeout().toApiError())) {
                task.cancel(true);
            }
        }, props.ai().taskTimeoutSeconds(), TimeUnit.SECONDS);
        s.task = task;
        s.timeout = timeout;
    }

    /** @return 是否确实写回（false 表示任务已过期或会话不再 generating）。 */
    private boolean succeed(long gen, Story story) {
        synchronized (lock) {
            Session s = activeGenerating(gen);
            if (s == null) {
                return false;
            }
            s.story = story;
            s.status = SessionStatus.READY;
            s.cancelTimers(false);
            device.send(DeviceEvent.STORY);
            return true;
        }
    }

    private boolean fail(long gen, ApiError error) {
        synchronized (lock) {
            Session s = activeGenerating(gen);
            if (s == null) {
                return false;
            }
            s.error = error;
            s.status = SessionStatus.ERROR;
            s.cancelTimers(false);
            device.send(DeviceEvent.ERROR);
            log.info("会话 {} 生成失败：{}", s.id, error.code());
            return true;
        }
    }

    private Session activeGenerating(long gen) {
        Session s = current;
        if (s == null || s.generation != gen || gen != generation || s.status != SessionStatus.GENERATING) {
            return null;
        }
        return s;
    }

    // ------------------------------------------------------------------ 查询

    public SessionSnapshot get(String id) {
        synchronized (lock) {
            return require(id).snapshot();
        }
    }

    /** 当前会话快照（设备重连恢复显示等用途），无会话时为 null。 */
    public SessionSnapshot currentSnapshot() {
        synchronized (lock) {
            return current == null ? null : current.snapshot();
        }
    }

    // ------------------------------------------------------------------ 选择

    public SessionSnapshot choose(String id, String optionId) {
        synchronized (lock) {
            Session s = require(id);
            if (s.status != SessionStatus.READY && s.status != SessionStatus.REFLECTING
                    && s.status != SessionStatus.ENDING) {
                throw ApiException.conflict(ErrorCodes.INVALID_STATE, stateMessage(s.status));
            }
            StoryOption option = s.story.option(optionId);
            if (option == null) {
                throw ApiException.badRequest(ErrorCodes.INVALID_OPTION, "选项不存在。");
            }
            if (optionId.equals(s.selectedOptionId)) {
                return s.snapshot(); // 重复提交同一选项，不重复发事件
            }
            s.selectedOptionId = optionId;
            if (option.reflection() != null) {
                s.status = SessionStatus.REFLECTING;
                device.send(DeviceEvent.REFLECT);
            } else {
                s.status = SessionStatus.ENDING;
                device.send(DeviceEvent.STORY);
            }
            return s.snapshot();
        }
    }

    // ------------------------------------------------------------------ 收据

    public SessionSnapshot confirmReceipt(String id, ReceiptRequest req) {
        String insight = TextRules.trim(req.insight());
        String nextStep = TextRules.trim(req.nextStep());
        synchronized (lock) {
            Session s = require(id);
            if (s.status == SessionStatus.COMPLETE) {
                if (s.receipt.insight().equals(insight) && s.receipt.nextStep().equals(nextStep)) {
                    return s.snapshot();
                }
                throw ApiException.conflict(ErrorCodes.RECEIPT_ALREADY_CONFIRMED, "收据已确认，不能再修改。");
            }
            if (s.status != SessionStatus.REFLECTING && s.status != SessionStatus.ENDING) {
                throw ApiException.conflict(ErrorCodes.INVALID_STATE, stateMessage(s.status));
            }
            s.receipt = new Receipt(s.id, Instant.now(), s.mode, s.input.chosenPath(), s.input.unchosenPath(),
                    List.copyOf(s.story.assumptions()), insight, nextStep);
            s.status = SessionStatus.COMPLETE;
            device.send(DeviceEvent.RECEIPT);
            return s.snapshot();
        }
    }

    // ------------------------------------------------------------------ 重置

    public void reset() {
        synchronized (lock) {
            generation++; // 先使旧任务失效
            if (current != null) {
                current.cancelTimers(true);
                rememberExpired(current.requestId);
                log.info("会话 {} 已重置", current.id);
                current = null;
            }
            device.send(DeviceEvent.RESET);
        }
    }

    // ------------------------------------------------------------------ 内部

    private Session require(String id) {
        Session s = current;
        if (s == null || !s.id.equals(id)) {
            throw ApiException.notFound();
        }
        return s;
    }

    private void rememberExpired(String requestId) {
        expiredRequestIds.add(requestId);
        if (expiredRequestIds.size() > EXPIRED_REQUEST_IDS_MAX) {
            Iterator<String> it = expiredRequestIds.iterator();
            it.next();
            it.remove();
        }
    }

    private static String stateMessage(SessionStatus status) {
        return switch (status) {
            case GENERATING -> "故事还在生成中，请稍候。";
            case COMPLETE -> "本次体验已完成，请重置后重新开始。";
            case ERROR -> "本次生成失败，请重置后重试。";
            default -> "当前状态不允许这个操作。";
        };
    }

    private static String newSessionId() {
        return "s-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    @PreDestroy
    public void shutdown() {
        workers.shutdownNow();
        timer.shutdownNow();
    }

    /** 判断重发内容是否相同：预置按 caseId，自定义按去空白后的输入。 */
    private record Fingerprint(SessionMode mode, String caseId, Input input) {
    }

    private static final class Session {
        final String id;
        final String requestId;
        final SessionMode mode;
        final Input input;
        final Fingerprint fingerprint;
        final long generation;

        SessionStatus status = SessionStatus.GENERATING;
        Story story;
        String selectedOptionId;
        Receipt receipt;
        ApiError error;
        Future<?> task;
        ScheduledFuture<?> timeout;

        Session(String id, String requestId, SessionMode mode, Input input, Fingerprint fingerprint, long generation) {
            this.id = id;
            this.requestId = requestId;
            this.mode = mode;
            this.input = input;
            this.fingerprint = fingerprint;
            this.generation = generation;
        }

        void cancelTimers(boolean interruptTask) {
            if (timeout != null) {
                timeout.cancel(false);
            }
            if (interruptTask && task != null) {
                task.cancel(true);
            }
        }

        SessionSnapshot snapshot() {
            return new SessionSnapshot(id, mode, status, input, story, selectedOptionId, receipt, error);
        }
    }
}
