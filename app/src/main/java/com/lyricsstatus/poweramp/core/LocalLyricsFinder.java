package com.lyricsstatus.poweramp.core;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Finds and reads sidecar .lrc files for an audio file, and resolves Poweramp-style paths. */
public final class LocalLyricsFinder {

    private static final long MAX_LRC = 2L * 1024 * 1024;

    private LocalLyricsFinder() {}

    /**
     * Poweramp reports paths like "primary/Music/a.flac" or "1A2B-3C4D/Music/a.flac" for its music folders,
     * or plain absolute paths. Content-provider paths ("@authority/…") can't be opened directly.
     */
    public static File resolve(String path) {
        if (path == null || path.isEmpty() || path.startsWith("@") || path.startsWith("content:")) return null;
        if (path.startsWith("file://")) path = path.substring(7);
        if (path.startsWith("/")) return new File(path);
        String p = path.replace(':', '/');
        int slash = p.indexOf('/');
        if (slash <= 0) return null;
        String volume = p.substring(0, slash);
        String rest = p.substring(slash + 1);
        if (volume.equalsIgnoreCase("primary")) return new File("/storage/emulated/0/" + rest);
        return new File("/storage/" + volume + "/" + rest);
    }

    /** Candidate .lrc files, in priority order. */
    public static List<File> candidates(File audio, Track t) {
        List<File> out = new ArrayList<>();
        File dir = audio.getParentFile();
        if (dir == null) return out;
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;

        List<String> wanted = new ArrayList<>();
        wanted.add(base + ".lrc");
        wanted.add(name + ".lrc"); // "song.flac.lrc"
        if (t != null && !t.artist.isEmpty() && !t.title.isEmpty()) {
            wanted.add(t.artist + " - " + t.title + ".lrc");
        }

        File[] dirs = {dir, new File(dir, "Lyrics"), new File(dir, "lyrics"), new File(dir, ".lyrics")};
        for (File d : dirs) {
            File[] files = d.listFiles();
            if (files == null) continue;
            for (String w : wanted) {
                for (File f : files) {
                    if (f.isFile() && f.getName().equalsIgnoreCase(w) && !out.contains(f)) out.add(f);
                }
            }
        }
        return out;
    }

    /** @return the first readable .lrc next to the audio file, synced preferred; null if none. */
    public static Lyrics find(File audio, Track t) {
        Lyrics plain = null;
        for (File f : candidates(audio, t)) {
            try {
                if (f.length() > MAX_LRC) continue;
                String text = readText(Files.readAllBytes(f.toPath()));
                Lyrics l = LrcParser.parse(text, "Local .lrc");
                if (l.isEmpty()) continue;
                if (l.synced) return l;
                if (plain == null) plain = l;
            } catch (IOException | SecurityException ignored) {
            }
        }
        return plain;
    }

    /** Decode with BOM detection, strict UTF-8, then a GBK/Windows-1252 guess. */
    public static String readText(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            // Non-UTF-8 LRCs are most often GBK (Chinese sites) or Windows-1252
            for (String cs : new String[]{"GBK", "windows-1252"}) {
                try {
                    return Charset.forName(cs).newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(b)).toString();
                } catch (Exception ignored) {
                }
            }
            return new String(b, StandardCharsets.ISO_8859_1);
        }
    }
}
