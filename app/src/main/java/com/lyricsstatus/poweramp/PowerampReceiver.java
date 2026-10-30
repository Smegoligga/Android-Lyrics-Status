package com.lyricsstatus.poweramp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives Poweramp's explicit TRACK_CHANGED / STATUS_CHANGED broadcasts (delivered even when this app is in the
 * background). The track bundle includes the file path, used to find .lrc files and embedded lyrics.
 */
public final class PowerampReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        App app = App.get(context);
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case PowerampApi.ACTION_TRACK_CHANGED_EXPLICIT:
            case PowerampApi.ACTION_TRACK_CHANGED:
                app.onPowerampTrack(intent.getBundleExtra(PowerampApi.EXTRA_TRACK), intent.getExtras());
                break;
            case PowerampApi.ACTION_STATUS_CHANGED_EXPLICIT:
            case PowerampApi.ACTION_STATUS_CHANGED:
                // Poweramp started playing: wake the media listener if it was asleep to save battery
                if (!intent.getBooleanExtra(PowerampApi.EXTRA_PAUSED, true)) app.wakeForPoweramp();
                app.onPowerampStatus(intent.getExtras());
                break;
            default:
                break;
        }
    }
}
