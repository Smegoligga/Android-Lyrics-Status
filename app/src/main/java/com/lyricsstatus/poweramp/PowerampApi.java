package com.lyricsstatus.poweramp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.lyricsstatus.poweramp.core.Track;

/**
 * The few Poweramp API constants this app needs, from Poweramp's public API
 * (https://github.com/maxmpz/powerampapi, PowerampAPI.java), copied as strings so no library is required.
 */
public final class PowerampApi {
    public static final String PACKAGE = "com.maxmpz.audioplayer";
    public static final String API_RECEIVER = "com.maxmpz.audioplayer.player.PowerampAPIReceiver";

    public static final String ACTION_TRACK_CHANGED = "com.maxmpz.audioplayer.TRACK_CHANGED";
    public static final String ACTION_TRACK_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.TRACK_CHANGED_EXPLICIT";
    public static final String ACTION_STATUS_CHANGED = "com.maxmpz.audioplayer.STATUS_CHANGED";
    public static final String ACTION_STATUS_CHANGED_EXPLICIT = "com.maxmpz.audioplayer.STATUS_CHANGED_EXPLICIT";

    public static final String ACTION_NEED_LYRICS = "com.maxmpz.audioplayer.ACTION_NEED_LYRICS";
    public static final String ACTION_UPDATE_LYRICS = "com.maxmpz.audioplayer.ACTION_UPDATE_LYRICS";
    public static final String ACTION_LYRICS_LINK = "com.maxmpz.audioplayer.ACTION_LYRICS_LINK";

    public static final String EXTRA_TRACK = "track";
    public static final String EXTRA_PAUSED = "paused";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_POS = "pos";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_PACKAGE = "pak";
    public static final String EXTRA_LYRICS = "lyrics";
    public static final String EXTRA_INFO_LINE = "info_line";

    public static final int STATE_STOPPED = 0;

    // Track bundle keys (PowerampAPI.Track)
    static final String T_REAL_ID = "realId";
    static final String T_PATH = "path";
    static final String T_TITLE = "title";
    static final String T_ALBUM = "album";
    static final String T_ARTIST = "artist";
    static final String T_DURATION = "dur";
    static final String T_DURATION_MS = "durMs";
    static final String T_FILE_TYPE = "fileType";
    static final int FILE_TYPE_STREAM = 23;

    private PowerampApi() {}

    /** Track from a Poweramp track bundle or ACTION_NEED_LYRICS extras. */
    static Track trackFrom(Bundle b) {
        if (b == null) return null;
        String title = b.getString(T_TITLE);
        if (title == null || title.isEmpty()) return null;
        long dur = b.getInt(T_DURATION_MS, 0);
        if (dur <= 0) dur = b.getInt(T_DURATION, 0) * 1000L;
        boolean stream = b.getInt(T_FILE_TYPE, -1) == FILE_TYPE_STREAM;
        return new Track(title, b.getString(T_ARTIST), b.getString(T_ALBUM), dur,
                stream ? null : b.getString(T_PATH), b.getLong(T_REAL_ID, 0));
    }

    /** Hand lyrics (LRC or plain) back to Poweramp; null lyrics tells it the lookup failed. */
    static void sendLyrics(Context c, long realId, String lyrics, String infoLine) {
        Intent i = new Intent(ACTION_UPDATE_LYRICS);
        i.setComponent(new ComponentName(PACKAGE, API_RECEIVER));
        i.putExtra(EXTRA_ID, realId);
        i.putExtra(EXTRA_LYRICS, lyrics);
        i.putExtra(EXTRA_INFO_LINE, infoLine);
        i.putExtra(EXTRA_PACKAGE, c.getPackageName());
        c.sendBroadcast(i);
    }
}
