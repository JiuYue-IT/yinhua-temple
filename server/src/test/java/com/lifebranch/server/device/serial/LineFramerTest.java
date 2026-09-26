package com.lifebranch.server.device.serial;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineFramerTest {

    @Test
    void splitsPartialAndMultipleLinesAndToleratesCrlf() {
        LineFramer f = new LineFramer(512);
        List<String> out = new ArrayList<>();
        out.addAll(feed(f, "{\"a\":1}\r\n{\"b\""));
        assertThat(out).containsExactly("{\"a\":1}");
        out.addAll(feed(f, ":2}\n\n{\"c\":3}\n"));
        assertThat(out).containsExactly("{\"a\":1}", "{\"b\":2}", "{\"c\":3}");
    }

    @Test
    void multibyteUtf8SplitAcrossReadsIsKept() {
        LineFramer f = new LineFramer(512);
        byte[] b = "{\"message\":\"静候一念\"}\n".getBytes(StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (byte x : b) {
            out.addAll(f.feed(new byte[]{x}, 1));
        }
        assertThat(out).containsExactly("{\"message\":\"静候一念\"}");
    }

    @Test
    void overlongLineIsDiscardedUntilNextNewline() {
        LineFramer f = new LineFramer(16);
        List<String> out = feed(f, "x".repeat(40) + "\n{\"ok\":1}\n");
        assertThat(out).containsExactly("{\"ok\":1}");
        assertThat(f.takeOverflows()).isEqualTo(1);
        assertThat(f.takeOverflows()).isZero();
    }

    private static List<String> feed(LineFramer f, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return f.feed(b, b.length);
    }
}
