package com.lifebranch.server.device.serial;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifebranch.server.model.AckStatus;
import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.DeviceStatus;
import com.lifebranch.server.model.Health;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class SerialDeviceBridgeTest {

    private final FakeFirmware fw = new FakeFirmware();
    private SerialDeviceBridge bridge;

    private static final SerialDeviceBridge.Timings FAST = new SerialDeviceBridge.Timings(
            Duration.ofMillis(100), Duration.ofMillis(400), Duration.ofMillis(900),
            Duration.ofMillis(100), Duration.ofMillis(300), Duration.ofMillis(600), Duration.ofMillis(300));

    @AfterEach
    void tearDown() {
        if (bridge != null) {
            bridge.close();
        }
    }

    private SerialDeviceBridge start() {
        bridge = new SerialDeviceBridge("COM-TEST", fw, FAST);
        bridge.start();
        return bridge;
    }

    @Test
    void handshakeThenDrawGoesAcceptedThenDone() {
        start();
        await(() -> status() == DeviceStatus.ONLINE, "握手");
        await(() -> fw.commandEvents().equals(List.of("RESET")), "握手后先发 RESET");

        bridge.send(DeviceEvent.DRAW);
        await(() -> info().lastEvent() == DeviceEvent.DRAW && info().lastAck() == AckStatus.DONE, "DRAW done");

        JsonNode cmd = lastCmd();
        assertThat(cmd.path("v").asInt()).isEqualTo(1);
        assertThat(cmd.path("bootId").asText()).isEqualTo("boot0001");
        assertThat(cmd.path("id").asText()).matches("[0-9a-f-]{36}");
        assertThat(cmd.fieldNames()).toIterable().containsExactlyInAnyOrder("type", "v", "bootId", "id", "event");
    }

    @Test
    void pingsWhenHelloWasMissed() {
        fw.helloOnOpen = false; // 打开串口时没有收到 hello（例如板子没有复位）
        start();
        await(() -> status() == DeviceStatus.ONLINE, "通过 ping 获得 hello");
        assertThat(fw.pings()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void offlineEventsAreDroppedNotQueued() {
        fw.failOpen = true;
        start();
        sleep(300);
        assertThat(status()).isEqualTo(DeviceStatus.OFFLINE);
        bridge.send(DeviceEvent.DRAW);
        bridge.send(DeviceEvent.STORY);

        fw.failOpen = false;
        await(() -> status() == DeviceStatus.ONLINE, "端口出现后自动连接");
        sleep(300);
        assertThat(fw.commandEvents()).containsExactly("RESET"); // 没有补发 DRAW / STORY
    }

    @Test
    void unplugGoesOfflineAndReconnectRestoresWithoutDraw() {
        AtomicInteger handshakes = new AtomicInteger();
        start();
        bridge.onHandshake(() -> {
            handshakes.incrementAndGet();
            bridge.send(DeviceEvent.STORY); // 模拟 SessionService 恢复当前画面
        });
        await(() -> status() == DeviceStatus.ONLINE, "握手");
        bridge.send(DeviceEvent.DRAW);
        await(() -> info().lastAck() == AckStatus.DONE, "DRAW done");

        fw.unplug();
        await(() -> status() == DeviceStatus.OFFLINE, "拔线后离线");
        fw.received.clear();

        await(() -> status() == DeviceStatus.ONLINE, "重连");
        await(() -> fw.commandEvents().equals(List.of("RESET", "STORY")), "重连后 RESET → 恢复画面");
        sleep(200);
        assertThat(fw.commandEvents()).doesNotContain("DRAW");
        assertThat(fw.opens).isGreaterThanOrEqualTo(2);
    }

    @Test
    void boardRebootIsDetectedAndOldBootAcksIgnored() {
        start();
        await(() -> status() == DeviceStatus.ONLINE, "握手");
        await(() -> fw.commandEvents().size() == 1, "首次 RESET");

        fw.reboot();
        await(() -> fw.commandEvents().size() >= 2, "重启后重新 RESET");
        assertThat(lastCmd().path("bootId").asText()).isEqualTo("boot0002");

        bridge.send(DeviceEvent.REFLECT);
        await(() -> info().lastEvent() == DeviceEvent.REFLECT && info().lastAck() == AckStatus.DONE, "新 bootId 正常");
    }

    @Test
    void missingAckBecomesUnknownAndIsNotResent() {
        start();
        await(() -> status() == DeviceStatus.ONLINE, "握手");
        await(() -> fw.commandEvents().size() == 1, "RESET");
        fw.mute = true;
        bridge.send(DeviceEvent.DRAW);
        await(() -> info().lastEvent() == DeviceEvent.DRAW && info().lastAck() == AckStatus.SENT, "已写出");
        await(() -> info().lastAck() == AckStatus.UNKNOWN, "无回执 → unknown");
        sleep(700);
        assertThat(fw.commandEvents().stream().filter("DRAW"::equals).count()).isEqualTo(1);
        assertThat(status()).isEqualTo(DeviceStatus.ONLINE); // 仍有其它输出（hello）时连接视为有效
    }

    @Test
    void silentDeviceIsMarkedOffline() {
        start();
        await(() -> status() == DeviceStatus.ONLINE, "握手");
        fw.silent = true; // 端口仍打开，但对 ping 也不再响应
        await(() -> status() == DeviceStatus.OFFLINE, "长时间无输出 → offline");
        fw.silent = false;
        await(() -> status() == DeviceStatus.ONLINE, "恢复输出后通过 ping 重新握手");
    }

    // ------------------------------------------------------------ 工具

    private Health.DeviceInfo info() {
        return bridge.info();
    }

    private DeviceStatus status() {
        return info().status();
    }

    private JsonNode lastCmd() {
        JsonNode n = lastCmdOrNull();
        assertThat(n).isNotNull();
        return n;
    }

    private JsonNode lastCmdOrNull() {
        synchronized (fw.received) {
            for (int i = fw.received.size() - 1; i >= 0; i--) {
                if ("cmd".equals(fw.received.get(i).path("type").asText())) {
                    return fw.received.get(i);
                }
            }
        }
        return null;
    }

    private static void await(BooleanSupplier cond, String what) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            sleep(20);
        }
        throw new AssertionError("等待超时：" + what);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
