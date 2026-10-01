package com.lifebranch.server.model;

/** 一次生成，两处展示：正殿只展示 summary，菩提果实展开 detail。 */
public record Reading(Summary summary, Detail detail) {
    public record Summary(String title, String verse, String message) {}
    public record Detail(String understanding, String possibility, String suggestion,
                         String nextStep, String basis) {}
}
