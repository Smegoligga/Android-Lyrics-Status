package com.lyricsstatus.poweramp.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genius lyrics: search and read a song page. Genius only has plain (unsynced) lyrics, so they can't move
 * the status line by line, but they're there when LRCLIB has nothing and they show inside Poweramp.
 * Same parsing as LRCSSync's genius.js.
 */
public final class GeniusClient {

    /** Genius blocks requests that don't look like a browser. */
    static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/130.0 Mobile Safari/537.36");
        HEADERS.put("Accept-Language", "en-US,en;q=0.9");
    }

    /** One song found on Genius (its lyrics are read separately, from {@link #url}). */
    public static final class Hit {
        public final long id;
        public final String title, artist, url;
        public final int year;

        Hit(long id, String title, String artist, String url, int year) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.url = url;
            this.year = year;
        }
    }

    /** Genius answered with a "are you human" check instead of the page. */
    public static final class BotCheck extends Exception {
        public final String url;

        BotCheck(String url) {
            super("Genius wants a quick check that you're human. Open it in your browser, or try again later "
                    + "(turning off a VPN usually helps).");
            this.url = url;
        }
    }

    /** How pages are fetched; swapped out in tests. Returns {code, body}. */
    interface Fetcher {
        Object[] get(String url) throws Exception;
    }

    static Fetcher fetcher = url -> {
        Http.Response r = Http.getRetry(url, HEADERS, 15000);
        return new Object[]{r.code, r.body};
    };

    private GeniusClient() {}

    public static List<Hit> search(String query) throws Exception {
        String q = query == null ? "" : query.trim();
        List<Hit> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        String url = "https://genius.com/api/search/multi?per_page=5&q=" + Http.enc(q);
        Object[] r = fetcher.get(url);
        int code = (int) r[0];
        String body = (String) r[1];
        if (code != 200 || isBotCheck(code, body)) throw new BotCheck("https://genius.com/search?q=" + Http.enc(q));
        JSONArray sections = new JSONObject(body).optJSONObject("response") == null ? new JSONArray()
                : new JSONObject(body).getJSONObject("response").optJSONArray("sections");
        if (sections == null) return out;
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < sections.length(); i++) {
            JSONArray hits = sections.getJSONObject(i).optJSONArray("hits");
            if (hits == null) continue;
            for (int j = 0; j < hits.length(); j++) {
                JSONObject h = hits.getJSONObject(j);
                JSONObject s = h.optJSONObject("result");
                if (!"song".equals(h.optString("type")) || s == null || !seen.add(s.optLong("id"))) continue;
                JSONObject pa = s.optJSONObject("primary_artist");
                String artist = pa != null ? pa.optString("name", "") : s.optString("artist_names", "");
                JSONObject rd = s.optJSONObject("release_date_components");
                int year = rd != null ? rd.optInt("year", 0) : 0;
                out.add(new Hit(s.optLong("id"), s.optString("title", ""), artist, s.optString("url", ""), year));
            }
        }
        return out;
    }

    /** Plain lyrics of a Genius song page, section headers like [Chorus] removed. */
    public static String lyrics(String url) throws Exception {
        if (url == null || !url.startsWith("https://genius.com/")) throw new Exception("Not a Genius link");
        Object[] r = fetcher.get(url);
        int code = (int) r[0];
        String html = (String) r[1];
        String text = parseHtml(html);
        if (text != null && !clean(text).isEmpty()) return clean(text);
        if (isBotCheck(code, html) || code != 200) throw new BotCheck(url);
        throw new Exception("This Genius page has no lyrics");
    }

    // ------------------------------------------------------------------ parsing (pure, tested)

    static boolean isBotCheck(int code, String html) {
        return code == 403 || code == 429 || code == 503
                || (html != null && BOT.matcher(html).find());
    }

    private static final Pattern BOT = Pattern.compile("cf-chl|challenge-platform|Just a moment|cf-turnstile|captcha",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTAINER = Pattern.compile("<div\\b[^>]*data-lyrics-container=\"true\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXCLUDED = Pattern.compile("<div\\b[^>]*data-exclude-from-selection=\"true\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIV = Pattern.compile("</?div\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    /** {inner html, index just after the closing tag} of the div opened at {@code start}. */
    private static Object[] innerOf(String html, int start) {
        int tagEnd = html.indexOf('>', start);
        int depth = 1;
        Matcher m = DIV.matcher(html);
        m.region(tagEnd + 1, html.length());
        while (m.find()) {
            String t = m.group();
            if (t.charAt(1) == '/') depth--;
            else if (!t.endsWith("/>")) depth++;
            if (depth == 0) return new Object[]{html.substring(tagEnd + 1, m.start()), m.end()};
        }
        return new Object[]{html.substring(tagEnd + 1), html.length()};
    }

    private static String removeExcluded(String html) {
        String out = html;
        Matcher m;
        while ((m = EXCLUDED.matcher(out)).find()) {
            int end = (int) innerOf(out, m.start())[1];
            out = out.substring(0, m.start()) + out.substring(end);
        }
        return out;
    }

    /** Lyrics text from a Genius song page, or null when the page has none (e.g. a bot check). */
    static String parseHtml(String html) {
        if (html == null) return null;
        List<String> parts = new ArrayList<>();
        Matcher m = CONTAINER.matcher(html);
        int from = 0;
        while (from < html.length() && m.find(from)) {
            Object[] io = innerOf(html, m.start());
            String inner = removeExcluded((String) io[0]);
            parts.add(decode(inner.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")));
            from = (int) io[1];
        }
        if (parts.isEmpty()) return null;
        return String.join("\n", parts).replaceAll("\n{3,}", "\n\n").trim();
    }

    /** Drops [Verse 1: X] / [Chorus] headers and blank runs; keeps one empty line between sections. */
    static String clean(String text) {
        StringBuilder sb = new StringBuilder();
        boolean blank = false;
        for (String line : text.split("\n", -1)) {
            String t = line.trim();
            if (t.matches("\\[[^\\]]{1,80}]")) {
                blank = sb.length() > 0;
                continue;
            }
            if (t.isEmpty()) {
                blank = sb.length() > 0;
                continue;
            }
            if (blank) sb.append('\n');
            sb.append(t).append('\n');
            blank = false;
        }
        return sb.toString().trim();
    }

    static String decode(String s) {
        Matcher hex = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (hex.find()) hex.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(hex.group(1), 16)))));
        hex.appendTail(sb);
        Matcher dec = Pattern.compile("&#(\\d+);").matcher(sb.toString());
        StringBuffer sb2 = new StringBuffer();
        while (dec.find()) dec.appendReplacement(sb2, Matcher.quoteReplacement(new String(Character.toChars(Integer.parseInt(dec.group(1))))));
        dec.appendTail(sb2);
        return sb2.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
