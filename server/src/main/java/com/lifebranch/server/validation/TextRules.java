package com.lifebranch.server.validation;

/** 文本规则：去首尾空白后非空，并按 Unicode 码点计数（与前端 Array.from(text).length 一致）。 */
public final class TextRules {

    private TextRules() {
    }

    public static int length(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    public static String trim(String s) {
        return s == null ? null : s.strip();
    }

    /** 去空白后非空且码点数在 [1, max] 内。 */
    public static boolean fits(String s, int max) {
        if (s == null) {
            return false;
        }
        String t = s.strip();
        return !t.isEmpty() && length(t) <= max;
    }
}
