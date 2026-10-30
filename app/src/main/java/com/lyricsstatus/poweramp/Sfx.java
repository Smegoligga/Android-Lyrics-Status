package com.lyricsstatus.poweramp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Menu sounds for the CS 1.6 theme. Port of smegolas.com's sfx.js:
 *  - buttonrollover.wav    on hover (stylus/mouse), at most once per 40 ms
 *  - buttonclick.wav       on press (pointerdown)
 *  - buttonclickrelease.wav on click (release)
 *  - gain = volume / 100 * 0.6 (the game files are loud), volume default 70, off by default
 *
 * The .wav files are Valve's, so they aren't bundled: you import them once and they're copied into
 * the app's private storage. Missing files are simply skipped, like on the site.
 */
public final class Sfx {
    public static final String[] FILES = {"buttonrollover.wav", "buttonclick.wav", "buttonclickrelease.wav"};
    private static final int ROLLOVER = 0, CLICK = 1, RELEASE = 2;

    private final File dir;
    private SoundPool pool;
    private final int[] ids = {0, 0, 0};
    private boolean enabled;
    private float gain;
    private long lastRoll;

    public Sfx(Context c) {
        dir = new File(c.getFilesDir(), "sounds/cs16");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Load (or reload) the sound files; call when the theme or files change. */
    public void load(boolean enabled, int volume) {
        release();
        this.enabled = enabled;
        setVolume(volume);
        if (!enabled) return;
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (int i = 0; i < FILES.length; i++) {
            File f = new File(dir, FILES[i]);
            ids[i] = f.isFile() ? pool.load(f.getPath(), 1) : 0;
        }
    }

    public void setVolume(int volume) {
        gain = Math.max(0, Math.min(100, volume)) / 100f * 0.6f;
    }

    public void release() {
        if (pool != null) pool.release();
        pool = null;
        ids[0] = ids[1] = ids[2] = 0;
    }

    /** How many of the three files are present. */
    public int available() {
        int n = 0;
        for (String f : FILES) if (new File(dir, f).isFile()) n++;
        return n;
    }

    public boolean has(int i) {
        return new File(dir, FILES[i]).isFile();
    }

    private void play(int i) {
        if (!enabled || pool == null || ids[i] == 0) return;
        pool.play(ids[i], gain, gain, 1, 0, 1f);
    }

    public void playRelease() {
        play(RELEASE);
    }

    /** Hook a clickable view up like sfx.wire() does on the site. */
    @SuppressLint("ClickableViewAccessibility")
    public void wire(View v) {
        v.setOnTouchListener((view, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) play(CLICK);
            else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                // release only for a real click (finger still on the control)
                if (e.getX() >= 0 && e.getY() >= 0 && e.getX() <= view.getWidth() && e.getY() <= view.getHeight()) {
                    play(RELEASE);
                }
            }
            return false; // let the view handle the click normally
        });
        v.setOnHoverListener((view, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
                long now = SystemClock.uptimeMillis();
                if (now - lastRoll >= 40) {
                    lastRoll = now;
                    play(ROLLOVER);
                }
            }
            return false;
        });
    }

    /**
     * Copy picked files into place, matched by file name (buttonclickrelease / buttonrollover / buttonclick).
     * @return number of files imported
     */
    public int importFrom(Context c, Iterable<Uri> uris) {
        int n = 0;
        for (Uri u : uris) {
            String name = displayName(c, u).toLowerCase(Locale.ROOT);
            int slot = name.contains("buttonclickrelease") ? RELEASE
                    : name.contains("buttonrollover") ? ROLLOVER
                    : name.contains("buttonclick") ? CLICK : -1;
            if (slot < 0) continue;
            File out = new File(dir, FILES[slot]);
            try (InputStream in = c.getContentResolver().openInputStream(u);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) continue;
                byte[] buf = new byte[16384];
                int r;
                long total = 0;
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > 5L * 1024 * 1024) throw new java.io.IOException("too large for a menu sound");
                    os.write(buf, 0, r);
                }
                n++;
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
        return n;
    }

    private static String displayName(Context c, Uri u) {
        try (Cursor cur = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String s = cur.getString(0);
                if (s != null) return s;
            }
        } catch (Exception ignored) {
        }
        String p = u.getLastPathSegment();
        return p == null ? "" : p;
    }
}
