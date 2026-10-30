package com.lyricsstatus.poweramp.core;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discord custom-status calls, using the same endpoint and payload as StatusChanger.changeStatusRequest
 * in OvalQuilter's LyricsStatus (PATCH users/@me/settings with custom_status + expires_at).
 */
public final class DiscordClient {

    private static final String API = "https://discord.com/api/v9";
    private static final Pattern CUSTOM_EMOJI = Pattern.compile("<a?:([A-Za-z0-9_~]+):(\\d+)>");

    private final String token;

    public DiscordClient(String token) {
        this.token = token == null ? "" : token.trim().replaceAll("^\"|\"$", "");
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", token);
        h.put("Accept", "application/json");
        return h;
    }

    /** Build the custom_status object for a line. Emoji can be unicode or a Nitro emoji like <:name:123>. */
    public static JSONObject customStatus(String text, String emoji, int expirySeconds) throws Exception {
        JSONObject cs = new JSONObject();
        cs.put("text", text);
        Matcher m = CUSTOM_EMOJI.matcher(emoji == null ? "" : emoji.trim());
        if (m.matches()) {
            cs.put("emoji_id", m.group(2));
            cs.put("emoji_name", m.group(1));
        } else {
            cs.put("emoji_id", JSONObject.NULL);
            cs.put("emoji_name", emoji == null || emoji.trim().isEmpty() ? JSONObject.NULL : emoji.trim());
        }
        if (expirySeconds > 0) cs.put("expires_at", iso(System.currentTimeMillis() + expirySeconds * 1000L));
        else cs.put("expires_at", JSONObject.NULL);
        return cs;
    }

    static String iso(long ms) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(ms));
    }

    /** PATCH custom_status; pass null to clear it. */
    public Http.Response setCustomStatus(JSONObject customStatus) throws Exception {
        JSONObject body = new JSONObject();
        body.put("custom_status", customStatus == null ? JSONObject.NULL : customStatus);
        return Http.request("PATCH", API + "/users/@me/settings", headers(), body.toString(), 15000);
    }

    /** Current custom_status object, JSONObject.NULL if none, or null on failure. */
    public Object getCustomStatus() throws Exception {
        Http.Response r = Http.request("GET", API + "/users/@me/settings", headers(), null, 15000);
        if (!r.ok()) return null;
        JSONObject o = new JSONObject(r.body);
        return o.has("custom_status") ? o.get("custom_status") : JSONObject.NULL;
    }

    /** For the "Test token" button: returns the username or throws with the HTTP code. */
    public String whoAmI() throws Exception {
        Http.Response r = Http.request("GET", API + "/users/@me", headers(), null, 15000);
        if (r.code == 401) throw new Exception("Invalid token (401)");
        if (!r.ok()) throw new Exception("HTTP " + r.code);
        JSONObject o = new JSONObject(r.body);
        String global = o.optString("global_name", "");
        return (global.isEmpty() || global.equals("null")) ? o.optString("username") : global + " (@" + o.optString("username") + ")";
    }

    /** Seconds to wait from a 429 body, default 2 s. */
    public static double retryAfter(Http.Response r) {
        try {
            return Math.max(0.2, new JSONObject(r.body).optDouble("retry_after", 2.0));
        } catch (Exception e) {
            return 2.0;
        }
    }
}
