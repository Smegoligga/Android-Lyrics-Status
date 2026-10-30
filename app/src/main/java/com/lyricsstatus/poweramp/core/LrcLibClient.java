package com.lyricsstatus.poweramp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Automatic LRCLIB (https://lrclib.net) lookup. Based on LrcLibSource.ts from OvalQuilter's LyricsStatus,
 * extended to try several variations before giving up:
 *  1. exact lookup with the tags as they are (and with a cleaned title + main artist)
 *  2. searches: tags as-is, without the album, cleaned title + main artist, + each other credited artist,
 *     title alone, then free text (see {@link LyricsSearch#attempts})
 * Loose attempts (title alone / free text) are only accepted when the artist or the song length matches.
 * Busy-server errors are retried and then reported, never cached as "not found".
 */
public final class LrcLibClient {

    public static final String SOURCE = "LRCLIB";
    /** Upper bound on search requests per song, to stay polite to LRCLIB. */
    private static final int MAX_SEARCHES = 7;

    /** Result of a lookup: lyrics, "instrumental", or nothing. */
    public static final class Result {
        public final Lyrics lyrics;
        public final boolean instrumental;
        public final String plainFallback;

        Result(Lyrics lyrics, boolean instrumental, String plainFallback) {
            this.lyrics = lyrics;
            this.instrumental = instrumental;
            this.plainFallback = plainFallback;
        }
    }

    public Result fetch(Track t) throws Exception {
        String plain = null;
        List<String> who = LyricsSearch.credits(t.artist, t.title);
        String main = who.isEmpty() ? t.artist : who.get(0);
        String clean = LyricsSearch.cleanTitle(t.title);
        List<String> whoKeys = new ArrayList<>();
        for (String w : who) whoKeys.add(LyricsSearch.key(w));

        // 1) exact lookups (LRCLIB needs the length for these)
        if (t.durationMs > 0 && !t.artist.isEmpty()) {
            LyricsSearch.Record r = LyricsSearch.get(t.title, t.artist, t.album, t.durationMs);
            Result res = from(r);
            if (res != null && (res.lyrics != null || res.instrumental)) return res;
            if (r != null && !r.plain.isEmpty()) plain = r.plain;
            if (!clean.equals(t.title.trim()) || !main.equals(t.artist.trim())) {
                r = LyricsSearch.get(clean, main, "", t.durationMs);
                res = from(r);
                if (res != null && (res.lyrics != null || res.instrumental)) return res;
                if (plain == null && r != null && !r.plain.isEmpty()) plain = r.plain;
            }
        }

        // 2) searches, most specific first
        int n = 0;
        for (String q : LyricsSearch.attempts(t.title, t.artist, t.album)) {
            if (n++ >= MAX_SEARCHES) break;
            boolean loose = q.startsWith("q=") || !q.contains("artist_name=");
            List<LyricsSearch.Record> recs = LyricsSearch.query(q);
            LyricsSearch.Record best = pick(recs, t.durationMs, loose ? whoKeys : null);
            Result res = from(best);
            if (res != null && (res.lyrics != null || res.instrumental)) return res;
            if (plain == null) plain = firstPlain(recs, t.durationMs);
        }
        return new Result(null, false, plain);
    }

    /**
     * Closest synced (or instrumental) record within ±3 s. For loose searches the artist must match one of
     * the credited artists, or, if it doesn't, the length must be within 1.5 s.
     */
    static LyricsSearch.Record pick(List<LyricsSearch.Record> recs, long durationMs, List<String> looseArtistKeys) {
        LyricsSearch.Record best = null;
        double bestDiff = Double.MAX_VALUE;
        for (LyricsSearch.Record r : recs) {
            if (!r.isSynced() && !r.instrumental) continue;
            double diff = durationMs > 0 ? Math.abs(r.duration - durationMs / 1000.0) : 0;
            if (durationMs > 0 && diff > 3) continue;
            if (looseArtistKeys != null) {
                boolean artistOk = artistMatches(r.artist, looseArtistKeys);
                if (!artistOk && !(durationMs > 0 && diff <= 1.5)) continue;
            }
            if (r.instrumental && !r.isSynced()) diff += 0.5; // prefer real lyrics at equal distance
            if (diff < bestDiff) {
                bestDiff = diff;
                best = r;
            }
        }
        return best;
    }

    static boolean artistMatches(String recordArtist, List<String> keys) {
        for (String part : LyricsSearch.credits(recordArtist, "")) {
            String k = LyricsSearch.key(part);
            if (k.isEmpty()) continue;
            for (String want : keys) {
                if (!want.isEmpty() && (k.equals(want) || k.contains(want) || want.contains(k))) return true;
            }
        }
        return false;
    }

    private static String firstPlain(List<LyricsSearch.Record> recs, long durationMs) {
        for (LyricsSearch.Record r : recs) {
            if (r.plain.isEmpty()) continue;
            double diff = durationMs > 0 ? Math.abs(r.duration - durationMs / 1000.0) : 0;
            if (diff <= 3) return r.plain;
        }
        return null;
    }

    private static Result from(LyricsSearch.Record r) {
        if (r == null) return null;
        if (r.isSynced()) {
            Lyrics l = LrcParser.parse(r.synced, SOURCE);
            if (l.synced && !l.isEmpty()) return new Result(l, false, r.plain.isEmpty() ? null : r.plain);
        }
        if (r.instrumental) return new Result(null, true, null);
        return new Result(null, false, r.plain.isEmpty() ? null : r.plain);
    }
}
