package com.lifebranch.server.story;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifebranch.server.model.ReceiptDraft;
import com.lifebranch.server.model.Reflection;
import com.lifebranch.server.model.Story;
import com.lifebranch.server.model.StoryOption;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryValidatorTest {

    private final StoryValidator validator = new StoryValidator();

    @Test
    void presetCaseIsValid() throws Exception {
        PresetCatalog catalog = new PresetCatalog(new ObjectMapper(), validator);
        PresetCase c = catalog.find("team-project").orElseThrow();
        assertThat(validator.validate(c.story())).isEmpty();
        assertThat(c.story().option("A").reflection()).isNotNull();
        assertThat(c.story().option("B").reflection()).isNull();
    }

    @Test
    void storyWithoutAnyReflectionIsAllowed() {
        Story s = story(option("A", null), option("B", null));
        assertThat(validator.validate(s)).isEmpty();
    }

    @Test
    void rejectsWrongOptionCountAndDuplicateIds() {
        assertThat(validator.validate(story(option("A", null)))).contains("options 必须恰好 2 项");
        assertThat(validator.validate(story(option("A", null), option("A", null))))
                .anyMatch(e -> e.contains("id 重复"));
        assertThat(validator.validate(story(option("A", null), option("C", null))))
                .anyMatch(e -> e.contains("必须为 A 或 B"));
    }

    @Test
    void rejectsIncompleteReflectionAndMissingDraft() {
        StoryOption badReflection = new StoryOption("A", "标签", "后续",
                new Reflection("目标", " ", "替代"), new ReceiptDraft("发现", "下一步"));
        StoryOption noDraft = new StoryOption("B", "标签", "后续", null, null);
        List<String> errors = validator.validate(story(badReflection, noDraft));
        assertThat(errors).anyMatch(e -> e.contains("reflection.concern"));
        assertThat(errors).anyMatch(e -> e.contains("receiptDraft 缺失"));
    }

    @Test
    void lengthIsCountedInCodePoints() {
        // 12 个表情 = 12 码点（24 个 UTF-16 单元），应当通过 title ≤ 12
        String twelveEmoji = "😀".repeat(12);
        Story ok = new Story(twelveEmoji, "问", List.of("假设"), "开场", "决定",
                List.of(option("A", null), option("B", null)));
        assertThat(validator.validate(ok)).isEmpty();

        Story tooLong = new Story("😀".repeat(13), "问", List.of("假设"), "开场", "决定",
                List.of(option("A", null), option("B", null)));
        assertThat(validator.validate(tooLong)).anyMatch(e -> e.startsWith("title 超过"));
    }

    @Test
    void assumptionsMustBeOneToThree() {
        Story none = new Story("题", "问", List.of(), "开场", "决定",
                List.of(option("A", null), option("B", null)));
        Story four = new Story("题", "问", List.of("1", "2", "3", "4"), "开场", "决定",
                List.of(option("A", null), option("B", null)));
        assertThat(validator.validate(none)).anyMatch(e -> e.startsWith("assumptions"));
        assertThat(validator.validate(four)).anyMatch(e -> e.startsWith("assumptions"));
    }

    private static Story story(StoryOption... options) {
        return new Story("题", "问题", List.of("假设"), "开场", "决定", List.of(options));
    }

    private static StoryOption option(String id, Reflection r) {
        return new StoryOption(id, "标签" + id, "后续" + id, r, new ReceiptDraft("发现", "下一步"));
    }
}
