package com.lifebranch.server.device;

import com.lifebranch.server.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 按 DEVICE_MODE 选择设备桥接实现。 */
@Configuration
public class DeviceConfig {

    @Bean(destroyMethod = "")
    public DeviceBridge deviceBridge(AppProperties props) {
        return switch (props.device().mode()) {
            case DRYRUN -> new DryRunDeviceBridge();
            case SERIAL -> throw new IllegalStateException(
                    "DEVICE_MODE=serial 需要串口桥接（模块 M3，尚未实现），当前请使用 DEVICE_MODE=dryrun");
        };
    }
}
