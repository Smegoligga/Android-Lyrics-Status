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
    static final Map<String, String> HEADERS = Collections.singletonMap(
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

    /** LRCLIB is overloaded/down (HTTP 5xx after retries): not the app's fault, worth saying so. */
    public static final class ServiceDown extends Exception {
        ServiceDown(int code) {
            super("LRCLIB isn't responding right now (HTTP " + code + "). That's their server, not the app. "
                    + "Try again in a minute.");
        }
    }

    /** How requests are made; swapped out in tests. Returns the body of a 2xx, null for 404. */
    interface Fetcher {
        String get(String url) throws Exception;
    }

    static Fetcher fetcher = url -> {
        Http.Response r = Http.getRetry(url, HEADERS, 15000);
        if (r.code == 404) return null;
        if (r.code >= 500 || r.code == 429) throw new ServiceDown(r.code);
        if (!r.ok()) throw new Exception("LRCLIB HTTP " + r.code);
        return r.body;
    };

    /**
     * Everyone credited: the artist field split on "&", ",", "feat." etc., plus "(feat. X)" from the title.
     * The main artist comes first.
     */
    public static List<String> credits(String artist, String title) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String a : nz(artist).split("(?i)\\s*(,|;|/|&| feat\\.? | ft\\.? | featuring | x | with | and )\\s*")) {
            if (!a.trim().isEmpty()) out.add(a.trim());
        }
        java.util.regex.Matcher m = Pattern.compile("(?i)(?:feat\\.?|ft\\.?|featuring|with)\\s+([^)\\]]+)").matcher(nz(title));
        while (m.find()) {
            for (String a : m.group(1).split("(?i)\\s*(,|&| and | x )\\s*")) {
                if (!a.trim().isEmpty()) out.add(a.trim());
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Search queries to try, most specific first: the tags as they are, without the album, cleaned title with
     * the main artist, with each other credited artist alone, the title alone, then a free-text query.
     */
    static List<String> attempts(String title, String artist, String album) {
        java.util.LinkedHashSet<String> q = new java.util.LinkedHashSet<>();
        String raw = nz(title).trim(), clean = cleanTitle(title), alb = nz(album).trim();
        List<String> who = credits(artist, title);
        String main = who.isEmpty() ? "" : who.get(0);
        String fullArtist = nz(artist).trim();
        if (!raw.isEmpty() && !fullArtist.isEmpty() && !alb.isEmpty())
            q.add("track_name=" + Http.enc(raw) + "&artist_name=" + Http.enc(fullArtist) + "&album_name=" + Http.enc(alb));
        if (!raw.isEmpty() && !fullArtist.isEmpty())
            q.add("track_name=" + Http.enc(raw) + "&artist_name=" + Http.enc(fullArtist));
        if (!clean.isEmpty() && !main.isEmpty())
            q.add("track_name=" + Http.enc(clean) + "&artist_name=" + Http.enc(main));
        for (int i = 1; i < who.size(); i++)
            q.add("track_name=" + Http.enc(clean) + "&artist_name=" + Http.enc(who.get(i)));
        if (!clean.isEmpty()) q.add("track_name=" + Http.enc(clean));
        if (!clean.isEmpty() && !main.isEmpty()) q.add("q=" + Http.enc(clean + " " + main));
        return new ArrayList<>(q);
    }

    /** Free-text search: as typed, then without feat./prod./version tags. */
    public static List<Record> searchSimple(String text, boolean ignoreExtras) throws Exception {
        String t = nz(text).trim();
        if (t.isEmpty()) return new ArrayList<>();
        List<Record> out = query("q=" + Http.enc(t));
        if (out.isEmpty() && ignoreExtras) {
            String c = BARE_FEAT_INLINE.matcher(cleanTitle(t)).replaceAll(" ").replaceAll("\\s+", " ").trim();
            if (!c.isEmpty() && !c.equalsIgnoreCase(t)) out = query("q=" + Http.enc(c));
        }
        return sortSynced(out);
    }

    // "feat. Name" inside free text (up to the next bracket), for the simple search
    private static final Pattern BARE_FEAT_INLINE = Pattern.compile(
            "(?i)\\b(?:feat\\.?|ft\\.?|featuring|prod\\.?)\\s+[^()\\[\\]]*?(?=\\s+-\\s+|$)");

    private static List<Record> sortSynced(List<Record> in) {
        List<Record> sorted = new ArrayList<>(in);
        sorted.sort((x, y) -> Boolean.compare(y.isSynced(), x.isSynced()));
        return sorted;
    }

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
        List<Record> out = new ArrayList<>();
        if (!s.isEmpty()) {
            // first variation that finds anything wins
            List<String> tries = ignoreExtras ? attempts(song, artist, album)
                    : java.util.Arrays.asList("track_name=" + Http.enc(s) + (a.isEmpty() ? "" : "&artist_name=" + Http.enc(a))
                    + (al.isEmpty() ? "" : "&album_name=" + Http.enc(al)), "track_name=" + Http.enc(s)
                    + (a.isEmpty() ? "" : "&artist_name=" + Http.enc(a)));
            for (String q : tries) {
                out = query(q);
                if (!out.isEmpty()) break;
            }
        } else {
            String q = (a + " " + al).trim();
            if (q.isEmpty()) return new ArrayList<>();
            out = query("q=" + Http.enc(q));
            if (!al.isEmpty()) out = filterAlbum(out, al);
        }
        return sortSynced(out);
    }

    public static Record byId(long id) throws Exception {
        String body = fetcher.get(BASE + "/get/" + id);
        if (body == null) throw new Exception("Not found");
        return new Record(new JSONObject(body));
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

    static List<Record> query(String params) throws Exception {
        String body = fetcher.get(BASE + "/search?" + params);
        return body == null ? new ArrayList<>() : parse(new JSONArray(body));
    }

    /** Exact lookup (/api/get): needs title, artist and length; album optional. Null if not found. */
    static Record get(String title, String artist, String album, long durationMs) throws Exception {
        String body = fetcher.get(BASE + "/get?track_name=" + Http.enc(title) + "&artist_name=" + Http.enc(artist)
                + (album == null || album.isEmpty() ? "" : "&album_name=" + Http.enc(album))
                + "&duration=" + Math.round(durationMs / 1000.0));
        return body == null ? null : new Record(new JSONObject(body));
    }

    static List<Record> parse(JSONArray arr) {
        List<Record> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) out.add(new Record(o));
        }
        return out;
    }

    /** Sort modes for search results. */
    public static final int SORT_BEST = 0, SORT_LENGTH = 1;

    /**
     * Order results for the song that's playing.
     * BEST: synced lyrics whose length matches (within 3 s) and whose title matches come first, then by
     * length difference. LENGTH: purely by how close the length is. Without a known length, synced first.
     */
    public static List<Record> rank(List<Record> in, long durationMs, String title, int mode) {
        String want = key(cleanTitle(title));
        List<Record> out = new ArrayList<>(in);
        out.sort((x, y) -> Double.compare(score(x, durationMs, want, mode), score(y, durationMs, want, mode)));
        return out;
    }

    static double score(Record r, long durationMs, String wantKey, int mode) {
        double diff = durationMs > 0 && r.duration > 0 ? Math.abs(r.duration - durationMs / 1000.0) : 0;
        if (durationMs > 0 && r.duration <= 0) diff = 9999;
        if (mode == SORT_LENGTH) return diff + (r.isSynced() ? 0 : 0.001);
        double s = diff;
        if (!r.isSynced()) s += 10_000;            // plain lyrics can't follow the music
        if (durationMs > 0 && diff > 3) s += 1_000; // different version/edit
        if (!wantKey.isEmpty()) {
            String name = key(cleanTitle(r.track));
            if (!name.equals(wantKey)) s += name.contains(wantKey) || wantKey.contains(name) ? 100 : 500;
        }
        return s;
    }

    /** True when this record is a good automatic pick for the song: synced, title matches, length within 3 s. */
    public static boolean isGoodMatch(Record r, long durationMs, String title) {
        return score(r, durationMs, key(cleanTitle(title)), SORT_BEST) < 100;
    }

    /** "5:22 (−1 s)" style length with the difference to the playing song. */
    public static String lengthVsSong(Record r, long durationMs) {
        if (r.duration <= 0) return "length unknown";
        String len = Formatter.formatSeconds(Math.round(r.duration));
        if (durationMs <= 0) return len;
        long d = Math.round(r.duration - durationMs / 1000.0);
        return len + (d == 0 ? " (same length)" : " (" + (d > 0 ? "+" : "−") + Math.abs(d) + " s)");
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
