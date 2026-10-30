package com.lyricsstatus.poweramp.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * LRCLIB (https://lrclib.net) client. Based on LrcLibSource.ts from OvalQuilter's LyricsStatus,
 * extended with duration matching and a search fallback for local files whose tags don't match exactly.
 */
public final class LrcLibClient {

    public static final String SOURCE = "LRCLIB";
    private static final String BASE = "https://lrclib.net/api";
    private static final Map<String, String> HEADERS = Collections.singletonMap(
            "User-Agent", "AndroidLyricsStatus (https://github.com/Smegoligga/Android-Lyrics-Status)");

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
        boolean first = true;
        for (String title : titleVariants(t.title)) {
            for (String artist : artistVariants(t.artist)) {
                // 1) exact signature lookup (needs album + duration), only for the tags as-is
                if (first && !t.album.isEmpty() && t.durationMs > 0) {
                    String url = BASE + "/get?track_name=" + Http.enc(title) + "&artist_name=" + Http.enc(artist)
                            + "&album_name=" + Http.enc(t.album) + "&duration=" + Math.round(t.durationMs / 1000.0);
                    Http.Response r = Http.request("GET", url, HEADERS, null, 15000);
                    if (r.ok()) {
                        Result res = fromRecord(new JSONObject(r.body));
                        if (res.lyrics != null || res.instrumental) return res;
                        if (plain == null) plain = res.plainFallback;
                    }
                }
                first = false;
                // 2) search, pick the closest synced record
                String url = BASE + "/search?track_name=" + Http.enc(title)
                        + (artist.isEmpty() ? "" : "&artist_name=" + Http.enc(artist));
                Http.Response r = Http.request("GET", url, HEADERS, null, 15000);
                if (!r.ok()) continue;
                JSONArray arr = new JSONArray(r.body);
                JSONObject best = pickBest(arr, t.durationMs);
                if (best != null) {
                    Result res = fromRecord(best);
                    if (res.lyrics != null || res.instrumental) return res;
                }
                if (plain == null) plain = firstPlain(arr, t.durationMs);
            }
        }
        return new Result(null, false, plain);
    }

    /** Synced records within ±3 s (or any, if duration unknown); the closest duration wins. */
    static JSONObject pickBest(JSONArray arr, long durationMs) {
        JSONObject best = null;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            boolean synced = !o.optString("syncedLyrics", "").trim().isEmpty() && !o.isNull("syncedLyrics");
            boolean instrumental = o.optBoolean("instrumental", false);
            if (!synced && !instrumental) continue;
            double diff = durationMs > 0 ? Math.abs(o.optDouble("duration", 0) - durationMs / 1000.0) : 0;
            if (durationMs > 0 && diff > 3) continue;
            // prefer synced over instrumental at equal distance
            if (instrumental && !synced) diff += 0.5;
            if (diff < bestDiff) {
                bestDiff = diff;
                best = o;
            }
        }
        return best;
    }

    private static String firstPlain(JSONArray arr, long durationMs) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.isNull("plainLyrics")) continue;
            double diff = durationMs > 0 ? Math.abs(o.optDouble("duration", 0) - durationMs / 1000.0) : 0;
            if (diff <= 3) return o.optString("plainLyrics", null);
        }
        return null;
    }

    private static Result fromRecord(JSONObject o) {
        String synced = o.isNull("syncedLyrics") ? "" : o.optString("syncedLyrics", "");
        String plain = o.isNull("plainLyrics") ? null : o.optString("plainLyrics", null);
        if (!synced.trim().isEmpty()) {
            Lyrics l = LrcParser.parse(synced, SOURCE);
            if (l.synced && !l.isEmpty()) return new Result(l, false, plain);
        }
        if (o.optBoolean("instrumental", false)) return new Result(null, true, null);
        return new Result(null, false, plain);
    }

    /** Original title first, then OvalQuilter's " (…)" strip, then common suffixes. */
    static Set<String> titleVariants(String title) {
        Set<String> v = new LinkedHashSet<>();
        String t = title.trim();
        if (!t.isEmpty()) v.add(t);
        String noParens = t.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "").trim();
        if (!noParens.isEmpty()) v.add(noParens);
        String noDash = noParens.replaceAll("(?i)\\s+-\\s+(.*(remaster|version|mix|edit|live|mono|stereo|single|radio).*)$", "").trim();
        if (!noDash.isEmpty()) v.add(noDash);
        return v;
    }

    /** Full artist first, then just the first credited artist. */
    static Set<String> artistVariants(String artist) {
        Set<String> v = new LinkedHashSet<>();
        String a = artist.trim();
        v.add(a);
        String first = a.split("(?i)\\s*(,|;|/|&| feat\\.? | ft\\.? | featuring | x | and )\\s*")[0].trim();
        if (!first.isEmpty()) v.add(first);
        return v;
    }
}
