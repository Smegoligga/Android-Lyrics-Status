package com.lyricsstatus.poweramp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Keeps lyric updates running while music plays.
 *
 * Without it Android can freeze the app in the background, and on phones that play audio with the main CPU
 * asleep the line timer stops, so the status froze until the app was opened again. While music is playing
 * this runs as a foreground service (a quiet "Lyrics status" notification) and holds a partial wake lock.
 * Both are released after playback stops.
 */
public final class KeepAliveService extends Service {

    private static final String CHANNEL = "lyrics_status";
    private static final int NOTIFICATION_ID = 1;
    /** Safety net: the wake lock is renewed every time playback state changes, but never held forever. */
    private static final long WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1000L;

    private static PowerManager.WakeLock wakeLock;
    private static boolean running;

    /**
     * Start keeping lyric updates alive. Safe to call often.
     * @param withNotification also run as a foreground service (shows a notification, most reliable)
     */
    static void start(Context c, boolean withNotification) {
        acquireWakeLock(c);
        if (!withNotification) {
            stopService(c);
            return;
        }
        if (running) return;
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            c.startForegroundService(i);
            running = true;
        } catch (Exception e) {
            // Android 12+ may refuse to start a foreground service from the background unless battery
            // optimisation is off for the app. The wake lock alone still keeps the timer going.
            Log.w(App.TAG, "couldn't start keep-alive service", e);
        }
    }

    static void stop(Context c) {
        releaseWakeLock();
        stopService(c);
    }

    private static void stopService(Context c) {
        if (!running) return;
        running = false;
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
        } catch (Exception ignored) {
        }
    }

    private static void acquireWakeLock(Context c) {
        if (wakeLock == null) {
            PowerManager pm = c.getSystemService(PowerManager.class);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LyricsStatus:playing");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
    }

    private static void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            Log.w(App.TAG, "startForeground failed", e);
            running = false;
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Lyrics status", NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("Shown while your Discord status is following the music");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_lyrics)
                .setContentTitle("Lyrics status is on")
                .setContentText("Updating your Discord status while music plays")
                .setContentIntent(open)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }
}
