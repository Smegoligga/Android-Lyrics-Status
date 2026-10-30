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
 * The app ships its own built-in set (res/raw, made for this app). Imported files with the same names
 * override them and are copied into the app's private storage; "Use built-in sounds" deletes the imports.
 */
public final class Sfx {
    public static final String[] FILES = {"buttonrollover.wav", "buttonclick.wav", "buttonclickrelease.wav"};
    private static final int ROLLOVER = 0, CLICK = 1, RELEASE = 2;

    /** Built-in sounds, same order as FILES. */
    private static final int[] RAW = {R.raw.buttonrollover, R.raw.buttonclick, R.raw.buttonclickrelease};

    private final Context ctx;
    private final File dir;
    private SoundPool pool;
    private final int[] ids = {0, 0, 0};
    /** Per file: 0 = not loaded, 1 = ready, -1 = Android couldn't decode it. */
    private final int[] state = {0, 0, 0};
    private Runnable onStateChange;
    private boolean enabled;
    private float gain;
    private long lastRoll;

    public Sfx(Context c) {
        ctx = c.getApplicationContext();
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
        // USAGE_GAME plays on the media volume, like the site's Web Audio. (Sonification used the system/touch
        // volume, which many phones keep at 0 or mute in Do Not Disturb, so nothing was heard.)
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == sampleId) state[i] = status == 0 ? 1 : -1;
            }
            if (onStateChange != null) onStateChange.run();
        });
        for (int i = 0; i < FILES.length; i++) {
            File f = new File(dir, FILES[i]);
            ids[i] = f.isFile() ? pool.load(f.getPath(), 1) : pool.load(ctx, RAW[i], 1);
            state[i] = ids[i] == 0 ? -1 : 0;
        }
    }

    public void setVolume(int volume) {
        gain = Math.max(0, Math.min(100, volume)) / 100f * 0.6f;
    }

    public void release() {
        if (pool != null) pool.release();
        pool = null;
        ids[0] = ids[1] = ids[2] = 0;
        state[0] = state[1] = state[2] = 0;
    }

    /** How many of the three sounds have been replaced by imported files. */
    public int imported() {
        int n = 0;
        for (int i = 0; i < FILES.length; i++) if (isImported(i)) n++;
        return n;
    }

    public boolean isImported(int i) {
        return new File(dir, FILES[i]).isFile();
    }

    /** Delete imported files so the built-in sounds play again. */
    public void resetToBuiltIn() {
        for (String f : FILES) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, f).delete();
        }
    }

    /** Called (on the main thread) when a file finishes loading or fails to decode. */
    public void setOnStateChange(Runnable r) {
        onStateChange = r;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Status per file for the Options window, e.g. "buttonclick.wav: ready". */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FILES.length; i++) {
            String st = isImported(i) ? "imported" : "built-in";
            if (enabled) {
                st += state[i] == 1 ? ", ready" : state[i] == -1 ? ", can't be played (not a valid WAV?)" : ", loading…";
            }
            sb.append(i == 0 ? "" : "\n").append(FILES[i]).append(": ").append(st);
        }
        return sb.toString();
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
