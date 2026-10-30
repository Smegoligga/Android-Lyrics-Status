package com.lyricsstatus.poweramp.core;

import java.text.Normalizer;
import java.util.Locale;

/** The currently playing track, merged from Poweramp's broadcasts and the Android media session. */
public final class Track {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    /** Audio file path when known (Poweramp sends it), else null. */
    public final String path;
    /** Poweramp's real track id (needed for its lyrics API), or 0. */
    public final long realId;

    public Track(String title, String artist, String album, long durationMs, String path, long realId) {
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = durationMs;
        this.path = path == null || path.isEmpty() ? null : path;
        this.realId = realId;
    }

    /** Identity used to detect song changes and to match data from different sources. */
    public String key() {
        return norm(title) + "\u0000" + norm(artist);
    }

    public boolean sameSong(Track o) {
        return o != null && key().equals(o.key());
    }

    /** Fill blanks in this track with values from another report of the same song. */
    public Track mergeWith(Track o) {
        if (o == null || !sameSong(o)) return this;
        return new Track(title, artist, album.isEmpty() ? o.album : album,
                durationMs > 0 ? durationMs : o.durationMs,
                path != null ? path : o.path,
                realId != 0 ? realId : o.realId);
    }

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
        return n.replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return artist + " - " + title;
    }
}
