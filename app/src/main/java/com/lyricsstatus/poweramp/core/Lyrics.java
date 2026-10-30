package com.lyricsstatus.poweramp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of lyrics lines for one track.
 * Port of LyricsLine / SongLyrics from OvalQuilter's LyricsStatus (src/Sources/BaseSource.ts).
 */
public final class Lyrics {

    public static final class Line {
        /** Start time in milliseconds. */
        public final long time;
        public final String text;

        public Line(long time, String text) {
            this.time = time;
            this.text = text;
        }

        @Override
        public String toString() {
            return "[" + time + "] " + text;
        }
    }

    public final List<Line> lines;
    /** True when lines carry real timestamps (LRC / SYLT). Plain lyrics can't drive a status. */
    public final boolean synced;
    /** Human-readable origin, e.g. "Local .lrc", "Embedded (FLAC)", "LRCLIB". */
    public final String source;
    /** Raw LRC/plain text, kept so it can be cached or handed to Poweramp. */
    public final String raw;

    public Lyrics(List<Line> lines, boolean synced, String source, String raw) {
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.synced = synced;
        this.source = source;
        this.raw = raw;
    }

    public Lyrics withSource(String newSource) {
        return new Lyrics(lines, synced, newSource, raw);
    }

    public boolean isEmpty() {
        for (Line l : lines) if (!l.text.isEmpty()) return false;
        return true;
    }

    /** Index of the last line whose start time is <= positionMs, or -1. */
    public int indexAt(long positionMs) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= positionMs) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
