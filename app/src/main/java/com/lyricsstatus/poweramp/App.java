package com.lyricsstatus.poweramp;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import com.lyricsstatus.poweramp.core.Engine;
import com.lyricsstatus.poweramp.core.LocalLyricsFinder;
import com.lyricsstatus.poweramp.core.LyricsCache;
import com.lyricsstatus.poweramp.core.LyricsRepository;
import com.lyricsstatus.poweramp.core.Settings;
import com.lyricsstatus.poweramp.core.StatusSender;
import com.lyricsstatus.poweramp.core.Track;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process-wide singletons. The engine lives here so it keeps running while the app's
 * notification listener keeps the process alive (no foreground notification needed).
 */
public final class App extends Application {

    static final String TAG = "LyricsStatus";

    private static App instance;

    public final Handler main = new Handler(Looper.getMainLooper());
    public final ExecutorService io = Executors.newFixedThreadPool(2);

    private Prefs prefs;
    private Settings settings;
    private LyricsRepository repository;
    private StatusSender sender;
    private Engine engine;

    /** True while a media session from the followed player feeds us exact positions. */
    volatile boolean mediaSessionActive;
    /** Package of the player whose media session we follow, or null. */
    volatile String followedPackage;

    /** How long playback must be idle before the listener sleeps (process can then be stopped). */
    static final long SLEEP_AFTER_IDLE_MS = 10 * 60 * 1000L;
    private final Runnable sleepRunnable = MediaListenerService::sleep;
    private boolean sleepScheduled;

    public static App get() {
        return instance;
    }

    public static App get(Context c) {
        return (App) c.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        settings = prefs.load();
        repository = new LyricsRepository(new LyricsCache(new File(getFilesDir(), "lyrics-cache")));

        sender = new StatusSender(prefs, new StatusSender.Listener() {
            @Override
            public void onSent(String text, long roundTripMs) {
                main.post(() -> engine.onSent(text, roundTripMs));
            }

            @Override
            public void onIdle(String what) {
                main.post(() -> engine.onSenderIdle(what));
            }

            @Override
            public void onError(String message, boolean fatal) {
                Log.w(TAG, message);
                main.post(() -> engine.onSenderError(message, fatal));
            }
        });

        Engine.Scheduler scheduler = new Engine.Scheduler() {
            @Override
            public long now() {
                return SystemClock.elapsedRealtime();
            }

            @Override
            public void postDelayed(Runnable r, long delayMs) {
                main.postDelayed(r, delayMs);
            }

            @Override
            public void cancel(Runnable r) {
                main.removeCallbacks(r);
            }
        };

        Engine.LyricsLoader loader = (track, s, done) -> io.execute(() -> {
            LyricsRepository.Result r;
            try {
                r = repository.find(track, resolveAudioFile(track), s);
            } catch (Throwable t) {
                Log.e(TAG, "lyrics lookup failed", t);
                r = repository.find(track, null, s);
            }
            final LyricsRepository.Result result = r;
            Log.i(TAG, track + " -> " + result.info);
            main.post(() -> done.onLoaded(result));
        });

        Engine.Sink sink = new Engine.Sink() {
            @Override
            public void sendLine(String text, Settings s) {
                sender.sendLine(text, s);
            }

            @Override
            public void sendText(String text, String emoji, Settings s) {
                sender.sendText(text, emoji, s);
            }

            @Override
            public void idle(Settings s) {
                sender.idle(s);
            }
        };

        engine = new Engine(scheduler, loader, sink, settings);
        primeFromPoweramp();
    }

    public Settings settings() {
        return settings.copy();
    }

    /** Persist and apply new settings (main thread). */
    public void updateSettings(Settings s) {
        settings = s.copy();
        prefs.save(settings);
        engine.setSettings(settings);
        MediaListenerService.refreshController();
        if (!settings.enabled) {
            cancelSleep();
            MediaListenerService.sleep();
        } else {
            MediaListenerService.wake(this);
            onPlaybackActivity(engine.state().playing);
        }
    }

    /** Theme is UI-only: save it without touching the engine (no status re-send). */
    public void setTheme(String id) {
        settings.theme = id;
        prefs.save(settings);
    }

    // ------------------------------------------------------------------ battery: sleep while idle

    /**
     * Called on every playback change. While nothing plays, a 10-minute timer releases the notification
     * listener so Android may stop the process entirely; Poweramp's broadcast wakes us when it plays again.
     * Only possible when following Poweramp only, since other players can't wake us.
     */
    void onPlaybackActivity(boolean playing) {
        if (playing || !canSleep()) {
            cancelSleep();
        } else if (!sleepScheduled) {
            sleepScheduled = true;
            main.postDelayed(sleepRunnable, settings.enabled ? SLEEP_AFTER_IDLE_MS : 0);
        }
    }

    private void cancelSleep() {
        if (sleepScheduled) {
            main.removeCallbacks(sleepRunnable);
            sleepScheduled = false;
        }
    }

    private boolean canSleep() {
        return settings.onlyPoweramp || !settings.enabled;
    }

    /** Wake the listener when Poweramp does something (it may have been put to sleep). */
    void wakeForPoweramp() {
        if (settings.enabled) MediaListenerService.wake(this);
    }

    public Engine engine() {
        return engine;
    }

    public LyricsRepository repository() {
        return repository;
    }

    // ------------------------------------------------------------------ Poweramp input

    /** Poweramp's TRACK_CHANGED: carries the file path, which the media session doesn't. */
    void onPowerampTrack(Bundle b, Bundle extras) {
        onPowerampTrack(b, extras, true);
    }

    void onPowerampTrack(Bundle b, Bundle extras, boolean wake) {
        if (b == null) return;
        // Universal mode: ignore Poweramp while we're following another app that is playing
        if (mediaSessionActive && followedPackage != null && !PowerampApi.PACKAGE.equals(followedPackage)) return;
        if (wake) wakeForPoweramp();
        Track t = PowerampApi.trackFrom(b);
        if (t == null) return;
        engine.onTrack(t);
        // Without media-session access we only get second-precision positions from Poweramp
        if (!mediaSessionActive && extras != null && extras.containsKey(PowerampApi.EXTRA_PAUSED)) {
            onPowerampStatus(extras);
        }
    }

    void onPowerampStatus(Bundle extras) {
        if (mediaSessionActive || extras == null) return;
        boolean paused = extras.getBoolean(PowerampApi.EXTRA_PAUSED, true);
        int state = extras.getInt(PowerampApi.EXTRA_STATE, -1);
        onPlaybackActivity(!paused && state != PowerampApi.STATE_STOPPED);
        if (state == PowerampApi.STATE_STOPPED) {
            engine.onStopped();
            return;
        }
        if (extras.containsKey(PowerampApi.EXTRA_POS)) {
            long pos = extras.getInt(PowerampApi.EXTRA_POS, 0) * 1000L;
            engine.onPlayback(!paused, pos, SystemClock.elapsedRealtime(), 1f);
        } else {
            engine.onPlayback(!paused, engine.position(), SystemClock.elapsedRealtime(), 1f);
        }
    }

    /** Pick up whatever Poweramp is playing right now from its sticky broadcasts. */
    void primeFromPoweramp() {
        try {
            Intent track = registerSticky(PowerampApi.ACTION_TRACK_CHANGED);
            if (track != null) onPowerampTrack(track.getBundleExtra(PowerampApi.EXTRA_TRACK), null, false);
            Intent status = registerSticky(PowerampApi.ACTION_STATUS_CHANGED);
            if (status != null && status.getExtras() != null) onPowerampStatus(status.getExtras());
        } catch (Throwable t) {
            Log.w(TAG, "sticky query failed", t);
        }
    }

    private Intent registerSticky(String action) {
        IntentFilter f = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) return registerReceiver(null, f, Context.RECEIVER_EXPORTED);
        return registerReceiver(null, f);
    }

    // ------------------------------------------------------------------ files

    public boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** The playing file, from Poweramp's path or, failing that, a MediaStore lookup by title/artist. */
    File resolveAudioFile(Track t) {
        File f = LocalLyricsFinder.resolve(t.path);
        if (f != null && f.canRead()) return f;
        File m = mediaStoreLookup(t);
        if (m != null) return m;
        return f != null && f.exists() ? f : null;
    }

    private File mediaStoreLookup(Track t) {
        if (t.title.isEmpty()) return null;
        boolean canQuery = Build.VERSION.SDK_INT >= 33
                ? checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED || hasFileAccess()
                : hasFileAccess();
        if (!canQuery) return null;
        String[] proj = {MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST};
        String sel = MediaStore.Audio.Media.TITLE + "=?";
        File best = null;
        long bestScore = Long.MAX_VALUE;
        try (Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel,
                new String[]{t.title}, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                String data = c.getString(0);
                if (data == null) continue;
                long dur = c.getLong(1);
                String artist = c.getString(2);
                long score = t.durationMs > 0 ? Math.abs(dur - t.durationMs) : 0;
                if (artist == null || !Track.norm(artist).equals(Track.norm(t.artist))) score += 10_000;
                if (score < bestScore) {
                    File f = new File(data);
                    if (f.canRead()) {
                        best = f;
                        bestScore = score;
                    }
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "MediaStore lookup failed", e);
        }
        return bestScore <= 15_000 ? best : null;
    }
}
