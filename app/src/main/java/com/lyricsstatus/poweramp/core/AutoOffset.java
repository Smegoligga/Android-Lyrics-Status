package com.lyricsstatus.poweramp.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rolling average of how long Discord takes to apply a status change, so lines are sent early enough
 * to appear on time. Port of Autooffset.ts from OvalQuilter's LyricsStatus (same "+100 ms" rule in StatusChanger).
 */
public final class AutoOffset {
    private final Deque<Long> values = new ArrayDeque<>();
    private int limit = 3;

    public synchronized void setLimit(int limit) {
        limit = Math.max(1, limit);
        this.limit = limit;
        while (values.size() > limit) values.removeLast();
    }

    public synchronized void add(long ms) {
        values.addFirst(ms);
        while (values.size() > limit) values.removeLast();
    }

    /** Average of the last samples, padding missing ones with 0 like the original. */
    public synchronized long average() {
        long sum = 0;
        for (long v : values) sum += v;
        return sum / limit;
    }

    /** The offset to use: average round-trip + 100 ms, or the manual value. */
    public long offset(Settings s) {
        setLimit(s.autoOffsetSamples);
        return s.autoOffset ? average() + 100 : s.sendTimeOffset;
    }
}
