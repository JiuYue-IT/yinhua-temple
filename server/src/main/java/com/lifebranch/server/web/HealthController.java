package com.lifebranch.server.web;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.device.DeviceBridge;
import com.lifebranch.server.model.Health;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/health：服务、AI 配置与设备状态。设备状态来自 DeviceBridge。 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final AppProperties props;
    private final DeviceBridge device;

    public HealthController(AppProperties props, DeviceBridge device) {
        this.props = props;
        this.device = device;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("ok", props.ai().configured(), device.info());
    }
}
