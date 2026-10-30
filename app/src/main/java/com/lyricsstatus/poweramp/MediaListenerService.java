package com.lyricsstatus.poweramp;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import com.lyricsstatus.poweramp.core.Track;

import java.util.List;

/**
 * Notification access is the only way for an app to read other players' media sessions, which carry the
 * playback position in milliseconds (Poweramp's own broadcasts only give whole seconds).
 * The system keeps this service bound, which also keeps the app process alive in the background.
 * No notifications are read.
 */
public final class MediaListenerService extends NotificationListenerService {

    private static MediaListenerService current;

    private MediaSessionManager msm;
    private MediaController controller;
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::onSessions;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            pushState(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            pushMetadata(metadata);
        }

        @Override
        public void onSessionDestroyed() {
            detach();
            App.get().engine().onStopped();
            refreshController();
        }
    };

    static boolean isConnected() {
        return current != null;
    }

    /** Re-pick the followed session (e.g. "only Poweramp" toggled). */
    static void refreshController() {
        MediaListenerService s = current;
        if (s != null && s.msm != null) {
            try {
                s.onSessions(s.msm.getActiveSessions(new ComponentName(s, MediaListenerService.class)));
            } catch (SecurityException e) {
                Log.w(App.TAG, "lost notification access", e);
            }
        }
    }

    @Override
    public void onListenerConnected() {
        current = this;
        msm = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaListenerService.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, App.get().main);
            onSessions(msm.getActiveSessions(me));
        } catch (SecurityException e) {
            Log.w(App.TAG, "no notification access", e);
        }
        App.get().primeFromPoweramp();
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        detach();
        if (current == this) current = null;
        App.get().mediaSessionActive = false;
    }

    private void onSessions(List<MediaController> sessions) {
        MediaController pick = choose(sessions);
        if (pick != null && controller != null && pick.getSessionToken().equals(controller.getSessionToken())) return;
        detach();
        if (pick == null) {
            App.get().mediaSessionActive = false;
            return;
        }
        controller = pick;
        controller.registerCallback(callback, App.get().main);
        App.get().mediaSessionActive = true;
        pushMetadata(controller.getMetadata());
        pushState(controller.getPlaybackState());
    }

    private MediaController choose(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        boolean onlyPoweramp = App.get().settings().onlyPoweramp;
        MediaController fallback = null;
        for (MediaController c : sessions) {
            boolean isPa = PowerampApi.PACKAGE.equals(c.getPackageName());
            if (onlyPoweramp && !isPa) continue;
            if (isPa) return c;
            PlaybackState st = c.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) return c;
            if (fallback == null) fallback = c;
        }
        return fallback;
    }

    private void detach() {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
            controller = null;
        }
    }

    private void pushMetadata(MediaMetadata m) {
        if (m == null) return;
        String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty()) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        String album = m.getString(MediaMetadata.METADATA_KEY_ALBUM);
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (title == null || title.isEmpty()) return;
        App.get().engine().onTrack(new Track(title, artist, album, dur, null, 0));
    }

    private void pushState(PlaybackState st) {
        if (st == null) return;
        int s = st.getState();
        if (s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_ERROR) {
            App.get().engine().onStopped();
            return;
        }
        boolean playing = s == PlaybackState.STATE_PLAYING;
        long at = st.getLastPositionUpdateTime();
        if (at <= 0) at = android.os.SystemClock.elapsedRealtime();
        App.get().engine().onPlayback(playing, st.getPosition(), at, playing ? st.getPlaybackSpeed() : 0f);
    }
}
