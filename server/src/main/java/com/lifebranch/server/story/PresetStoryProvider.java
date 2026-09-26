package com.lifebranch.server.story;

import com.lifebranch.server.config.AppProperties;
import com.lifebranch.server.model.Story;
import org.springframework.stereotype.Component;

/**
 * 预置案例来源。加一个短暂的固定停顿（app.preset.delay-ms），让签筒短时摇动与「正在问签」画面有时间呈现；
 * 页面始终标注为预置案例，不伪装成实时生成。
 */
@Component
public class PresetStoryProvider {

    private final long delayMs;

    public PresetStoryProvider(AppProperties props) {
        this.delayMs = Math.max(0, props.preset().delayMs());
    }

    public Story load(PresetCase presetCase) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        return presetCase.story();
    }
}
