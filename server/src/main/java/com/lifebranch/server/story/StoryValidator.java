package com.lifebranch.server.story;

import com.lifebranch.server.model.ReceiptDraft;
import com.lifebranch.server.model.Reflection;
import com.lifebranch.server.model.Sign;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.model.StoryOption;
import com.lifebranch.server.validation.TextRules;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Story 的结构与长度校验（文档 04 §3.2）。只检查格式，不裁定人生选择对错，
 * 也不强制存在回望。长度按码点计数、去首尾空白后判断。
 */
@Component
public class StoryValidator {

    public static final int TITLE_MAX = 12;
    public static final int QUESTION_MAX = 80;
    public static final int OPENING_MAX = 220;
    public static final int DECISION_MAX = 80;
    public static final int ASSUMPTION_MAX = 80;
    public static final int ASSUMPTIONS_MAX_COUNT = 3;
    public static final int LABEL_MAX = 50;
    public static final int OUTCOME_MAX = 220;
    public static final int REFLECTION_FIELD_MAX = 120;
    public static final int DRAFT_FIELD_MAX = 120;
    public static final int SIGN_TITLE_MAX = 20;
    public static final int SIGN_VERSE_MAX = 120;
    public static final int SIGN_FIELD_MAX = 220;

    /** 返回全部问题；空列表表示通过。 */
    public List<String> validate(Story story) {
        List<String> errors = new ArrayList<>();
        if (story == null) {
            errors.add("story 为空");
            return errors;
        }
        text(errors, "title", story.title(), TITLE_MAX);
        text(errors, "question", story.question(), QUESTION_MAX);
        text(errors, "opening", story.opening(), OPENING_MAX);
        text(errors, "decision", story.decision(), DECISION_MAX);

        List<String> assumptions = story.assumptions();
        if (assumptions == null || assumptions.isEmpty() || assumptions.size() > ASSUMPTIONS_MAX_COUNT) {
            errors.add("assumptions 必须为 1—" + ASSUMPTIONS_MAX_COUNT + " 项");
        } else {
            for (int i = 0; i < assumptions.size(); i++) {
                text(errors, "assumptions[" + i + "]", assumptions.get(i), ASSUMPTION_MAX);
            }
        }

        List<StoryOption> options = story.options();
        if (options == null || options.size() != 2) {
            errors.add("options 必须恰好 2 项");
            return errors;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            StoryOption o = options.get(i);
            String p = "options[" + i + "]";
            if (o == null) {
                errors.add(p + " 为空");
                continue;
            }
            if (!"A".equals(o.id()) && !"B".equals(o.id())) {
                errors.add(p + ".id 必须为 A 或 B");
            } else if (!ids.add(o.id())) {
                errors.add(p + ".id 重复：" + o.id());
            }
            text(errors, p + ".label", o.label(), LABEL_MAX);
            text(errors, p + ".outcome", o.outcome(), OUTCOME_MAX);
            reflection(errors, p + ".reflection", o.reflection());
            draft(errors, p + ".receiptDraft", o.receiptDraft());
            sign(errors, p + ".sign", o.sign());
        }
        return errors;
    }

    public boolean isValid(Story story) {
        return validate(story).isEmpty();
    }

    private static void reflection(List<String> errors, String p, Reflection r) {
        if (r == null) {
            return; // null 表示不回望，合法
        }
        text(errors, p + ".goal", r.goal(), REFLECTION_FIELD_MAX);
        text(errors, p + ".concern", r.concern(), REFLECTION_FIELD_MAX);
        text(errors, p + ".alternative", r.alternative(), REFLECTION_FIELD_MAX);
    }

    private static void draft(List<String> errors, String p, ReceiptDraft d) {
        if (d == null) {
            errors.add(p + " 缺失");
            return;
        }
        text(errors, p + ".insight", d.insight(), DRAFT_FIELD_MAX);
        text(errors, p + ".nextStep", d.nextStep(), DRAFT_FIELD_MAX);
    }

    /** null 表示故事未带签文（旧案例），抛签时由 SessionService 生成兜底签；带了就按长度校验。 */
    private static void sign(List<String> errors, String p, Sign s) {
        if (s == null) {
            return;
        }
        text(errors, p + ".title", s.title(), SIGN_TITLE_MAX);
        text(errors, p + ".verse", s.verse(), SIGN_VERSE_MAX);
        text(errors, p + ".preview", s.preview(), SIGN_FIELD_MAX);
        text(errors, p + ".remedy", s.remedy(), SIGN_FIELD_MAX);
        text(errors, p + ".counsel", s.counsel(), SIGN_FIELD_MAX);
        text(errors, p + ".nextStep", s.nextStep(), SIGN_FIELD_MAX);
        text(errors, p + ".basis", s.basis(), SIGN_FIELD_MAX);
    }

    private static void text(List<String> errors, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            errors.add(field + " 为空");
        } else if (TextRules.length(value.strip()) > max) {
            errors.add(field + " 超过 " + max + " 字（实际 " + TextRules.length(value.strip()) + "）");
        }
    }
}
