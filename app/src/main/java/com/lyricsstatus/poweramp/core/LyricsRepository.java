package com.lyricsstatus.poweramp.core;

import java.io.File;

/**
 * Finds lyrics for a track: sidecar .lrc → embedded tags → cache → LRCLIB.
 * Same idea as LyricsFetcher.ts in OvalQuilter's LyricsStatus (ordered sources, first hit wins, results cached),
 * with local sources first because they're instant and usually what the user curated.
 */
public final class LyricsRepository {

    public static final class Result {
        /** Synced lyrics to drive the status, or null. */
        public final Lyrics lyrics;
        /** Explains the outcome, shown in the app ("Embedded (FLAC)", "No synced lyrics found", …). */
        public final String info;

        Result(Lyrics lyrics, String info) {
            this.lyrics = lyrics;
            this.info = info;
        }
    }

    private final LyricsCache cache;
    private final Overrides overrides;
    private final LrcLibClient lrclib = new LrcLibClient();

    public LyricsRepository(LyricsCache cache) {
        this(cache, null);
    }

    public LyricsRepository(LyricsCache cache, Overrides overrides) {
        this.cache = cache;
        this.overrides = overrides;
    }

    public Overrides overrides() {
        return overrides;
    }

    /** Blocking; call off the main thread. {@code audio} may be null when the file isn't accessible. */
    public Result find(Track t, File audio, Settings s) {
        // Picked by hand: a pinned song wins over everything, then an album mapping
        if (overrides != null) {
            Lyrics pinned = overrides.song(t);
            if (pinned != null && !pinned.isEmpty()) return new Result(pinned, pinned.source);
            Overrides.AlbumMap m = overrides.album(t);
            if (m != null) {
                Lyrics l = fromMappedAlbum(t, m);
                if (l != null) return new Result(l, l.source);
            }
        }
        Lyrics plainLocal = null;
        String fileNote = audio == null ? "file not accessible" : null;

        if (audio != null && s.useLocalLrc) {
            Lyrics l = LocalLyricsFinder.find(audio, t);
            if (l != null && l.synced) return new Result(l, l.source);
            if (l != null) plainLocal = l;
        }
        if (audio != null && s.useEmbedded) {
            Lyrics l = TagLyricsReader.read(audio);
            if (l != null && l.synced) return new Result(l, l.source);
            if (l != null && plainLocal == null) plainLocal = l;
        }

        boolean tryOnline = s.useLrclib && (plainLocal == null || s.onlineWhenLocalUnsynced);
        if (tryOnline) {
            LyricsCache.Entry e = cache.get(t);
            if (e != null) {
                if (e.lyrics != null) return new Result(e.lyrics, e.lyrics.source);
                return new Result(null, e.instrumental ? "Instrumental (LRCLIB, cached)" : noneMessage(plainLocal, fileNote) + " (cached miss)");
            }
            try {
                LrcLibClient.Result r = lrclib.fetch(t);
                if (r.lyrics != null) {
                    cache.put(t, r.lyrics);
                    return new Result(r.lyrics, r.lyrics.source);
                }
                cache.putMiss(t, r.instrumental);
                if (r.instrumental) return new Result(null, "Instrumental (LRCLIB)");
            } catch (Exception ex) {
                return new Result(null, "LRCLIB error: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() != null ? " " + ex.getMessage() : ""));
            }
        }
        return new Result(null, noneMessage(plainLocal, fileNote));
    }

    /** Lyrics for this song from the album the user mapped its album to (cached per mapping). */
    private Lyrics fromMappedAlbum(Track t, Overrides.AlbumMap m) {
        String key = t.key() + "\u0000album:" + LyricsSearch.key(m.artist) + "\u0000" + LyricsSearch.key(m.album);
        LyricsCache.Entry e = cache.get(key);
        if (e != null) return e.lyrics;
        try {
            LyricsSearch.Record r = LyricsSearch.findInAlbum(t.title, t.durationMs, m.artist, m.album);
            if (r == null) {
                cache.putMiss(key, false);
                return null;
            }
            Lyrics l = r.lyrics("From album: " + m.album);
            cache.put(key, t, l);
            return l;
        } catch (Exception ex) {
            return null; // offline: fall back to the normal sources this time
        }
    }

    private static String noneMessage(Lyrics plainLocal, String fileNote) {
        if (plainLocal != null) return "Only unsynced lyrics (" + plainLocal.source + ")";
        return "No synced lyrics found" + (fileNote != null ? " (" + fileNote + ")" : "");
    }

    /**
     * For Poweramp's lyrics plugin request: online lyrics as LRC (or plain text if that's all LRCLIB has).
     * Blocking.
     */
    public String fetchForPoweramp(Track t, Settings s) {
        if (!s.useLrclib) return null;
        LyricsCache.Entry e = cache.get(t);
        if (e != null && e.lyrics != null) return e.lyrics.raw;
        if (e != null) return null;
        try {
            LrcLibClient.Result r = lrclib.fetch(t);
            if (r.lyrics != null) {
                cache.put(t, r.lyrics);
                return r.lyrics.raw;
            }
            if (r.plainFallback == null) cache.putMiss(t, r.instrumental);
            return r.plainFallback;
        } catch (Exception ex) {
            return null;
        }
    }

    public LyricsCache cache() {
        return cache;
    }
}
