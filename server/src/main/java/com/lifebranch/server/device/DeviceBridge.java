package com.lifebranch.server.device;

import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.Health;

/**
 * 应用 → 设备的事件出口。SessionService 在持有会话锁时调用 send，
 * 以保证状态转换与设备事件顺序一致，因此实现必须非阻塞（只入队或记录，不等待 ACK）。
 * 设备离线时直接丢弃事件，不缓存以后补发。
 */
public interface DeviceBridge extends AutoCloseable {

    void send(DeviceEvent event);

    Health.DeviceInfo info();

    /**
     * 每次与设备（重新）握手成功后回调，用于恢复当前画面。回调在桥接内部线程中执行，
     * 调用前桥接已自行发送 RESET；回调中可以调用 send，但绝不能重放 DRAW。
     */
    default void onHandshake(Runnable listener) {
    }

    default void start() {
    }

    @Override
    default void close() {
    }
}
