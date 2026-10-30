package com.lyricsstatus.poweramp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.lyricsstatus.poweramp.core.Settings;
import com.lyricsstatus.poweramp.core.Track;

/**
 * Poweramp lyrics-plugin hook: when Poweramp has no lyrics for a track it asks installed plugins.
 * We answer with LRCLIB results so Poweramp's own lyrics screen shows them too (same cache as the status).
 */
public final class LyricsRequestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PowerampApi.ACTION_NEED_LYRICS.equals(intent.getAction()) || intent.getExtras() == null) return;
        App app = App.get(context);
        Settings s = app.settings();
        Track t = PowerampApi.trackFrom(intent.getExtras());
        if (t == null || t.realId == 0) return;
        final Context appCtx = context.getApplicationContext();
        if (!s.providePowerampLyrics || !s.useLrclib) {
            PowerampApi.sendLyrics(appCtx, t.realId, null, null);
            return;
        }
        // Not goAsync(): several LRCLIB requests can exceed its time budget. The notification listener keeps
        // the process alive, and Poweramp accepts ACTION_UPDATE_LYRICS whenever it arrives.
        app.io.execute(() -> {
            try {
                String lyrics = app.repository().fetchForPoweramp(t, s);
                PowerampApi.sendLyrics(appCtx, t.realId, lyrics, lyrics != null ? "Lyrics from LRCLIB · Android Lyrics Status" : null);
            } catch (Throwable e) {
                Log.w(App.TAG, "Poweramp lyrics request failed", e);
            }
        });
    }
}
