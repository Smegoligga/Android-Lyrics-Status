package com.lyricsstatus.poweramp.core;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Disk cache for online lyrics, like LyricsFetcher.cacheLyrics in OvalQuilter's LyricsStatus.
 * Also remembers misses for a few days so unknown songs don't hit LRCLIB on every play.
 */
public final class LyricsCache {

    private static final long MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000;

    private final File dir;

    public LyricsCache(File dir) {
        this.dir = dir;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Cached state for a track. */
    public static final class Entry {
        public final Lyrics lyrics; // null = known miss
        public final boolean instrumental;

        Entry(Lyrics lyrics, boolean instrumental) {
            this.lyrics = lyrics;
            this.instrumental = instrumental;
        }
    }

    public synchronized Entry get(Track t) {
        File f = file(t);
        if (!f.isFile()) return null;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (o.optBoolean("miss", false)) {
                if (System.currentTimeMillis() - o.optLong("at", 0) > MISS_TTL_MS) return null;
                return new Entry(null, o.optBoolean("instrumental", false));
            }
            Lyrics l = LrcParser.parse(o.getString("lrc"), o.optString("source", LrcLibClient.SOURCE) + " (cached)");
            return new Entry(l, false);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void put(Track t, Lyrics l) {
        try {
            JSONObject o = new JSONObject();
            o.put("lrc", l.raw);
            o.put("source", l.source);
            o.put("title", t.title);
            o.put("artist", t.artist);
            Files.write(file(t).toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public synchronized void putMiss(Track t, boolean instrumental) {
        try {
            JSONObject o = new JSONObject();
            o.put("miss", true);
            o.put("instrumental", instrumental);
            o.put("at", System.currentTimeMillis());
            Files.write(file(t).toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public synchronized void remove(Track t) {
        //noinspection ResultOfMethodCallIgnored
        file(t).delete();
    }

    public synchronized int clear() {
        int n = 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.delete()) n++;
        return n;
    }

    private File file(Track t) {
        return new File(dir, sha1(t.key()) + ".json");
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
