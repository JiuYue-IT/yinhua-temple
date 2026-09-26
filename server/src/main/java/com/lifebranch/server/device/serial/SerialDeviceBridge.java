package com.lifebranch.server.device.serial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifebranch.server.device.DeviceBridge;
import com.lifebranch.server.model.AckStatus;
import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.DeviceMode;
import com.lifebranch.server.model.DeviceStatus;
import com.lifebranch.server.model.Health;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * USB 串口桥接（文档 03 §5、文档 04 §6）。
 *
 * <ul>
 *   <li>IO 线程：打开串口 → 读行 → 解析 hello/ack/error/log；未握手时每隔 pingInterval 发 ping；
 *       在线但长时间无任何输出时发 ping，超过 silenceLimit 视为离线；读写失败则关闭并重连。</li>
 *   <li>写线程：按顺序写出命令；命令带当前 bootId 和新 UUID；连接代号变化的旧命令直接丢弃。</li>
 *   <li>计时线程：约 1 秒无 accepted → unknown；done 超时（DRAW 5 秒）→ unknown。绝不自动重发。</li>
 *   <li>每次（重新）握手：先发 RESET，再回调 onHandshake 恢复当前画面（调用方保证不重放 DRAW）。</li>
 *   <li>离线时 send 直接丢弃事件，不排队补发。</li>
 * </ul>
 */
public class SerialDeviceBridge implements DeviceBridge {

    private static final Logger log = LoggerFactory.getLogger(SerialDeviceBridge.class);
    public static final int MAX_LINE_BYTES = 512;
    private static final int HISTORY_MAX = 32;

    /** 各种时限；测试中可缩短。 */
    public record Timings(Duration pingInterval, Duration silencePing, Duration silenceLimit,
                          Duration reconnectDelay, Duration acceptTimeout, Duration drawDoneTimeout,
                          Duration displayDoneTimeout) {
        public static Timings defaults() {
            return new Timings(Duration.ofMillis(1500), Duration.ofSeconds(5), Duration.ofSeconds(12),
                    Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(2));
        }
    }

    private final String portName;
    private final SerialTransportFactory factory;
    private final Timings t;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ExecutorService writer = Executors.newSingleThreadExecutor(daemon("serial-writer"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(daemon("serial-timer"));
    private Thread ioThread;
    private volatile boolean running;
    private volatile Runnable handshakeListener = () -> { };

    private final Object writeLock = new Object();
    private final Object state = new Object();
    // ---- 以下字段受 state 保护
    private SerialTransport transport;
    private long epoch;          // 每次打开串口递增
    private String bootId;       // null = 未握手（离线）
    private long lastRxNanos;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private Command lastCommand;

    public SerialDeviceBridge(String portName, SerialTransportFactory factory, Timings timings) {
        this.portName = portName;
        this.factory = factory;
        this.t = timings;
    }

    // ================================================================ DeviceBridge

    @Override
    public void onHandshake(Runnable listener) {
        this.handshakeListener = listener == null ? () -> { } : listener;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        ioThread = new Thread(this::ioLoop, "serial-io");
        ioThread.setDaemon(true);
        ioThread.start();
        log.info("串口桥接已启动，端口 {}", portName);
    }

    @Override
    public void send(DeviceEvent event) {
        long ep;
        synchronized (state) {
            if (bootId == null || transport == null) {
                log.info("设备离线，丢弃事件 {}（不补发）", event);
                return;
            }
            ep = epoch;
        }
        String id = UUID.randomUUID().toString();
        writer.execute(() -> writeCommand(ep, id, event));
    }

    @Override
    public Health.DeviceInfo info() {
        synchronized (state) {
            DeviceStatus status = (bootId != null && transport != null) ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE;
            return new Health.DeviceInfo(DeviceMode.SERIAL, status,
                    lastCommand == null ? null : lastCommand.event,
                    lastCommand == null ? null : lastCommand.status);
        }
    }

    @Override
    public void close() {
        running = false;
        if (ioThread != null) {
            ioThread.interrupt();
        }
        writer.shutdownNow();
        timer.shutdownNow();
        closeTransport(null);
    }

    // ================================================================ 写

    private void writeCommand(long ep, String id, DeviceEvent event) {
        SerialTransport tr;
        String boot;
        synchronized (state) {
            if (ep != epoch || bootId == null || transport == null) {
                log.info("连接已变化，丢弃旧事件 {}", event);
                return;
            }
            tr = transport;
            boot = bootId;
        }
        ObjectNode cmd = mapper.createObjectNode();
        cmd.put("type", "cmd").put("v", 1).put("bootId", boot).put("id", id).put("event", event.name());
        Command c = new Command(id, event, boot);
        synchronized (state) {
            commands.put(id, c);
            if (commands.size() > HISTORY_MAX) {
                commands.remove(commands.keySet().iterator().next());
            }
            lastCommand = c;
        }
        if (!writeLine(tr, cmd.toString())) {
            synchronized (state) {
                c.status = AckStatus.UNKNOWN;
            }
            closeTransport(tr);
            return;
        }
        log.info("→ 设备 {} ({})", event, shortId(id));
        timer.schedule(() -> expire(c, AckStatus.SENT), t.acceptTimeout().toMillis(), TimeUnit.MILLISECONDS);
        Duration done = event == DeviceEvent.DRAW ? t.drawDoneTimeout() : t.displayDoneTimeout();
        timer.schedule(() -> expire(c, null), done.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** onlyIf 为 null 时：只要还没有终态就标 unknown；否则只在当前状态等于 onlyIf 时标 unknown。 */
    private void expire(Command c, AckStatus onlyIf) {
        synchronized (state) {
            if (c.status.isTerminal()) {
                return;
            }
            if (onlyIf != null && c.status != onlyIf) {
                return;
            }
            if (c.status != AckStatus.UNKNOWN) {
                log.warn("设备未及时回执 {} ({})，状态 {} → unknown", c.event, shortId(c.id), c.status.json());
            }
            c.status = AckStatus.UNKNOWN;
        }
    }

    private boolean writeLine(SerialTransport tr, String json) {
        byte[] bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            return tr.write(bytes) == bytes.length;
        }
    }

    // ================================================================ IO 循环

    private void ioLoop() {
        byte[] buf = new byte[256];
        boolean warnedOpen = false;
        while (running) {
            SerialTransport tr;
            try {
                tr = factory.open(portName);
                warnedOpen = false;
            } catch (IOException | RuntimeException e) {
                if (!warnedOpen) {
                    log.warn("串口 {} 暂不可用：{}（每 {} 秒重试）", portName, e.getMessage(),
                            t.reconnectDelay().toSeconds());
                    warnedOpen = true;
                }
                if (!sleep(t.reconnectDelay())) {
                    return;
                }
                continue;
            }
            synchronized (state) {
                transport = tr;
                epoch++;
                bootId = null;
                lastRxNanos = System.nanoTime();
            }
            log.info("串口 {} 已打开，等待设备 hello", portName);
            readLoop(tr, buf);
            closeTransport(tr);
            if (running) {
                log.warn("串口连接中断，设备离线；稍后重连");
                if (!sleep(t.reconnectDelay())) {
                    return;
                }
            }
        }
    }

    private void readLoop(SerialTransport tr, byte[] buf) {
        LineFramer framer = new LineFramer(MAX_LINE_BYTES);
        long lastPing = System.nanoTime();
        while (running) {
            int n = tr.read(buf);
            if (n < 0) {
                return;
            }
            if (n > 0) {
                synchronized (state) {
                    lastRxNanos = System.nanoTime();
                }
                for (String line : framer.feed(buf, n)) {
                    handleLine(tr, line);
                }
                int over = framer.takeOverflows();
                if (over > 0) {
                    log.warn("设备输出行超过 {} 字节，已丢弃 {} 行", MAX_LINE_BYTES, over);
                }
            }
            long now = System.nanoTime();
            boolean handshaken;
            long silent;
            synchronized (state) {
                if (transport != tr) {
                    return; // 已被写失败关闭
                }
                handshaken = bootId != null;
                silent = now - lastRxNanos;
                if (handshaken && silent > t.silenceLimit().toNanos()) {
                    log.warn("设备 {} 秒无响应，标记为离线", t.silenceLimit().toSeconds());
                    bootId = null;
                    handshaken = false;
                }
            }
            // 未握手：定期 ping 获取 hello；已握手：安静太久才 ping 探活
            boolean wantPing = !handshaken || silent > t.silencePing().toNanos();
            if (wantPing && now - lastPing > t.pingInterval().toNanos()) {
                if (!writeLine(tr, "{\"type\":\"ping\"}")) {
                    return;
                }
                lastPing = now;
            }
        }
    }

    private void handleLine(SerialTransport tr, String line) {
        JsonNode msg;
        try {
            msg = mapper.readTree(line);
        } catch (IOException e) {
            // ESP32 复位时 ROM 会以 115200 打印启动信息，属正常现象
            log.debug("忽略非 JSON 输出：{}", truncate(line));
            return;
        }
        if (msg == null || !msg.isObject()) {
            return;
        }
        String type = msg.path("type").asText("");
        switch (type) {
            case "hello" -> onHello(tr, msg);
            case "ack" -> onAck(msg);
            case "error" -> log.warn("设备报告错误：{}", msg.path("code").asText("?"));
            case "log" -> log.debug("[设备] {}", truncate(msg.path("message").asText("")));
            default -> log.debug("忽略未知消息类型：{}", type);
        }
    }

    private void onHello(SerialTransport tr, JsonNode msg) {
        String newBoot = msg.path("bootId").asText("");
        if (newBoot.isEmpty()) {
            return;
        }
        synchronized (state) {
            if (transport != tr) {
                return;
            }
            if (newBoot.equals(bootId)) {
                return; // ping 的回应，连接依然有效
            }
            String old = bootId;
            bootId = newBoot;
            log.info("设备握手成功：device={} bootId={}{}", msg.path("device").asText("?"), newBoot,
                    old == null ? "" : "（设备已重启，原 bootId=" + old + "）");
        }
        // 重连：先停止执行器，再恢复当前画面（调用方保证不重放 DRAW）
        send(DeviceEvent.RESET);
        try {
            handshakeListener.run();
        } catch (RuntimeException e) {
            log.warn("恢复设备画面失败：{}", e.toString());
        }
    }

    private void onAck(JsonNode msg) {
        String id = msg.path("id").asText("");
        String boot = msg.path("bootId").asText("");
        AckStatus status = parseAck(msg.path("status").asText(""));
        String code = msg.path("code").asText(null);
        boolean needPing = false;
        synchronized (state) {
            if (bootId == null || !bootId.equals(boot)) {
                log.debug("忽略旧 bootId 的回执 {}", shortId(id));
                return;
            }
            Command c = commands.get(id);
            if (c == null || status == null) {
                return;
            }
            if (!c.status.isTerminal()) {
                c.status = status;
            }
            if (status == AckStatus.ERROR) {
                log.warn("设备拒绝 {} ({})：{}", c.event, shortId(id), code);
                needPing = "BOOT_MISMATCH".equals(code);
            } else {
                log.info("← 设备 {} ({}) {}", c.event, shortId(id), status.json());
            }
        }
        if (needPing) {
            SerialTransport tr;
            synchronized (state) {
                tr = transport;
                bootId = null; // 重新握手
            }
            if (tr != null) {
                writeLine(tr, "{\"type\":\"ping\"}");
            }
        }
    }

    private static AckStatus parseAck(String s) {
        return switch (s) {
            case "accepted" -> AckStatus.ACCEPTED;
            case "done" -> AckStatus.DONE;
            case "cancelled" -> AckStatus.CANCELLED;
            case "error" -> AckStatus.ERROR;
            default -> null;
        };
    }

    // ================================================================ 工具

    private void closeTransport(SerialTransport expected) {
        SerialTransport tr;
        synchronized (state) {
            if (transport == null || (expected != null && transport != expected)) {
                return;
            }
            tr = transport;
            transport = null;
            bootId = null;
            epoch++;
        }
        try {
            tr.close();
        } catch (RuntimeException e) {
            log.debug("关闭串口异常：{}", e.toString());
        }
    }

    private boolean sleep(Duration d) {
        try {
            Thread.sleep(Math.max(d.toMillis(), 0));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static String truncate(String s) {
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static ThreadFactory daemon(String name) {
        return r -> {
            Thread th = new Thread(r, name);
            th.setDaemon(true);
            return th;
        };
    }

    /** 测试与调试用：最近命令（旧 → 新）。 */
    public List<Command> recentCommands() {
        synchronized (state) {
            return commands.values().stream().map(Command::copy).toList();
        }
    }

    public static final class Command {
        public final String id;
        public final DeviceEvent event;
        public final String bootId;
        volatile AckStatus status = AckStatus.SENT;

        Command(String id, DeviceEvent event, String bootId) {
            this.id = id;
            this.event = event;
            this.bootId = bootId;
        }

        public AckStatus status() {
            return status;
        }

        Command copy() {
            Command c = new Command(id, event, bootId);
            c.status = status;
            return c;
        }
    }
}
