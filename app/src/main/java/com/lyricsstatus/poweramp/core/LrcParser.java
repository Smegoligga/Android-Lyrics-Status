package com.lyricsstatus.poweramp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC text (also "enhanced" LRC with per-word tags) into {@link Lyrics}.
 * Extends the parser from OvalQuilter's LrcLibSource.ts: supports 1-3 digit minutes,
 * 1-3 digit fractions, the [offset:] tag, and several timestamps per line.
 */
public final class LrcParser {

    // [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx]
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("^\\s*\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("^\\s*\\[[a-zA-Z#]+:[^\\]]*]\\s*$");
    // Enhanced LRC word timing <mm:ss.xx>
    private static final Pattern WORD_TIME = Pattern.compile("<\\d+:\\d{1,2}(?:[.:]\\d{1,3})?>");

    private LrcParser() {}

    /** Returns true if the text contains at least two LRC line timestamps. */
    public static boolean looksSynced(String text) {
        if (text == null) return false;
        Matcher m = TIME.matcher(text);
        int count = 0;
        while (m.find()) {
            if (++count >= 2) return true;
        }
        return false;
    }

    public static Lyrics parse(String text, String source) {
        if (text == null) text = "";
        if (text.startsWith("﻿")) text = text.substring(1);
        if (!looksSynced(text)) return parsePlain(text, source);

        long offset = 0;
        List<Lyrics.Line> out = new ArrayList<>();
        for (String rawLine : text.split("\r\n|\r|\n")) {
            Matcher om = OFFSET.matcher(rawLine);
            if (om.find()) {
                try {
                    offset = Long.parseLong(om.group(1));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if (META.matcher(rawLine).matches() && !TIME.matcher(rawLine).find()) continue;

            Matcher m = TIME.matcher(rawLine);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (m.find()) {
                times.add(toMillis(m));
                textStart = m.end();
            }
            if (times.isEmpty()) continue;

            String lineText = rawLine.substring(textStart);
            lineText = TIME.matcher(lineText).replaceAll("");
            lineText = WORD_TIME.matcher(lineText).replaceAll("");
            lineText = lineText.replaceAll("\\s+", " ").trim();

            for (long t : times) out.add(new Lyrics.Line(t, lineText));
        }

        // LRC spec: positive offset means lyrics appear sooner
        if (offset != 0) {
            List<Lyrics.Line> shifted = new ArrayList<>(out.size());
            for (Lyrics.Line l : out) shifted.add(new Lyrics.Line(Math.max(0, l.time - offset), l.text));
            out = shifted;
        }

        // Stable sort keeps original order for identical timestamps
        out.sort((a, b) -> Long.compare(a.time, b.time));
        return new Lyrics(out, true, source, text);
    }

    private static Lyrics parsePlain(String text, String source) {
        List<Lyrics.Line> out = new ArrayList<>();
        for (String l : text.split("\r\n|\r|\n")) {
            String t = l.trim();
            if (!t.isEmpty()) out.add(new Lyrics.Line(0, t));
        }
        return new Lyrics(out, false, source, text);
    }

    private static long toMillis(Matcher m) {
        long min = Long.parseLong(m.group(1));
        long sec = Long.parseLong(m.group(2));
        long frac = 0;
        String f = m.group(3);
        if (f != null) {
            if (f.length() == 1) frac = Long.parseLong(f) * 100;
            else if (f.length() == 2) frac = Long.parseLong(f) * 10;
            else frac = Long.parseLong(f);
        }
        return (min * 60 + sec) * 1000 + frac;
    }
}
