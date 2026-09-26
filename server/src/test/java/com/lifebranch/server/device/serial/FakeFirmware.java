package com.lifebranch.server.device.serial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 按文档 03 §5 模拟 ESP32 固件的内存串口：hello/ping、accepted→done、同 id 去重、DRAW BUSY、bootId 校验。
 * 输出故意拆成 1—3 字节的碎片并与启动噪声混合，用来验证分包/粘包解析。
 */
class FakeFirmware implements SerialTransport, SerialTransportFactory {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LinkedBlockingQueue<byte[]> out = new LinkedBlockingQueue<>();
    private final StringBuilder in = new StringBuilder();
    final List<JsonNode> received = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> seen = new LinkedHashMap<>(); // id → event

    volatile String bootId = "boot0001";
    volatile boolean open;
    volatile boolean mute;          // 不回任何 ack（模拟无回执）
    volatile boolean silent;        // 完全不输出（模拟卡死但端口仍打开）
    volatile boolean helloOnOpen = true;
    volatile boolean failOpen;
    private volatile boolean drawing;
    private int boots = 1;
    int opens;

    // ---------------- SerialTransportFactory

    @Override
    public synchronized SerialTransport open(String portName) throws java.io.IOException {
        if (failOpen) {
            throw new java.io.IOException("端口不存在（模拟）");
        }
        opens++;
        open = true;
        out.clear();
        emitRaw("ets Jun  8 2016 00:22:57\r\nrst:0x1 (POWERON_RESET)\r\n"); // ESP32 ROM 启动噪声
        if (helloOnOpen) {
            hello();
        }
        return this;
    }

    // ---------------- SerialTransport

    @Override
    public int read(byte[] buffer) {
        if (!open) {
            return -1;
        }
        try {
            byte[] chunk = out.poll(50, TimeUnit.MILLISECONDS);
            if (!open) {
                return -1;
            }
            if (chunk == null) {
                return 0;
            }
            System.arraycopy(chunk, 0, buffer, 0, chunk.length);
            return chunk.length;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public synchronized int write(byte[] data) {
        if (!open) {
            return -1;
        }
        in.append(new String(data, StandardCharsets.UTF_8));
        int nl;
        while ((nl = in.indexOf("\n")) >= 0) {
            String line = in.substring(0, nl).trim();
            in.delete(0, nl + 1);
            handle(line);
        }
        return data.length;
    }

    @Override
    public void close() {
        open = false;
    }

    // ---------------- 测试控制

    /** 模拟拔掉 USB。 */
    void unplug() {
        open = false;
    }

    /** 模拟板卡重启：新的 bootId，清空去重表，主动输出 hello。 */
    synchronized void reboot() {
        bootId = "boot000" + (++boots);
        seen.clear();
        drawing = false;
        hello();
    }

    List<String> commandEvents() {
        synchronized (received) {
            return received.stream().filter(n -> "cmd".equals(n.path("type").asText()))
                    .map(n -> n.path("event").asText()).toList();
        }
    }

    long pings() {
        synchronized (received) {
            return received.stream().filter(n -> "ping".equals(n.path("type").asText())).count();
        }
    }

    // ---------------- 协议

    private void handle(String line) {
        JsonNode n;
        try {
            n = mapper.readTree(line);
        } catch (Exception e) {
            emit("{\"type\":\"error\",\"code\":\"BAD_COMMAND\"}");
            return;
        }
        received.add(n);
        switch (n.path("type").asText()) {
            case "ping" -> hello();
            case "cmd" -> command(n);
            default -> emit("{\"type\":\"error\",\"code\":\"BAD_COMMAND\"}");
        }
    }

    private void command(JsonNode n) {
        String id = n.path("id").asText();
        String event = n.path("event").asText();
        if (mute) {
            return;
        }
        if (!bootId.equals(n.path("bootId").asText())) {
            ack(id, "error", "BOOT_MISMATCH");
            return;
        }
        String prev = seen.get(id);
        if (prev != null) {
            ack(id, prev.equals(event) ? "done" : "error", prev.equals(event) ? null : "ID_CONFLICT");
            return;
        }
        if ("DRAW".equals(event) && drawing) {
            ack(id, "error", "BUSY");
            return;
        }
        seen.put(id, event);
        ack(id, "accepted", null);
        if ("DRAW".equals(event)) {
            drawing = true;
            new Thread(() -> {
                sleep(150); // 模拟短时摇签
                synchronized (this) {
                    if (drawing) {
                        drawing = false;
                        ack(id, "done", null);
                    }
                }
            }).start();
        } else {
            if ("RESET".equals(event) || "ERROR".equals(event)) {
                drawing = false;
            }
            ack(id, "done", null);
        }
    }

    private void hello() {
        emit("{\"type\":\"log\",\"message\":\"starting\"}");
        emit("{\"type\":\"hello\",\"v\":1,\"bootId\":\"" + bootId + "\",\"device\":\"temple-01\"}");
    }

    private void ack(String id, String status, String code) {
        emit("{\"type\":\"ack\",\"bootId\":\"" + bootId + "\",\"id\":\"" + id + "\",\"status\":\"" + status + "\""
                + (code == null ? "" : ",\"code\":\"" + code + "\"") + "}");
    }

    private void emit(String json) {
        emitRaw(json + "\r\n");
    }

    /** 拆成 1—3 字节的碎片写入输出队列。 */
    private void emitRaw(String s) {
        if (silent) {
            return;
        }
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int i = 0;
        int step = 1;
        while (i < b.length) {
            int len = Math.min(step, b.length - i);
            byte[] chunk = new byte[len];
            System.arraycopy(b, i, chunk, 0, len);
            out.add(chunk);
            i += len;
            step = step % 3 + 1;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
