package com.lifebranch.server.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.model.Input;
import com.lifebranch.server.validation.TextRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 启动时加载 classpath:presets/*.json。任何案例不通过校验都会让服务启动失败，
 * 避免演示现场才发现预置数据有问题。
 */
@Component
public class PresetCatalog {

    private static final Logger log = LoggerFactory.getLogger(PresetCatalog.class);

    private final Map<String, PresetCase> cases;

    public PresetCatalog(ObjectMapper mapper, StoryValidator validator) throws IOException {
        Map<String, PresetCase> loaded = new LinkedHashMap<>();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:presets/*.json");
        for (Resource r : resources) {
            PresetCase c;
            try (InputStream in = r.getInputStream()) {
                c = mapper.readValue(in, PresetCase.class);
            }
            List<String> errors = validator.validate(c.story());
            checkInput(c.input(), errors);
            if (c.caseId() == null || c.caseId().isBlank()) {
                errors.add("caseId 为空");
            }
            if (!errors.isEmpty()) {
                throw new IllegalStateException("预置案例 " + r.getFilename() + " 校验失败：" + errors);
            }
            if (loaded.put(c.caseId(), c) != null) {
                throw new IllegalStateException("预置案例 caseId 重复：" + c.caseId());
            }
        }
        this.cases = Map.copyOf(loaded);
        log.info("已加载预置案例：{}", cases.keySet());
    }

    public Optional<PresetCase> find(String caseId) {
        return Optional.ofNullable(caseId == null ? null : cases.get(caseId));
    }

    public Set<String> ids() {
        return cases.keySet();
    }

    private static void checkInput(Input in, List<String> errors) {
        if (in == null) {
            errors.add("input 为空");
            return;
        }
        if (!TextRules.fits(in.background(), Input.BACKGROUND_MAX)) errors.add("input.background 不合法");
        if (!TextRules.fits(in.chosenPath(), Input.PATH_MAX)) errors.add("input.chosenPath 不合法");
        if (!TextRules.fits(in.unchosenPath(), Input.PATH_MAX)) errors.add("input.unchosenPath 不合法");
        if (!TextRules.fits(in.priority(), Input.PRIORITY_MAX)) errors.add("input.priority 不合法");
    }
}
