package com.lifebranch.server.device;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.device.serial.JSerialCommTransport;
import com.lifebranch.server.device.serial.SerialDeviceBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.stream.Collectors;

/** 按 DEVICE_MODE 选择设备桥接实现；应用就绪后才开始连接串口。 */
@Configuration
public class DeviceConfig {

    private static final Logger log = LoggerFactory.getLogger(DeviceConfig.class);

    @Bean(destroyMethod = "close")
    public DeviceBridge deviceBridge(AppProperties props) {
        return switch (props.device().mode()) {
            case DRYRUN -> {
                log.info("设备模式 dryrun：只记录模拟事件，不连接硬件");
                yield new DryRunDeviceBridge();
            }
            case SERIAL -> {
                String ports = describePorts();
                log.info("本机串口：{}", ports);
                String port = props.device().serialPort();
                if (port == null || port.isBlank()) {
                    throw new IllegalStateException("DEVICE_MODE=serial 需要设置 SERIAL_PORT。本机串口：" + ports);
                }
                yield new SerialDeviceBridge(port.trim(), JSerialCommTransport::open,
                        SerialDeviceBridge.Timings.defaults());
            }
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startDevice(ApplicationReadyEvent e) {
        e.getApplicationContext().getBean(DeviceBridge.class).start();
    }

    private static String describePorts() {
        var list = JSerialCommTransport.listPorts();
        if (list.isEmpty()) {
            return "（无）";
        }
        return list.stream().map(p -> p.name() + " " + p.description()).collect(Collectors.joining("；"));
    }
}
