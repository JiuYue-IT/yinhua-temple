package com.lifebranch.server.model;

/**
 * 主殿抛出的签。它同时保留两种回应：对当前选择的前路预演，以及对后悔念头的补救建议。
 * 文案是可能性与劝勉，不是对未来的断言。
 */
public record Sign(
        String title,
        String verse,
        String preview,
        String remedy,
        String counsel,
        String nextStep,
        String basis) {
}
