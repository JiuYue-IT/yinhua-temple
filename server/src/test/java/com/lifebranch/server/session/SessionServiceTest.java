package com.lifebranch.server.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.device.DryRunDeviceBridge;
import com.lifebranch.server.error.ApiException;
import com.lifebranch.server.error.ErrorCodes;
import com.lifebranch.server.model.CreateSessionRequest;
import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.DeviceMode;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.model.ReceiptDraft;
import com.lifebranch.server.model.ReceiptRequest;
import com.lifebranch.server.model.SessionMode;
import com.lifebranch.server.model.SessionSnapshot;
import com.lifebranch.server.model.SessionStatus;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.model.StoryOption;
import com.lifebranch.server.story.PresetCatalog;
import com.lifebranch.server.story.PresetStoryProvider;
import com.lifebranch.server.story.StoryGenerationException;
import com.lifebranch.server.story.StoryProvider;
import com.lifebranch.server.story.StoryValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {

    private static final Input LIVE_INPUT = new Input("  我在两份工作之间犹豫。 ", "留在本地", "去外地工作", "希望有成长空间");

    private DryRunDeviceBridge device;
    private SessionService service;
    private ScriptedProvider live;

    @BeforeEach
    void setUp() throws Exception {
        device = new DryRunDeviceBridge();
        live = new ScriptedProvider();
        service = newService(live, true, 30);
    }

    @AfterEach
    void tearDown() {
        live.releaseIfBlocked();
        service.shutdown();
    }

    private SessionService newService(StoryProvider provider, boolean aiConfigured, int taskTimeoutSeconds)
            throws Exception {
        AppProperties props = new AppProperties(
                new AppProperties.Ai(aiConfigured ? "http://ai" : null, aiConfigured ? "k" : null,
                        aiConfigured ? "m" : null, 25, taskTimeoutSeconds),
                new AppProperties.Device(DeviceMode.DRYRUN, null),
                new AppProperties.Preset(0));
        StoryValidator validator = new StoryValidator();
        PresetCatalog catalog = new PresetCatalog(new ObjectMapper(), validator);
        return new SessionService(props, catalog, new PresetStoryProvider(props), provider, validator, device);
    }

    // ------------------------------------------------------------ 创建与幂等

    @Test
    void sameRequestIdOnlyCreatesOneSessionAndOneDraw() {
        CreateSessionRequest req = preset(uuid());
        SessionSnapshot a = service.create(req);
        SessionSnapshot b = service.create(req);
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(count(DeviceEvent.DRAW)).isEqualTo(1);
    }

    @Test
    void sameRequestIdWithDifferentContentConflicts() {
        String id = uuid();
        service.create(live(id, LIVE_INPUT));
        Input other = new Input("别的背景", "留在本地", "去外地工作", "希望有成长空间");
        assertCode(() -> service.create(live(id, other)), ErrorCodes.REQUEST_CONFLICT);
    }

    @Test
    void retryWithOnlyWhitespaceDifferenceIsSameRequest() {
        String id = uuid();
        SessionSnapshot a = service.create(live(id, LIVE_INPUT));
        SessionSnapshot b = service.create(live(id, LIVE_INPUT.trimmed()));
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(a.input().background()).isEqualTo("我在两份工作之间犹豫。");
    }

    @Test
    void secondSessionIsBusyEvenAfterComplete() {
        service.create(preset(uuid()));
        assertCode(() -> service.create(preset(uuid())), ErrorCodes.SESSION_BUSY);
        assertThat(count(DeviceEvent.DRAW)).isEqualTo(1);
    }

    @Test
    void presetRejectsCustomInputAndUsesCaseInput() {
        assertCode(() -> service.create(new CreateSessionRequest(uuid(), SessionMode.PRESET, "team-project", LIVE_INPUT)),
                ErrorCodes.VALIDATION_ERROR);
        assertCode(() -> service.create(new CreateSessionRequest(uuid(), SessionMode.PRESET, "nope", null)),
                ErrorCodes.VALIDATION_ERROR);
        assertThat(device.history()).isEmpty();

        SessionSnapshot s = service.create(preset(uuid()));
        assertThat(s.mode()).isEqualTo(SessionMode.PRESET);
        assertThat(s.input().chosenPath()).isEqualTo("拒绝邀请");
    }

    @Test
    void liveWithoutAiConfigIs503AndNoDraw() throws Exception {
        service.shutdown();
        service = newService(live, false, 30);
        assertThatThrownBy(() -> service.create(live(uuid(), LIVE_INPUT)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(ErrorCodes.AI_NOT_CONFIGURED);
                    assertThat(((ApiException) e).status().value()).isEqualTo(503);
                });
        assertThat(device.history()).isEmpty();
        // 预置模式不受影响
        assertThat(service.create(preset(uuid())).status()).isEqualTo(SessionStatus.GENERATING);
    }

    // ------------------------------------------------------------ 完整流程

    @Test
    void presetFlowReflectThenSwitchToBThenReceiptMatchesB() {
        SessionSnapshot s = service.create(preset(uuid()));
        s = awaitStatus(s.id(), SessionStatus.READY);
        assertThat(s.story().title()).isEqualTo("同行");

        s = service.choose(s.id(), "A");
        assertThat(s.status()).isEqualTo(SessionStatus.REFLECTING);
        assertThat(s.selectedOptionId()).isEqualTo("A");

        service.choose(s.id(), "A"); // 重复提交不重复发事件
        s = service.choose(s.id(), "B");
        assertThat(s.status()).isEqualTo(SessionStatus.ENDING);

        ReceiptDraft draftB = s.story().option("B").receiptDraft();
        s = service.confirmReceipt(s.id(), new ReceiptRequest(draftB.insight(), draftB.nextStep()));
        assertThat(s.status()).isEqualTo(SessionStatus.COMPLETE);
        assertThat(s.receipt().insight()).isEqualTo(draftB.insight());
        assertThat(s.receipt().chosenPath()).isEqualTo("拒绝邀请");
        assertThat(s.receipt().unchosenPath()).isEqualTo("接受邀请，负责一个小模块");
        assertThat(s.receipt().mode()).isEqualTo(SessionMode.PRESET);

        assertThat(device.history()).containsExactly(
                DeviceEvent.DRAW, DeviceEvent.STORY, DeviceEvent.REFLECT, DeviceEvent.STORY, DeviceEvent.RECEIPT);
    }

    @Test
    void receiptIsIdempotentAndThenLocked() {
        SessionSnapshot s = readyPreset();
        service.choose(s.id(), "B");
        ReceiptRequest r = new ReceiptRequest(" 我的发现 ", "我的下一步");
        service.confirmReceipt(s.id(), r);
        service.confirmReceipt(s.id(), r);
        assertThat(count(DeviceEvent.RECEIPT)).isEqualTo(1);
        assertCode(() -> service.confirmReceipt(s.id(), new ReceiptRequest("改了", "我的下一步")),
                ErrorCodes.RECEIPT_ALREADY_CONFIRMED);
        assertCode(() -> service.choose(s.id(), "A"), ErrorCodes.INVALID_STATE);
    }

    @Test
    void invalidOptionAndStateChecks() {
        live.block();
        SessionSnapshot g = service.create(live(uuid(), LIVE_INPUT));
        assertCode(() -> service.choose(g.id(), "A"), ErrorCodes.INVALID_STATE);
        assertCode(() -> service.confirmReceipt(g.id(), new ReceiptRequest("a", "b")), ErrorCodes.INVALID_STATE);
        live.release(validStory(false));
        awaitStatus(g.id(), SessionStatus.READY);
        assertCode(() -> service.choose(g.id(), "C"), ErrorCodes.INVALID_OPTION);
        assertCode(() -> service.confirmReceipt(g.id(), new ReceiptRequest("a", "b")), ErrorCodes.INVALID_STATE);
        assertCode(() -> service.get("s-unknown"), ErrorCodes.SESSION_NOT_FOUND);
    }

    @Test
    void liveStoryWithoutReflectionGoesToEnding() {
        live.respond(validStory(false));
        SessionSnapshot s = service.create(live(uuid(), LIVE_INPUT));
        s = awaitStatus(s.id(), SessionStatus.READY);
        s = service.choose(s.id(), "A");
        assertThat(s.status()).isEqualTo(SessionStatus.ENDING);
        assertThat(device.history()).containsExactly(DeviceEvent.DRAW, DeviceEvent.STORY, DeviceEvent.STORY);
    }

    // ------------------------------------------------------------ 失败

    @Test
    void providerErrorsBecomeErrorSnapshot() {
        live.fail(StoryGenerationException.unavailable());
        SessionSnapshot s = service.create(live(uuid(), LIVE_INPUT));
        s = awaitStatus(s.id(), SessionStatus.ERROR);
        assertThat(s.error().code()).isEqualTo(ErrorCodes.AI_UNAVAILABLE);
        assertThat(s.story()).isNull();
        assertThat(device.history()).containsExactly(DeviceEvent.DRAW, DeviceEvent.ERROR);
    }

    @Test
    void invalidStoryStructureBecomesFormatError() {
        Story broken = new Story("题", "问", List.of("假设"), "开场", "决定",
                List.of(new StoryOption("A", "a", "o", null, new ReceiptDraft("i", "n"))));
        live.respond(broken);
        SessionSnapshot s = service.create(live(uuid(), LIVE_INPUT));
        assertThat(awaitStatus(s.id(), SessionStatus.ERROR).error().code()).isEqualTo(ErrorCodes.AI_FORMAT_ERROR);
    }

    @Test
    void taskTimeoutBecomesAiTimeout() throws Exception {
        service.shutdown();
        service = newService(live, true, 1);
        live.block();
        SessionSnapshot s = service.create(live(uuid(), LIVE_INPUT));
        s = awaitStatus(s.id(), SessionStatus.ERROR, Duration.ofSeconds(4));
        assertThat(s.error().code()).isEqualTo(ErrorCodes.AI_TIMEOUT);
        assertThat(live.interrupted.await(2, TimeUnit.SECONDS)).as("超时后后台任务被中断").isTrue();
        assertThat(device.history()).containsExactly(DeviceEvent.DRAW, DeviceEvent.ERROR);
    }

    // ------------------------------------------------------------ 设备重连

    @Test
    void reconnectRestoresCurrentDisplayButNeverDraw() {
        service.restoreDeviceDisplay(); // 空闲：什么都不发
        assertThat(device.history()).isEmpty();

        live.block();
        SessionSnapshot s = service.create(live(uuid(), LIVE_INPUT));
        service.restoreDeviceDisplay(); // generating：保持待机，不重放 DRAW
        assertThat(device.history()).containsExactly(DeviceEvent.DRAW);

        live.release(validStory(true));
        awaitStatus(s.id(), SessionStatus.READY);
        device.clearHistory();
        service.restoreDeviceDisplay();
        service.choose(s.id(), "A");
        service.restoreDeviceDisplay();
        service.confirmReceipt(s.id(), new ReceiptRequest("发现", "下一步"));
        service.restoreDeviceDisplay();
        assertThat(device.history()).containsExactly(DeviceEvent.STORY, DeviceEvent.REFLECT, DeviceEvent.REFLECT,
                DeviceEvent.RECEIPT, DeviceEvent.RECEIPT);
    }

    // ------------------------------------------------------------ 重置隔离

    @Test
    void lateResultAfterResetDoesNotPolluteNewSession() throws Exception {
        live.block();
        SessionSnapshot old = service.create(live(uuid(), LIVE_INPUT));
        service.reset();
        assertCode(() -> service.get(old.id()), ErrorCodes.SESSION_NOT_FOUND);

        SessionSnapshot fresh = service.create(preset(uuid()));
        awaitStatus(fresh.id(), SessionStatus.READY);
        device.clearHistory();

        live.release(validStory(true)); // 旧任务（若未被中断）迟到返回
        Thread.sleep(200);
        SessionSnapshot now = service.get(fresh.id());
        assertThat(now.mode()).isEqualTo(SessionMode.PRESET);
        assertThat(now.story().title()).isEqualTo("同行");
        assertThat(device.history()).isEmpty(); // 没有旧 STORY/ERROR
    }

    @Test
    void resetExpiresOldRequestIdAndNeverDraws() {
        String id = uuid();
        service.create(preset(id));
        service.reset();
        service.reset();
        assertCode(() -> service.create(preset(id)), ErrorCodes.REQUEST_EXPIRED);
        assertThat(service.currentSnapshot()).isNull();
        assertThat(device.history()).containsExactly(DeviceEvent.DRAW, DeviceEvent.RESET, DeviceEvent.RESET);
    }

    // ------------------------------------------------------------ 工具

    private SessionSnapshot readyPreset() {
        return awaitStatus(service.create(preset(uuid())).id(), SessionStatus.READY);
    }

    private SessionSnapshot awaitStatus(String id, SessionStatus status) {
        return awaitStatus(id, status, Duration.ofSeconds(3));
    }

    private SessionSnapshot awaitStatus(String id, SessionStatus status, Duration max) {
        long deadline = System.nanoTime() + max.toNanos();
        SessionSnapshot s;
        do {
            s = service.get(id);
            if (s.status() == status) {
                return s;
            }
            sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("等待状态 " + status + " 超时，当前 " + s.status());
    }

    private long count(DeviceEvent e) {
        return device.history().stream().filter(e::equals).count();
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }

    private static CreateSessionRequest preset(String id) {
        return new CreateSessionRequest(id, SessionMode.PRESET, "team-project", null);
    }

    private static CreateSessionRequest live(String id, Input input) {
        return new CreateSessionRequest(id, SessionMode.LIVE, null, input);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static Story validStory(boolean withReflection) {
        return new Story("远行", "你想去的是那个地方，还是离开现在？", List.of("假设新工作如期入职"),
                "你去了外地。", "第一个月末，你会怎么做？",
                List.of(new StoryOption("A", "继续适应", "你逐渐熟悉了节奏。",
                                withReflection ? new com.lifebranch.server.model.Reflection("目标", "后果", "替代") : null,
                                new ReceiptDraft("发现A", "下一步A")),
                        new StoryOption("B", "回来", "你回到了原来的城市。", null,
                                new ReceiptDraft("发现B", "下一步B"))));
    }

    /** 可控的假 AI：立即返回、立即失败，或阻塞到 release。 */
    static final class ScriptedProvider implements StoryProvider {
        private volatile Supplier<Story> answer = () -> validStory(false);
        private volatile StoryGenerationException error;
        private volatile CountDownLatch gate;
        final CountDownLatch interrupted = new CountDownLatch(1);

        void respond(Story s) {
            error = null;
            answer = () -> s;
        }

        void fail(StoryGenerationException e) {
            error = e;
        }

        void block() {
            gate = new CountDownLatch(1);
        }

        void release(Story s) {
            respond(s);
            gate.countDown();
        }

        void releaseIfBlocked() {
            CountDownLatch g = gate;
            if (g != null) {
                g.countDown();
            }
        }

        @Override
        public Story generate(Input input) throws StoryGenerationException, InterruptedException {
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw e;
                }
            }
            if (error != null) {
                throw error;
            }
            return answer.get();
        }
    }
}
