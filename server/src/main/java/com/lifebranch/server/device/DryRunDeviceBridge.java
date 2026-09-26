package com.lifebranch.server.device;

import com.lifebranch.server.model.AckStatus;
import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.DeviceMode;
import com.lifebranch.server.model.DeviceStatus;
import com.lifebranch.server.model.Health;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟设备：只记录事件，health 始终显示 dryrun，lastAck 只到 sent（没有真实回执，不冒充 done）。
 */
public class DryRunDeviceBridge implements DeviceBridge {

    private static final Logger log = LoggerFactory.getLogger(DryRunDeviceBridge.class);
    private static final int HISTORY_MAX = 100;

    private final List<DeviceEvent> history = new ArrayList<>();
    private DeviceEvent lastEvent;
    private AckStatus lastAck;

    @Override
    public synchronized void send(DeviceEvent event) {
        log.info("[dryrun] 模拟设备事件 {}", event);
        history.add(event);
        if (history.size() > HISTORY_MAX) {
            history.remove(0);
        }
        lastEvent = event;
        lastAck = AckStatus.SENT;
    }

    @Override
    public synchronized Health.DeviceInfo info() {
        return new Health.DeviceInfo(DeviceMode.DRYRUN, DeviceStatus.DRYRUN, lastEvent, lastAck);
    }

    /** 最近事件（调试与测试用）。 */
    public synchronized List<DeviceEvent> history() {
        return List.copyOf(history);
    }

    public synchronized void clearHistory() {
        history.clear();
    }
}
