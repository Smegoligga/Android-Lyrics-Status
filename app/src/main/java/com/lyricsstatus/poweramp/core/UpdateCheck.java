package com.lyricsstatus.poweramp.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;

/** Looks at the newest GitHub release and says whether it's newer than the installed app. */
public final class UpdateCheck {

    public static final String REPO = "Smegoligga/LRCStatus";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String RELEASES_URL = REPO_URL + "/releases";

    /** How often to look on launch: every launch, once a day, once a week, never (only "Check now"). */
    public static final int EVERY_LAUNCH = 0, DAILY = 1, WEEKLY = 2, NEVER = 3;

    public static final class Release {
        public final String version, pageUrl, apkUrl, notes;

        Release(String version, String pageUrl, String apkUrl, String notes) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }

    private UpdateCheck() {}

    /** True when a check is due for this frequency, given the last check time (ms). */
    public static boolean due(int frequency, long lastCheckMs, long nowMs) {
        switch (frequency) {
            case EVERY_LAUNCH:
                return true;
            case DAILY:
                return nowMs - lastCheckMs >= 20 * 3600_000L;   // a little under a day, so a daily habit still checks
            case WEEKLY:
                return nowMs - lastCheckMs >= 6 * 24 * 3600_000L + 20 * 3600_000L;
            default:
                return false;
        }
    }

    /** Newest published release (pre-releases included, drafts skipped), or null if GitHub has none. Blocking. */
    public static Release latest() throws Exception {
        Http.Response r = Http.getRetry("https://api.github.com/repos/" + REPO + "/releases?per_page=5",
                Collections.singletonMap("Accept", "application/vnd.github+json"), 15000);
        if (!r.ok()) throw new Exception("GitHub answered HTTP " + r.code);
        return pick(new JSONArray(r.body));
    }

    static Release pick(JSONArray releases) {
        Release best = null;
        for (int i = 0; i < releases.length(); i++) {
            JSONObject o = releases.optJSONObject(i);
            if (o == null || o.optBoolean("draft")) continue;
            String v = o.optString("tag_name", "").replaceFirst("^[vV]", "");
            if (v.isEmpty()) continue;
            String apk = null;
            JSONArray assets = o.optJSONArray("assets");
            if (assets != null) {
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject a = assets.optJSONObject(j);
                    if (a != null && a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url");
                }
            }
            Release rel = new Release(v, o.optString("html_url", RELEASES_URL), apk, o.optString("body", ""));
            if (best == null || compare(rel.version, best.version) > 0) best = rel;
        }
        return best;
    }

    /** Compares dotted versions numerically: 0.10 > 0.9.2 > 0.9. */
    public static int compare(String a, String b) {
        String[] x = a.split("[^0-9]+"), y = b.split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    public static boolean isNewer(Release r, String installed) {
        return r != null && compare(r.version, installed) > 0;
    }
}
