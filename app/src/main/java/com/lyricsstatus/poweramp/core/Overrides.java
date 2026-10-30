package com.lyricsstatus.poweramp.core;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Lyrics chosen by hand in the search screen.
 *  - Song pins: these exact lyrics for one song (highest priority).
 *  - Album mappings: "lyrics for my album X come from album Y": each song is matched by name in Y.
 */
public final class Overrides {

    public static final class AlbumMap {
        public final String artist, album;

        AlbumMap(String artist, String album) {
            this.artist = artist;
            this.album = album;
        }
    }

    private final File file;
    private JSONObject songs = new JSONObject(), albums = new JSONObject();

    public Overrides(File file) {
        this.file = file;
        try {
            if (file.isFile()) {
                JSONObject o = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                songs = o.optJSONObject("songs") != null ? o.getJSONObject("songs") : songs;
                albums = o.optJSONObject("albums") != null ? o.getJSONObject("albums") : albums;
            }
        } catch (Exception ignored) {
        }
    }

    /** Key for an album of the playing track; null when the track has no album. */
    public static String albumKey(Track t) {
        if (t == null || t.album.isEmpty()) return null;
        return LyricsSearch.key(LyricsSearch.cleanArtist(t.artist)) + "\u0000" + LyricsSearch.key(LyricsSearch.cleanTitle(t.album));
    }

    public synchronized Lyrics song(Track t) {
        JSONObject o = songs.optJSONObject(t.key());
        if (o == null) return null;
        return LrcParser.parse(o.optString("lrc"), o.optString("source", "Picked"));
    }

    public synchronized boolean hasSong(Track t) {
        return t != null && songs.has(t.key());
    }

    public synchronized void pinSong(Track t, LyricsSearch.Record r) {
        try {
            JSONObject o = new JSONObject();
            o.put("lrc", r.text());
            o.put("source", "Picked: " + r.track + " — " + r.artist);
            o.put("id", r.id);
            songs.put(t.key(), o);
            save();
        } catch (Exception ignored) {
        }
    }

    public synchronized void unpinSong(Track t) {
        songs.remove(t.key());
        save();
    }

    public synchronized AlbumMap album(Track t) {
        String k = albumKey(t);
        JSONObject o = k == null ? null : albums.optJSONObject(k);
        return o == null ? null : new AlbumMap(o.optString("artist"), o.optString("album"));
    }

    public synchronized void mapAlbum(Track t, String artist, String album) {
        String k = albumKey(t);
        if (k == null) return;
        try {
            JSONObject o = new JSONObject();
            o.put("artist", artist);
            o.put("album", album);
            albums.put(k, o);
            save();
        } catch (Exception ignored) {
        }
    }

    public synchronized void unmapAlbum(Track t) {
        String k = albumKey(t);
        if (k != null) albums.remove(k);
        save();
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("songs", songs);
            o.put("albums", albums);
            Files.write(file.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
