package com.lifebranch.server.device;

import com.lifebranch.server.model.DeviceEvent;
import com.lifebranch.server.model.Health;

/**
 * 应用 → 设备的事件出口。SessionService 在持有会话锁时调用 send，
 * 以保证状态转换与设备事件顺序一致，因此实现必须非阻塞（只入队或记录，不等待 ACK）。
 * 设备离线时直接丢弃事件，不缓存以后补发。
 */
public interface DeviceBridge {

    void send(DeviceEvent event);

    Health.DeviceInfo info();
}
