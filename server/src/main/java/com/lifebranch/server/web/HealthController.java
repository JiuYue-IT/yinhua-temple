package com.lifebranch.server.web;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.model.DeviceMode;
import com.lifebranch.server.model.DeviceStatus;
import com.lifebranch.server.model.Health;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health。
 * M1：设备状态仅由配置推出（dryrun → dryrun；serial → offline，串口桥接在 M3 实现）。
 * M2/M3 起改为从 DeviceBridge 读取真实状态。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final AppProperties props;

    public HealthController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Health health() {
        DeviceMode mode = props.device().mode();
        DeviceStatus status = mode == DeviceMode.DRYRUN ? DeviceStatus.DRYRUN : DeviceStatus.OFFLINE;
        return new Health("ok", props.ai().configured(),
                new Health.DeviceInfo(mode, status, null, null));
    }
}
