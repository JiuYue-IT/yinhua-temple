package com.lifebranch.server.config;

import com.lifebranch.server.model.DeviceMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 全部外部配置。值来自环境变量（见 application.yml），密钥不写入仓库。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Ai ai, Device device, @DefaultValue Preset preset) {

    /** 预置案例加载前的固定停顿，让「正在问签」有时间呈现；测试中设为 0。 */
    public record Preset(@DefaultValue("2000") long delayMs) {
    }

    public record Ai(
            String endpoint,
            String apiKey,
            String model,
            @DefaultValue("25") int requestTimeoutSeconds,
            @DefaultValue("30") int taskTimeoutSeconds) {

        /** 只表示三项配置都存在，不代表服务一定可用。 */
        public boolean configured() {
            return notBlank(endpoint) && notBlank(apiKey) && notBlank(model);
        }

        /** 防止密钥进入日志。 */
        @Override
        public String toString() {
            return "Ai[endpoint=" + endpoint + ", apiKey=" + (notBlank(apiKey) ? "***" : "<empty>")
                    + ", model=" + model + ", requestTimeoutSeconds=" + requestTimeoutSeconds
                    + ", taskTimeoutSeconds=" + taskTimeoutSeconds + "]";
        }
    }

    public record Device(
            @DefaultValue("dryrun") DeviceMode mode,
            String serialPort) {
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
