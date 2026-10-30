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
            // Universal mode: if this app paused, another one may have started playing
            if (state != null && state.getState() != PlaybackState.STATE_PLAYING && !App.get().settings().onlyPoweramp) {
                refreshController();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            pushMetadata(metadata);
        }

        @Override
        public void onSessionDestroyed() {
            detach();
            App.get().engine().onStopped();
            App.get().onPlaybackActivity(false);
            refreshController();
        }
    };

    static boolean isConnected() {
        return current != null;
    }

    /**
     * Battery: drop the system's binding so Android can stop our process while nothing plays.
     * The listener stays enabled; {@link #wake} brings it back.
     */
    /** True while we unbound on purpose (idle sleep / disabled); otherwise a disconnect is Android's doing. */
    private static boolean sleeping;

    static void sleep() {
        MediaListenerService s = current;
        if (s == null) return;
        sleeping = true;
        Log.i(App.TAG, "sleeping until playback starts");
        try {
            s.requestUnbind();
        } catch (Exception e) {
            Log.w(App.TAG, "requestUnbind failed", e);
        }
    }

    /** Ask the system to bind the listener again (no-op without notification access). */
    static void wake(android.content.Context c) {
        sleeping = false;
        if (current != null) return;
        try {
            NotificationListenerService.requestRebind(new ComponentName(c, MediaListenerService.class));
        } catch (Exception e) {
            Log.w(App.TAG, "requestRebind failed", e);
        }
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
        if (controller == null) App.get().onPlaybackActivity(false);
    }

    @Override
    public void onListenerDisconnected() {
        if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        detach();
        if (current == this) current = null;
        App.get().mediaSessionActive = false;
        // Android sometimes drops the binding on its own; reconnect instead of waiting for the app to be opened
        if (!sleeping && App.get().settings().enabled) {
            try {
                requestRebind(new ComponentName(this, MediaListenerService.class));
            } catch (Exception e) {
                Log.w(App.TAG, "rebind failed", e);
            }
        }
    }

    private void onSessions(List<MediaController> sessions) {
        MediaController pick = choose(sessions);
        if (pick != null && controller != null && pick.getSessionToken().equals(controller.getSessionToken())) return;
        detach();
        if (pick == null) {
            App.get().mediaSessionActive = false;
            App.get().followedPackage = null;
            return;
        }
        controller = pick;
        App.get().followedPackage = pick.getPackageName();
        controller.registerCallback(callback, App.get().main);
        App.get().mediaSessionActive = true;
        pushMetadata(controller.getMetadata());
        pushState(controller.getPlaybackState());
    }

    /**
     * Universal mode: the app that is actually playing wins (Poweramp first if several are), otherwise
     * Poweramp, otherwise the most recent session. Poweramp-only mode: just Poweramp.
     */
    private MediaController choose(List<MediaController> sessions) {
        if (sessions == null || sessions.isEmpty()) return null;
        boolean onlyPoweramp = App.get().settings().onlyPoweramp;
        MediaController playing = null, poweramp = null, first = null;
        for (MediaController c : sessions) {
            boolean isPa = PowerampApi.PACKAGE.equals(c.getPackageName());
            if (onlyPoweramp && !isPa) continue;
            if (first == null) first = c;
            if (isPa && poweramp == null) poweramp = c;
            PlaybackState st = c.getPlaybackState();
            boolean isPlaying = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            if (isPlaying && (playing == null || isPa)) playing = c;
        }
        if (playing != null) return playing;
        if (poweramp != null) return poweramp;
        return first;
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
            App.get().onPlaybackActivity(false);
            return;
        }
        boolean playing = s == PlaybackState.STATE_PLAYING;
        App.get().onPlaybackActivity(playing);
        long at = st.getLastPositionUpdateTime();
        if (at <= 0) at = android.os.SystemClock.elapsedRealtime();
        App.get().engine().onPlayback(playing, st.getPosition(), at, playing ? st.getPlaybackSpeed() : 0f);
    }
}
