package com.lyricsstatus.poweramp.core;

import java.util.Locale;

/**
 * Builds the Discord status text.
 * Port of StatusChanger.getStatusString / parseStatusString from OvalQuilter's LyricsStatus v3,
 * with the same placeholders (plus {album}, {duration}). Every occurrence is replaced, not just the first.
 */
public final class Formatter {

    /** Discord's custom status limit. */
    public static final int MAX_LEN = 128;

    private static final String LETTERS_ONLY = "['\",.]";
    private static final String CROP = "( ?- ?.+)|(\\(.+\\))";

    private Formatter() {}

    /** Same as JS formatSeconds: 83 -> "1:23". */
    public static String formatSeconds(long s) {
        long m = s / 60, r = s % 60;
        return m + (r > 9 ? ":" : ":0") + r;
    }

    public static String timestamp(long ms) {
        return formatSeconds(Math.round(ms / 1000.0));
    }

    public static String lrcStamp(long ms) {
        long cs = Math.round(ms / 10.0);
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", cs / 6000, (cs / 100) % 60, cs % 100);
    }

    /** The "simple" view: optional [m:ss] timestamp, optional "Song lyrics - " label. */
    public static String simple(Settings s, Lyrics.Line line) {
        String out = (s.showTimestamp ? "[" + timestamp(line.time) + "] " : "")
                + (s.showLabel ? "Song lyrics - " : "")
                + line.text.replace("♪", "🎶");
        return clip(out);
    }

    /** The "advanced" view: user template with placeholders. */
    public static String template(String template, Lyrics.Line line, Track track) {
        String lyr = line != null ? line.text : "";
        String name = track != null ? nz(track.title) : "";
        String author = track != null ? nz(track.artist) : "";
        String album = track != null ? nz(track.album) : "";
        String ts = line != null ? timestamp(line.time) : "";
        String dur = track != null && track.durationMs > 0 ? timestamp(track.durationMs) : "";
        String cropped = name.replaceAll(CROP, "").trim();

        String s = template;
        // Longest placeholders first so {lyrics} doesn't eat {lyrics_upper}
        s = s.replace("{lyrics_upper_letters_only}", lyr.toUpperCase(Locale.ROOT).replaceAll(LETTERS_ONLY, ""));
        s = s.replace("{lyrics_lower_letters_only}", lyr.toLowerCase(Locale.ROOT).replaceAll(LETTERS_ONLY, ""));
        s = s.replace("{lyrics_letters_only}", lyr.replaceAll(LETTERS_ONLY, ""));
        s = s.replace("{lyrics_upper}", lyr.toUpperCase(Locale.ROOT));
        s = s.replace("{lyrics_lower}", lyr.toLowerCase(Locale.ROOT));
        s = s.replace("{lyrics}", lyr);
        s = s.replace("{timestamp}", ts);
        s = s.replace("{duration}", dur);
        s = s.replace("{song_name_upper_cropped}", cropped.toUpperCase(Locale.ROOT));
        s = s.replace("{song_name_lower_cropped}", cropped.toLowerCase(Locale.ROOT));
        s = s.replace("{song_name_cropped}", cropped);
        s = s.replace("{song_name_upper}", name.toUpperCase(Locale.ROOT));
        s = s.replace("{song_name_lower}", name.toLowerCase(Locale.ROOT));
        s = s.replace("{song_name}", name);
        s = s.replace("{song_author_upper}", author.toUpperCase(Locale.ROOT));
        s = s.replace("{song_author_lower}", author.toLowerCase(Locale.ROOT));
        s = s.replace("{song_author}", author);
        s = s.replace("{album}", album);
        s = s.replace("♪", "🎶");
        return clip(s.trim());
    }

    public static String forLine(Settings s, Lyrics.Line line, Track track) {
        return s.advanced ? template(s.customStatus, line, track) : simple(s, line);
    }

    /** Clip to 128 UTF-16 units without splitting a surrogate pair. */
    public static String clip(String s) {
        if (s.length() <= MAX_LEN) return s;
        int end = MAX_LEN;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
