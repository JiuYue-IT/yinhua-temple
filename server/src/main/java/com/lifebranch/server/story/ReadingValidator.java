package com.lifebranch.server.story;

import com.lifebranch.server.model.Reading;
import com.lifebranch.server.validation.TextRules;
import java.util.ArrayList;
import java.util.List;

public final class ReadingValidator {
    private ReadingValidator() {}
    public static List<String> validate(Reading r) {
        List<String> errors = new ArrayList<>();
        if (r == null || r.summary() == null || r.detail() == null) return List.of("summary / detail 缺失");
        check(errors, "summary.title", r.summary().title(), 20);
        check(errors, "summary.verse", r.summary().verse(), 60);
        check(errors, "summary.message", r.summary().message(), 100);
        check(errors, "detail.understanding", r.detail().understanding(), 300);
        check(errors, "detail.possibility", r.detail().possibility(), 300);
        check(errors, "detail.suggestion", r.detail().suggestion(), 300);
        check(errors, "detail.nextStep", r.detail().nextStep(), 150);
        check(errors, "detail.basis", r.detail().basis(), 200);
        return errors;
    }
    private static void check(List<String> errors, String field, String value, int max) {
        if (!TextRules.fits(value, max)) errors.add(field + " 为空或超长");
    }
}
