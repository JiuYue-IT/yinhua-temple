package com.lifebranch.server.device.serial;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 把串口字节流切成行（文档 03 §5.1）：以 \n 结束，容忍 \r\n，单行最多 maxLine 字节。
 * 超长时丢弃到下一个换行并计数，不会无限占用内存。一次读取可能含半行或多行。
 * 非线程安全，只由读线程使用。
 */
public final class LineFramer {

    private final byte[] buf;
    private int len;
    private boolean discarding;
    private int overflows;

    public LineFramer(int maxLine) {
        this.buf = new byte[maxLine];
    }

    public List<String> feed(byte[] data, int count) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byte b = data[i];
            if (b == '\n') {
                if (!discarding) {
                    int end = len;
                    if (end > 0 && buf[end - 1] == '\r') {
                        end--;
                    }
                    if (end > 0) {
                        lines.add(new String(buf, 0, end, StandardCharsets.UTF_8));
                    }
                }
                len = 0;
                discarding = false;
            } else if (!discarding) {
                if (len == buf.length) {
                    discarding = true;
                    overflows++;
                    len = 0;
                } else {
                    buf[len++] = b;
                }
            }
        }
        return lines;
    }

    /** 返回并清零自上次调用以来的超长行数量。 */
    public int takeOverflows() {
        int n = overflows;
        overflows = 0;
        return n;
    }
}
