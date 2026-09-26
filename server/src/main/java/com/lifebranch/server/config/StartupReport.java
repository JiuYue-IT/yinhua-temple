package com.lifebranch.server.config;

import com.lifebranch.server.device.DeviceBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** 启动完成后打印一段现场可读的摘要：访问地址、AI、设备、网页托管情况。 */
@Component
public class StartupReport {

    private static final Logger log = LoggerFactory.getLogger(StartupReport.class);

    private final AppProperties props;
    private final DeviceBridge device;
    private final Environment env;

    public StartupReport(AppProperties props, DeviceBridge device, Environment env) {
        this.props = props;
        this.device = device;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        AppProperties.Ai ai = props.ai();
        String aiLine = ai.configured()
                ? "已配置（" + ai.provider().name().toLowerCase() + " / " + ai.model() + "）"
                : "未配置，只能使用预置案例（在 server/.env 填 AI_ENDPOINT、AI_API_KEY）";
        String deviceLine = switch (props.device().mode()) {
            case DRYRUN -> "dryrun 模拟（不连接硬件）";
            case SERIAL -> "serial " + props.device().serialPort() + "（当前 " + device.info().status().json() + "）";
        };
        String dist = env.getProperty("app.web.dist", "../web/dist/");
        Path index = Path.of(dist).resolve("index.html").toAbsolutePath().normalize();
        String webLine = Files.isRegularFile(index)
                ? "已托管 " + index.getParent() + " → http://localhost:" + port + "/"
                : "未找到 web/dist/index.html（开发时用 Vite 代理 /api 到本端口）";
        log.info("""

                ==================== 人生支线后端已启动 ====================
                  接口：http://localhost:{}/api/health
                  AI  ：{}
                  设备：{}
                  网页：{}
                ============================================================""",
                port, aiLine, deviceLine, webLine);
    }
}
