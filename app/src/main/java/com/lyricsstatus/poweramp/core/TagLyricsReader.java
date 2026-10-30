package com.lyricsstatus.poweramp.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads lyrics embedded in audio file tags without any third-party library.
 *
 * Supported:
 *  - ID3v2.2/2.3/2.4 (MP3, AAC, and ID3 chunks inside WAV/AIFF): USLT, SYLT (ms timestamps), TXXX:LYRICS
 *  - FLAC Vorbis comments: LYRICS, SYNCEDLYRICS, UNSYNCEDLYRICS
 *  - Ogg Vorbis / Opus comments (same keys)
 *  - MP4 / M4A / ALAC: the ©lyr atom
 *  - APEv2 (APE, MPC, WavPack): "Lyrics"
 *
 * Synced candidates win over plain ones.
 */
public final class TagLyricsReader {

    private static final long MAX_BLOCK = 64L * 1024 * 1024;

    private final List<Candidate> candidates = new ArrayList<>();

    private static final class Candidate {
        final Lyrics lyrics;

        Candidate(Lyrics lyrics) {
            this.lyrics = lyrics;
        }
    }

    private TagLyricsReader() {}

    /** @return the best embedded lyrics in the file, or null if none. */
    public static Lyrics read(File file) {
        if (file == null || !file.isFile() || !file.canRead()) return null;
        TagLyricsReader r = new TagLyricsReader();
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            r.readAll(raf);
        } catch (IOException | RuntimeException e) {
            // Corrupt/odd tags: fall through with whatever we found so far
        }
        return r.best();
    }

    private Lyrics best() {
        Lyrics plain = null;
        for (Candidate c : candidates) {
            if (c.lyrics.isEmpty()) continue;
            if (c.lyrics.synced) return c.lyrics;
            if (plain == null) plain = c.lyrics;
        }
        return plain;
    }

    private void addText(String text, String format) {
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        candidates.add(new Candidate(LrcParser.parse(text, "Embedded (" + format + ")")));
    }

    private void readAll(RandomAccessFile raf) throws IOException {
        byte[] head = new byte[12];
        raf.seek(0);
        int n = raf.read(head);
        if (n < 4) return;

        if (startsWith(head, "ID3")) {
            long end = readId3(raf, 0, "ID3");
            // FLAC/other with an ID3 prepended
            if (end > 0 && end < raf.length() - 4) {
                raf.seek(end);
                byte[] h2 = new byte[4];
                if (raf.read(h2) == 4 && startsWith(h2, "fLaC")) readFlac(raf, end + 4);
            }
            readApe(raf);
        } else if (startsWith(head, "fLaC")) {
            readFlac(raf, 4);
        } else if (startsWith(head, "OggS")) {
            readOgg(raf);
        } else if (n >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') {
            readMp4(raf, 0, raf.length(), 0);
        } else if (startsWith(head, "RIFF") && n >= 12 && head[8] == 'W' && head[9] == 'A') {
            readRiff(raf);
        } else if (startsWith(head, "FORM") && n >= 12 && head[8] == 'A' && head[9] == 'I') {
            readAiff(raf);
        } else {
            readApe(raf);
        }
    }

    // ------------------------------------------------------------------ ID3v2

    /** @return offset just after the tag, or -1 */
    private long readId3(RandomAccessFile raf, long offset, String fmt) throws IOException {
        raf.seek(offset);
        byte[] h = new byte[10];
        if (raf.read(h) != 10 || !startsWith(h, "ID3")) return -1;
        int major = h[3] & 0xFF;
        int flags = h[5] & 0xFF;
        long size = syncsafe(h, 6);
        long tagEnd = offset + 10 + size;
        if (size > MAX_BLOCK || major < 2 || major > 4) return tagEnd;

        String label = fmt.equals("ID3") ? "ID3v2." + major : fmt;
        boolean tagUnsync = (flags & 0x80) != 0;

        if (tagUnsync && major < 4) {
            // Whole-tag unsynchronisation (old v2.2/2.3 files): read and decode in memory
            byte[] body = new byte[(int) size];
            raf.readFully(body);
            body = deUnsync(body, 0, body.length);
            parseId3Frames(new ByteSource(body), major, flags, label, false);
        } else {
            parseId3Frames(new FileSource(raf, offset + 10, tagEnd), major, flags, label, tagUnsync);
        }
        return tagEnd;
    }

    private void parseId3Frames(Source src, int major, int tagFlags, String label, boolean v24TagUnsync) throws IOException {
        long pos = src.start();
        long end = src.end();

        // Extended header
        if ((tagFlags & 0x40) != 0 && major >= 3) {
            byte[] eh = src.read(pos, 4);
            if (eh == null) return;
            long ehSize = major == 4 ? syncsafe(eh, 0) : be32(eh, 0);
            pos += major == 4 ? ehSize : ehSize + 4;
        }

        int headerLen = major == 2 ? 6 : 10;
        int idLen = major == 2 ? 3 : 4;

        while (pos + headerLen <= end) {
            byte[] fh = src.read(pos, headerLen);
            if (fh == null || fh[0] == 0) break; // padding
            String id = new String(fh, 0, idLen, StandardCharsets.ISO_8859_1);
            if (!id.matches("[A-Z0-9]+")) break;

            long fsize;
            int fflags = 0;
            if (major == 2) {
                fsize = ((fh[3] & 0xFFL) << 16) | ((fh[4] & 0xFFL) << 8) | (fh[5] & 0xFFL);
            } else if (major == 3) {
                fsize = be32(fh, 4);
                fflags = ((fh[8] & 0xFF) << 8) | (fh[9] & 0xFF);
            } else {
                fsize = syncsafe(fh, 4);
                // Some taggers write plain 32-bit sizes in v2.4; prefer whichever lands on a valid next frame
                long plain = be32(fh, 4);
                if (plain != fsize && !validFrameAt(src, pos + headerLen + fsize, end)
                        && validFrameAt(src, pos + headerLen + plain, end)) {
                    fsize = plain;
                }
                fflags = ((fh[8] & 0xFF) << 8) | (fh[9] & 0xFF);
            }
            long dataPos = pos + headerLen;
            if (fsize <= 0 || dataPos + fsize > end) break;

            boolean wanted = id.equals("USLT") || id.equals("ULT") || id.equals("SYLT") || id.equals("SLT")
                    || id.equals("TXXX") || id.equals("TXX");
            if (wanted && fsize < MAX_BLOCK) {
                byte[] data = src.read(dataPos, (int) fsize);
                if (data != null) {
                    boolean compressed, encrypted, unsync, dataLen;
                    if (major == 3) {
                        compressed = (fflags & 0x0080) != 0;
                        encrypted = (fflags & 0x0040) != 0;
                        int skip = 0;
                        if (compressed) skip += 4;
                        if ((fflags & 0x0020) != 0) skip += 1; // grouping
                        if (encrypted) skip += 1;
                        if (!compressed && !encrypted && skip > 0 && skip < data.length) {
                            data = slice(data, skip, data.length);
                        }
                        unsync = false;
                    } else if (major == 4) {
                        compressed = (fflags & 0x0008) != 0;
                        encrypted = (fflags & 0x0004) != 0;
                        unsync = (fflags & 0x0002) != 0 || v24TagUnsync;
                        dataLen = (fflags & 0x0001) != 0;
                        int skip = 0;
                        if ((fflags & 0x0040) != 0) skip += 1; // grouping id
                        if (encrypted) skip += 1;
                        if (dataLen) skip += 4;
                        if (skip > 0 && skip < data.length) data = slice(data, skip, data.length);
                        if (unsync) data = deUnsync(data, 0, data.length);
                    } else {
                        compressed = false;
                        encrypted = false;
                    }
                    if (!compressed && !encrypted) handleId3Frame(id, data, label);
                }
            }
            pos = dataPos + fsize;
        }
    }

    private boolean validFrameAt(Source src, long pos, long end) throws IOException {
        if (pos == end) return true;
        if (pos + 10 > end) return pos <= end;
        byte[] b = src.read(pos, 4);
        if (b == null) return false;
        if (b[0] == 0) return true; // padding
        for (byte x : b) {
            if (!((x >= 'A' && x <= 'Z') || (x >= '0' && x <= '9'))) return false;
        }
        return true;
    }

    private void handleId3Frame(String id, byte[] d, String label) {
        if (d.length < 2) return;
        int enc = d[0] & 0xFF;
        switch (id) {
            case "USLT":
            case "ULT": {
                if (d.length < 5) return;
                int p = 4; // encoding + 3-byte language
                p = skipTerminated(d, p, enc);
                addText(decode(d, p, d.length, enc), label);
                break;
            }
            case "TXXX":
            case "TXX": {
                int descEnd = findTerminator(d, 1, enc);
                String desc = decode(d, 1, descEnd, enc).trim().toUpperCase(Locale.ROOT);
                if (desc.contains("LYRICS")) {
                    int p = descEnd + terminatorLength(enc);
                    addText(decode(d, p, d.length, enc), label);
                }
                break;
            }
            case "SYLT":
            case "SLT": {
                if (d.length < 7) return;
                int format = d[4] & 0xFF; // 1 = MPEG frames, 2 = milliseconds
                int p = 6;
                p = skipTerminated(d, p, enc);
                if (format != 2) return;
                List<String> texts = new ArrayList<>();
                List<Long> times = new ArrayList<>();
                while (p < d.length) {
                    int te = findTerminator(d, p, enc);
                    String t = decode(d, p, te, enc);
                    p = te + terminatorLength(enc);
                    if (p + 4 > d.length) break;
                    long ts = be32(d, p);
                    p += 4;
                    texts.add(t);
                    times.add(ts);
                }
                Lyrics l = syltToLyrics(texts, times, label);
                if (l != null) candidates.add(new Candidate(l));
                break;
            }
            default:
                break;
        }
    }

    /**
     * SYLT entries are either whole lines or karaoke syllables where a leading newline starts a new line.
     */
    static Lyrics syltToLyrics(List<String> texts, List<Long> times, String label) {
        if (texts.isEmpty()) return null;
        boolean fragments = false;
        for (int i = 1; i < texts.size(); i++) {
            String t = texts.get(i);
            if (t.startsWith("\n") || t.startsWith("\r")) {
                fragments = true;
                break;
            }
        }
        List<Lyrics.Line> lines = new ArrayList<>();
        StringBuilder lrc = new StringBuilder();
        if (!fragments) {
            for (int i = 0; i < texts.size(); i++) {
                String t = texts.get(i).replaceAll("[\\r\\n]+", " ").trim();
                lines.add(new Lyrics.Line(times.get(i), t));
            }
        } else {
            StringBuilder cur = null;
            long curTime = 0;
            for (int i = 0; i < texts.size(); i++) {
                String t = texts.get(i);
                boolean newLine = cur == null || t.startsWith("\n") || t.startsWith("\r");
                if (newLine) {
                    if (cur != null) lines.add(new Lyrics.Line(curTime, cur.toString().trim()));
                    cur = new StringBuilder();
                    curTime = times.get(i);
                }
                cur.append(t.replaceAll("[\\r\\n]+", ""));
            }
            if (cur != null) lines.add(new Lyrics.Line(curTime, cur.toString().trim()));
        }
        lines.sort((a, b) -> Long.compare(a.time, b.time));
        for (Lyrics.Line l : lines) lrc.append(Formatter.lrcStamp(l.time)).append(l.text).append('\n');
        return new Lyrics(lines, true, "Embedded (" + label + " SYLT)", lrc.toString());
    }

    // ------------------------------------------------------------------ FLAC

    private void readFlac(RandomAccessFile raf, long pos) throws IOException {
        long len = raf.length();
        while (pos + 4 <= len) {
            raf.seek(pos);
            int hdr = raf.readUnsignedByte();
            boolean last = (hdr & 0x80) != 0;
            int type = hdr & 0x7F;
            long size = ((long) raf.readUnsignedByte() << 16) | ((long) raf.readUnsignedByte() << 8) | raf.readUnsignedByte();
            if (type == 4 && size < MAX_BLOCK) {
                byte[] b = new byte[(int) size];
                raf.readFully(b);
                parseVorbisComments(b, 0, "FLAC");
            }
            pos += 4 + size;
            if (last) break;
        }
    }

    private void parseVorbisComments(byte[] b, int p, String label) {
        if (p + 4 > b.length) return;
        long vendorLen = le32(b, p);
        p += 4 + (int) vendorLen;
        if (p + 4 > b.length || vendorLen < 0) return;
        long count = le32(b, p);
        p += 4;
        String synced = null, plain = null;
        for (long i = 0; i < count && p + 4 <= b.length; i++) {
            long l = le32(b, p);
            p += 4;
            if (l < 0 || p + l > b.length) break;
            String kv = new String(b, p, (int) l, StandardCharsets.UTF_8);
            p += (int) l;
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String key = kv.substring(0, eq).toUpperCase(Locale.ROOT);
            String val = kv.substring(eq + 1);
            if (key.equals("SYNCEDLYRICS")) synced = val;
            else if (key.equals("LYRICS") || key.startsWith("LYRICS-") || key.equals("UNSYNCEDLYRICS")
                    || key.equals("UNSYNCED LYRICS")) {
                if (plain == null || (!LrcParser.looksSynced(plain) && LrcParser.looksSynced(val))) plain = val;
            }
        }
        if (synced != null) addText(synced, label);
        if (plain != null) addText(plain, label);
    }

    // ------------------------------------------------------------------ Ogg (Vorbis / Opus)

    private void readOgg(RandomAccessFile raf) throws IOException {
        long pos = 0;
        long len = raf.length();
        int packetIndex = 0;
        long serial = -1;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        byte[] hdr = new byte[27];
        while (pos + 27 <= len) {
            raf.seek(pos);
            raf.readFully(hdr);
            if (!startsWith(hdr, "OggS")) return;
            long pageSerial = le32(hdr, 14);
            int segs = hdr[26] & 0xFF;
            byte[] lacing = new byte[segs];
            raf.readFully(lacing);
            long dataPos = pos + 27 + segs;
            int pageLen = 0;
            for (byte s : lacing) pageLen += s & 0xFF;
            if (serial == -1) serial = pageSerial;
            if (pageSerial == serial) {
                byte[] data = new byte[pageLen];
                raf.seek(dataPos);
                raf.readFully(data);
                int off = 0;
                for (int i = 0; i < segs; i++) {
                    int s = lacing[i] & 0xFF;
                    packet.write(data, off, s);
                    off += s;
                    if (packet.size() > MAX_BLOCK) return;
                    if (s < 255) { // end of packet
                        if (packetIndex == 1) {
                            byte[] pk = packet.toByteArray();
                            if (pk.length > 7 && pk[0] == 3 && new String(pk, 1, 6, StandardCharsets.ISO_8859_1).equals("vorbis")) {
                                parseVorbisComments(pk, 7, "Ogg Vorbis");
                            } else if (pk.length > 8 && new String(pk, 0, 8, StandardCharsets.ISO_8859_1).equals("OpusTags")) {
                                parseVorbisComments(pk, 8, "Opus");
                            }
                            return;
                        }
                        packetIndex++;
                        packet.reset();
                    }
                }
            }
            pos = dataPos + pageLen;
        }
    }

    // ------------------------------------------------------------------ MP4

    private void readMp4(RandomAccessFile raf, long start, long end, int depth) throws IOException {
        if (depth > 8) return;
        long pos = start;
        byte[] h = new byte[8];
        while (pos + 8 <= end) {
            raf.seek(pos);
            raf.readFully(h);
            long size = be32(h, 0);
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            long hdrLen = 8;
            if (size == 1) {
                size = raf.readLong();
                hdrLen = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < hdrLen || pos + size > end) return;
            long cStart = pos + hdrLen, cEnd = pos + size;
            switch (type) {
                case "moov":
                case "udta":
                case "ilst":
                case "trak":
                    readMp4(raf, cStart, cEnd, depth + 1);
                    break;
                case "meta":
                    // Full box (version+flags) in ISO files; QuickTime-style meta has none
                    raf.seek(cStart);
                    byte[] probe = new byte[8];
                    raf.readFully(probe);
                    String inner = new String(probe, 4, 4, StandardCharsets.ISO_8859_1);
                    boolean qt = inner.equals("hdlr") || inner.equals("ilst") || inner.equals("keys");
                    readMp4(raf, qt ? cStart : cStart + 4, cEnd, depth + 1);
                    break;
                case "©lyr":
                    readMp4Data(raf, cStart, cEnd);
                    break;
                default:
                    break;
            }
            pos += size;
        }
    }

    private void readMp4Data(RandomAccessFile raf, long start, long end) throws IOException {
        long pos = start;
        byte[] h = new byte[8];
        while (pos + 16 <= end) {
            raf.seek(pos);
            raf.readFully(h);
            long size = be32(h, 0);
            String type = new String(h, 4, 4, StandardCharsets.ISO_8859_1);
            if (size < 16 || pos + size > end) return;
            if (type.equals("data") && size - 16 < MAX_BLOCK) {
                raf.seek(pos + 16); // skip type indicator + locale
                byte[] b = new byte[(int) (size - 16)];
                raf.readFully(b);
                addText(new String(b, StandardCharsets.UTF_8), "MP4");
            }
            pos += size;
        }
    }

    // ------------------------------------------------------------------ WAV / AIFF

    private void readRiff(RandomAccessFile raf) throws IOException {
        long pos = 12, len = raf.length();
        byte[] h = new byte[8];
        while (pos + 8 <= len) {
            raf.seek(pos);
            raf.readFully(h);
            String id = new String(h, 0, 4, StandardCharsets.ISO_8859_1);
            long size = le32(h, 4);
            if (id.equalsIgnoreCase("id3 ")) readId3(raf, pos + 8, "WAV ID3");
            pos += 8 + size + (size & 1);
        }
    }

    private void readAiff(RandomAccessFile raf) throws IOException {
        long pos = 12, len = raf.length();
        byte[] h = new byte[8];
        while (pos + 8 <= len) {
            raf.seek(pos);
            raf.readFully(h);
            String id = new String(h, 0, 4, StandardCharsets.ISO_8859_1);
            long size = be32(h, 4);
            if (id.equalsIgnoreCase("ID3 ")) readId3(raf, pos + 8, "AIFF ID3");
            pos += 8 + size + (size & 1);
        }
    }

    // ------------------------------------------------------------------ APEv2

    private void readApe(RandomAccessFile raf) throws IOException {
        long len = raf.length();
        long[] footers = {len - 32, len - 128 - 32}; // with or without trailing ID3v1
        for (long f : footers) {
            if (f < 0) continue;
            raf.seek(f);
            byte[] ft = new byte[32];
            raf.readFully(ft);
            if (!startsWith(ft, "APETAGEX")) continue;
            long tagSize = le32(ft, 12); // items + footer
            long items = le32(ft, 16);
            if (tagSize > MAX_BLOCK || tagSize < 32) return;
            long start = f + 32 - tagSize;
            if (start < 0) return;
            byte[] b = new byte[(int) (tagSize - 32)];
            raf.seek(start);
            raf.readFully(b);
            int p = 0;
            for (long i = 0; i < items && p + 8 < b.length; i++) {
                long vlen = le32(b, p);
                p += 8; // value size + flags
                int ke = p;
                while (ke < b.length && b[ke] != 0) ke++;
                String key = new String(b, p, ke - p, StandardCharsets.ISO_8859_1);
                p = ke + 1;
                if (vlen < 0 || p + vlen > b.length) return;
                if (key.equalsIgnoreCase("Lyrics") || key.equalsIgnoreCase("SyncedLyrics")
                        || key.equalsIgnoreCase("UnsyncedLyrics")) {
                    addText(new String(b, p, (int) vlen, StandardCharsets.UTF_8), "APEv2");
                }
                p += (int) vlen;
            }
            return;
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface Source {
        long start();

        long end();

        byte[] read(long pos, int len) throws IOException;
    }

    private static final class FileSource implements Source {
        final RandomAccessFile raf;
        final long start, end;

        FileSource(RandomAccessFile raf, long start, long end) {
            this.raf = raf;
            this.start = start;
            this.end = end;
        }

        public long start() { return start; }

        public long end() { return end; }

        public byte[] read(long pos, int len) throws IOException {
            if (pos < start || pos + len > end) return null;
            byte[] b = new byte[len];
            raf.seek(pos);
            raf.readFully(b);
            return b;
        }
    }

    private static final class ByteSource implements Source {
        final byte[] b;

        ByteSource(byte[] b) { this.b = b; }

        public long start() { return 0; }

        public long end() { return b.length; }

        public byte[] read(long pos, int len) {
            if (pos < 0 || pos + len > b.length) return null;
            return slice(b, (int) pos, (int) pos + len);
        }
    }

    private static boolean startsWith(byte[] b, String s) {
        if (b.length < s.length()) return false;
        for (int i = 0; i < s.length(); i++) if (b[i] != (byte) s.charAt(i)) return false;
        return true;
    }

    private static byte[] slice(byte[] b, int from, int to) {
        byte[] r = new byte[to - from];
        System.arraycopy(b, from, r, 0, r.length);
        return r;
    }

    private static long syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7FL) << 21) | ((b[o + 1] & 0x7FL) << 14) | ((b[o + 2] & 0x7FL) << 7) | (b[o + 3] & 0x7FL);
    }

    private static long be32(byte[] b, int o) {
        return ((b[o] & 0xFFL) << 24) | ((b[o + 1] & 0xFFL) << 16) | ((b[o + 2] & 0xFFL) << 8) | (b[o + 3] & 0xFFL);
    }

    private static long le32(byte[] b, int o) {
        return ((b[o + 3] & 0xFFL) << 24) | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 1] & 0xFFL) << 8) | (b[o] & 0xFFL);
    }

    private static byte[] deUnsync(byte[] b, int from, int to) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(to - from);
        for (int i = from; i < to; i++) {
            out.write(b[i]);
            if ((b[i] & 0xFF) == 0xFF && i + 1 < to && b[i + 1] == 0) i++;
        }
        return out.toByteArray();
    }

    private static int terminatorLength(int enc) {
        return (enc == 1 || enc == 2) ? 2 : 1;
    }

    private static int findTerminator(byte[] d, int from, int enc) {
        if (enc == 1 || enc == 2) {
            for (int i = from; i + 1 < d.length; i += 2) if (d[i] == 0 && d[i + 1] == 0) return i;
        } else {
            for (int i = from; i < d.length; i++) if (d[i] == 0) return i;
        }
        return d.length;
    }

    private static int skipTerminated(byte[] d, int from, int enc) {
        int t = findTerminator(d, from, enc);
        return Math.min(d.length, t + terminatorLength(enc));
    }

    private static String decode(byte[] d, int from, int to, int enc) {
        if (from >= to) return "";
        Charset cs;
        switch (enc) {
            case 1:
                cs = StandardCharsets.UTF_16; // BOM-detecting
                if (to - from >= 2 && !((d[from] == (byte) 0xFF && d[from + 1] == (byte) 0xFE)
                        || (d[from] == (byte) 0xFE && d[from + 1] == (byte) 0xFF))) {
                    cs = StandardCharsets.UTF_16LE; // missing BOM: most writers use LE
                }
                break;
            case 2:
                cs = StandardCharsets.UTF_16BE;
                break;
            case 3:
                cs = StandardCharsets.UTF_8;
                break;
            default:
                cs = StandardCharsets.ISO_8859_1;
        }
        String s = new String(d, from, to - from, cs);
        // Strip trailing NULs some writers leave behind
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == '\0') e--;
        return s.substring(0, e);
    }
}
