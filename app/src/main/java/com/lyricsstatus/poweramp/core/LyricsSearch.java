package com.lyricsstatus.poweramp.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Manual lyrics search on LRCLIB (song / artist / album), and matching a song inside a chosen album. */
public final class LyricsSearch {

    private static final String BASE = "https://lrclib.net/api";
    private static final Map<String, String> HEADERS = Collections.singletonMap(
            "User-Agent", "AndroidLyricsStatus (https://github.com/Smegoligga/Android-Lyrics-Status)");

    /** One LRCLIB record. */
    public static final class Record {
        public final long id;
        public final String track, artist, album;
        public final double duration; // seconds
        public final boolean instrumental;
        public final String synced, plain;

        Record(JSONObject o) {
            id = o.optLong("id");
            track = o.optString("trackName", o.optString("name", ""));
            artist = o.optString("artistName", "");
            album = o.optString("albumName", "");
            duration = o.optDouble("duration", 0);
            instrumental = o.optBoolean("instrumental", false);
            synced = o.isNull("syncedLyrics") ? "" : o.optString("syncedLyrics", "");
            plain = o.isNull("plainLyrics") ? "" : o.optString("plainLyrics", "");
        }

        public boolean isSynced() {
            return !synced.trim().isEmpty();
        }

        /** LRC text to store: synced when available, else plain. */
        public String text() {
            return isSynced() ? synced : plain;
        }

        public Lyrics lyrics(String source) {
            return LrcParser.parse(text(), source);
        }
    }

    // "(feat. X)", "[prod. by Y]", "(Remastered 2011)", "(Radio Edit)", "- 2011 Remaster", " feat. X" …
    private static final String EXTRA_WORDS =
            "feat\\.?|ft\\.?|featuring|with|prod\\.?|produced\\s+by|remaster(?:ed)?|\\d{4}\\s+remaster(?:ed)?"
                    + "|explicit|clean|radio\\s+edit|single\\s+version|album\\s+version|mono|stereo|bonus\\s+track|deluxe";
    private static final Pattern BRACKETED = Pattern.compile(
            "\\s*[(\\[{][^)\\]}]*?\\b(?:" + EXTRA_WORDS + ")\\b[^)\\]}]*[)\\]}]", Pattern.CASE_INSENSITIVE);
    private static final Pattern DASH_SUFFIX = Pattern.compile(
            "\\s+[-–—]\\s+(?:[^-–—]*\\b(?:" + EXTRA_WORDS + ")\\b.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_FEAT = Pattern.compile(
            "\\s+(?:feat\\.?|ft\\.?|featuring|prod\\.?|produced\\s+by)\\s+.*$", Pattern.CASE_INSENSITIVE);

    private LyricsSearch() {}

    /** Song title without featured artists, producers, remaster/edit tags. */
    public static String cleanTitle(String title) {
        if (title == null) return "";
        String t = title;
        String prev;
        do {
            prev = t;
            t = BRACKETED.matcher(t).replaceAll("");
        } while (!t.equals(prev));
        t = DASH_SUFFIX.matcher(t).replaceAll("");
        t = BARE_FEAT.matcher(t).replaceAll("");
        t = t.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? title.trim() : t;
    }

    /** Main artist only: "A feat. B", "A & B", "A, B", "A x B" all become "A". */
    public static String cleanArtist(String artist) {
        if (artist == null) return "";
        String a = artist.split("(?i)\\s*(,|;|/|&| feat\\.? | ft\\.? | featuring | x | with | and )\\s*")[0].trim();
        return a.isEmpty() ? artist.trim() : a;
    }

    /** Loose comparison key: lowercase letters and digits only. */
    public static String key(String s) {
        return Track.norm(s).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    /**
     * Search LRCLIB. With a song name: structured search (album as a filter, dropped if it finds nothing).
     * Without a song name: album/artist browse via the free-text query, filtered to the album when given.
     */
    public static List<Record> search(String song, String artist, String album, boolean ignoreExtras) throws Exception {
        String s = ignoreExtras ? cleanTitle(song) : nz(song).trim();
        String a = ignoreExtras ? cleanArtist(artist) : nz(artist).trim();
        String al = ignoreExtras ? cleanTitle(album) : nz(album).trim();
        List<Record> out;
        if (!s.isEmpty()) {
            out = query("track_name=" + Http.enc(s) + (a.isEmpty() ? "" : "&artist_name=" + Http.enc(a))
                    + (al.isEmpty() ? "" : "&album_name=" + Http.enc(al)));
            if (out.isEmpty() && !al.isEmpty()) {
                out = query("track_name=" + Http.enc(s) + (a.isEmpty() ? "" : "&artist_name=" + Http.enc(a)));
            }
        } else {
            String q = (a + " " + al).trim();
            if (q.isEmpty()) return new ArrayList<>();
            out = query("q=" + Http.enc(q));
            if (!al.isEmpty()) out = filterAlbum(out, al);
        }
        // synced first, then the order LRCLIB gave
        List<Record> sorted = new ArrayList<>(out);
        sorted.sort((x, y) -> Boolean.compare(y.isSynced(), x.isSynced()));
        return sorted;
    }

    public static Record byId(long id) throws Exception {
        Http.Response r = Http.request("GET", BASE + "/get/" + id, HEADERS, null, 15000);
        if (!r.ok()) throw new Exception("HTTP " + r.code);
        return new Record(new JSONObject(r.body));
    }

    /** The song called {@code title} on the given album, preferring synced lyrics and the closest length. */
    public static Record findInAlbum(String title, long durationMs, String artist, String album) throws Exception {
        String t = cleanTitle(title);
        List<Record> hits = query("track_name=" + Http.enc(t) + "&artist_name=" + Http.enc(artist)
                + "&album_name=" + Http.enc(album));
        Record best = pickInAlbum(hits, t, durationMs, album);
        if (best == null) {
            best = pickInAlbum(query("q=" + Http.enc(artist + " " + album + " " + t)), t, durationMs, album);
        }
        return best;
    }

    /** Pure matching step, separated for testing. */
    static Record pickInAlbum(List<Record> hits, String cleanTitle, long durationMs, String album) {
        String want = key(cleanTitle), albumKey = key(cleanTitle(album));
        Record best = null;
        double bestScore = Double.MAX_VALUE;
        for (Record r : hits) {
            if (r.text().trim().isEmpty()) continue;
            String rAlbum = key(cleanTitle(r.album));
            if (!rAlbum.equals(albumKey) && !rAlbum.contains(albumKey) && !albumKey.contains(rAlbum)) continue;
            String name = key(cleanTitle(r.track));
            double score;
            if (name.equals(want)) score = 0;
            else if (!want.isEmpty() && (name.startsWith(want) || want.startsWith(name))) score = 50;
            else continue;
            if (durationMs > 0) score += Math.abs(r.duration - durationMs / 1000.0);
            if (!r.isSynced()) score += 100;
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    static List<Record> filterAlbum(List<Record> in, String album) {
        String k = key(album);
        List<Record> out = new ArrayList<>();
        for (Record r : in) {
            String ra = key(cleanTitle(r.album));
            if (ra.equals(k) || ra.contains(k) || k.contains(ra)) out.add(r);
        }
        return out.isEmpty() ? in : out;
    }

    private static List<Record> query(String params) throws Exception {
        Http.Response r = Http.request("GET", BASE + "/search?" + params, HEADERS, null, 15000);
        if (!r.ok()) throw new Exception("LRCLIB HTTP " + r.code);
        return parse(new JSONArray(r.body));
    }

    static List<Record> parse(JSONArray arr) {
        List<Record> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(new Record(o));
        }
        return out;
    }

    public static String describe(Record r) {
        String d = r.duration > 0 ? Formatter.formatSeconds(Math.round(r.duration)) : "?";
        String kind = r.instrumental ? "Instrumental" : r.isSynced() ? "Synced" : r.plain.isEmpty() ? "No lyrics" : "Plain";
        return String.format(Locale.ROOT, "%s · %s · %s · %s", r.artist, r.album.isEmpty() ? "-" : r.album, d, kind);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
