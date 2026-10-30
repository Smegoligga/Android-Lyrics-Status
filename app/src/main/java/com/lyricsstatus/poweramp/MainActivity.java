package com.lyricsstatus.poweramp;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.lyricsstatus.poweramp.core.DiscordClient;
import com.lyricsstatus.poweramp.core.Engine;
import com.lyricsstatus.poweramp.core.Formatter;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Settings + live status. Built in code with platform widgets so the app has no dependencies. */
public final class MainActivity extends Activity {

    private App app;
    private LinearLayout root;
    private TextView nowPlaying, lyricsInfo, currentLine, discordInfo, errorView;
    private TextView notifState, fileState, batteryState, tokenState;
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshLive();
            app.main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.get(this);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, dp(32));
        sv.addView(root);
        setContentView(sv);
        if (Build.VERSION.SDK_INT >= 35) {
            sv.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return insets;
            });
        }
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (app.settings().enabled) MediaListenerService.wake(this);
        refreshSetup();
        app.main.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        app.main.removeCallbacks(refresher);
    }

    // ------------------------------------------------------------------ layout

    private void build() {
        TextView title = text("LyricsStatus for Poweramp", 22, true);
        root.addView(title);
        TextView sub = text("Your Discord status follows the lyrics of what Poweramp is playing.", 14, false);
        sub.setAlpha(0.75f);
        root.addView(sub);

        // live card
        LinearLayout live = card();
        nowPlaying = text("", 16, true);
        lyricsInfo = text("", 13, false);
        currentLine = text("", 15, false);
        currentLine.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
        discordInfo = text("", 13, false);
        errorView = text("", 13, true);
        errorView.setTextColor(0xFFE5484D);
        live.addView(nowPlaying);
        live.addView(lyricsInfo);
        live.addView(currentLine);
        live.addView(discordInfo);
        live.addView(errorView);
        LinearLayout liveBtns = row();
        liveBtns.addView(button("Find lyrics again", v -> {
            app.engine().reload();
            toast("Looking up lyrics again");
        }));
        liveBtns.addView(button("Clear cache", v -> {
            int n = app.repository().cache().clear();
            app.engine().reload();
            toast("Removed " + n + " cached lyrics");
        }));
        live.addView(liveBtns);
        root.addView(live);

        // setup
        section("Setup");
        LinearLayout setup = card();
        com.lyricsstatus.poweramp.core.Settings s = app.settings();

        tokenState = label("Discord token");
        setup.addView(tokenState);
        EditText token = new EditText(this);
        token.setHint("Paste your Discord token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTransformationMethod(PasswordTransformationMethod.getInstance());
        token.setText(s.token);
        setup.addView(token);
        LinearLayout tokenBtns = row();
        tokenBtns.addView(button("Save", v -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.token = token.getText().toString().trim().replaceAll("^\"|\"$", "");
            app.updateSettings(ns);
            refreshSetup();
            toast("Token saved");
        }));
        tokenBtns.addView(button("Test", v -> testToken(token.getText().toString())));
        tokenBtns.addView(button("Show", v -> {
            boolean hidden = token.getTransformationMethod() != null;
            token.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance());
            ((Button) v).setText(hidden ? "Hide" : "Show");
        }));
        setup.addView(tokenBtns);
        setup.addView(hint("Same token LyricsStatus used. Easiest to copy on a computer: open discord.com/app in a browser, "
                + "F12 → Network, click any request to discord.com/api and copy the Authorization header. "
                + "It gives full access to your account, so never share it."));

        notifState = label("");
        setup.addView(notifState);
        setup.addView(hint("Required. Lets the app read Poweramp's playback position to the millisecond and keeps it "
                + "running in the background. Notifications themselves are ignored."));
        setup.addView(button("Open notification access", v -> openNotificationAccess()));

        fileState = label("");
        setup.addView(fileState);
        setup.addView(hint("Needed for .lrc files next to your music and for lyrics embedded in tags. "
                + "Without it only online lyrics (LRCLIB) are used."));
        setup.addView(button("Grant file access", v -> requestFileAccess()));

        batteryState = label("");
        setup.addView(batteryState);
        setup.addView(hint("Recommended, so Android doesn't cut network access while the screen is off."));
        setup.addView(button("Allow background use", v -> requestBatteryExemption()));
        root.addView(setup);

        // general
        section("General");
        LinearLayout gen = card();
        gen.addView(toggle("Enabled", "Update my Discord status", x -> x.enabled, (x, v) -> x.enabled = v));
        gen.addView(toggle("Only follow Poweramp", "Off: follow whichever player is playing (online lyrics only for other apps)",
                x -> x.onlyPoweramp, (x, v) -> x.onlyPoweramp = v));
        gen.addView(toggle("Restore my status when idle", "Put back the status you had before, after pausing/stopping",
                x -> x.restoreStatus, (x, v) -> x.restoreStatus = v));
        gen.addView(number("Seconds paused before restoring", x -> String.valueOf(x.pauseClearSeconds),
                (x, v) -> x.pauseClearSeconds = (int) clamp(v, 0, 3600)));
        root.addView(gen);

        // display
        section("Status format");
        LinearLayout disp = card();
        disp.addView(toggle("Show timestamp", "[1:23] before the line", x -> x.showTimestamp, (x, v) -> x.showTimestamp = v));
        disp.addView(toggle("Show \"Song lyrics - \" label", null, x -> x.showLabel, (x, v) -> x.showLabel = v));
        disp.addView(toggle("Custom format", "Use the template and emoji below instead", x -> x.advanced, (x, v) -> x.advanced = v));
        disp.addView(textField("Template", x -> x.customStatus, (x, v) -> x.customStatus = v));
        disp.addView(textField("Emoji (unicode, or <:name:id> with Nitro)", x -> x.customEmoji, (x, v) -> x.customEmoji = v));
        disp.addView(hint("Placeholders: {lyrics} {lyrics_upper} {lyrics_lower} {lyrics_letters_only} {timestamp} {duration} "
                + "{song_name} {song_name_cropped} {song_name_upper} {song_name_lower} {song_author} {song_author_upper} "
                + "{song_author_lower} {album}"));
        disp.addView(textField("When a song has no synced lyrics (empty = restore/clear)", x -> x.noLyricsStatus,
                (x, v) -> x.noLyricsStatus = v));
        disp.addView(hint("e.g. Listening to {song_name} by {song_author}"));
        root.addView(disp);

        // sources
        section("Lyrics sources");
        LinearLayout src = card();
        src.addView(toggle(".lrc files", "Same name as the song, in its folder or a Lyrics/ subfolder",
                x -> x.useLocalLrc, (x, v) -> x.useLocalLrc = v));
        src.addView(toggle("Embedded lyrics", "MP3 USLT/SYLT, FLAC/Ogg/Opus LYRICS, M4A ©lyr, APE",
                x -> x.useEmbedded, (x, v) -> x.useEmbedded = v));
        src.addView(toggle("Download from LRCLIB", "Free synced lyrics database, results are cached",
                x -> x.useLrclib, (x, v) -> x.useLrclib = v));
        src.addView(toggle("Go online if local lyrics aren't synced", null,
                x -> x.onlineWhenLocalUnsynced, (x, v) -> x.onlineWhenLocalUnsynced = v));
        src.addView(toggle("Act as Poweramp lyrics plugin", "Also show LRCLIB lyrics inside Poweramp when it has none",
                x -> x.providePowerampLyrics, (x, v) -> x.providePowerampLyrics = v));
        root.addView(src);

        // timing
        section("Timing");
        LinearLayout tim = card();
        tim.addView(toggle("Auto offset", "Send lines early by Discord's measured delay + 100 ms",
                x -> x.autoOffset, (x, v) -> x.autoOffset = v));
        tim.addView(number("Auto offset samples", x -> String.valueOf(x.autoOffsetSamples),
                (x, v) -> x.autoOffsetSamples = (int) clamp(v, 1, 20)));
        tim.addView(number("Manual offset (ms, when auto is off)", x -> String.valueOf(x.sendTimeOffset),
                (x, v) -> x.sendTimeOffset = clamp(v, -10000, 10000)));
        root.addView(tim);

        // battery
        section("Battery");
        LinearLayout bat = card();
        bat.addView(number("Min. seconds between updates", x -> String.valueOf(x.minUpdateSeconds),
                (x, v) -> x.minUpdateSeconds = (int) clamp(v, 0, 60)));
        bat.addView(hint("0 = every lyric line, like the original. Each update is a network request that keeps "
                + "the radio awake; 5–10 s skips fast lines and noticeably reduces drain while music plays. "
                + "When Poweramp hasn't played for 10 minutes, the app goes fully to sleep until you press play."));
        root.addView(bat);

        // footer
        TextView foot = text("", 12, false);
        foot.setAlpha(0.7f);
        foot.setText(android.text.Html.fromHtml("Based on <a href=\"https://github.com/OvalQuilter/lyrics-status\">LyricsStatus</a> "
                + "by OvalQuilter. Lyrics from your files and <a href=\"https://lrclib.net\">LRCLIB</a>.<br><br>"
                + "Changing your status with a user token is against Discord's Terms of Service, the same as with the "
                + "original LyricsStatus. Use at your own risk.", android.text.Html.FROM_HTML_MODE_LEGACY));
        foot.setMovementMethod(LinkMovementMethod.getInstance());
        foot.setPadding(0, dp(24), 0, 0);
        root.addView(foot);
    }

    // ------------------------------------------------------------------ live state

    private void refreshLive() {
        Engine.State st = app.engine().state();
        if (st.track == null) {
            nowPlaying.setText("Nothing playing");
            lyricsInfo.setText(MediaListenerService.isConnected() || notificationAccessGranted()
                    ? "Start a song in Poweramp" : "Grant notification access below");
            currentLine.setText("");
        } else {
            nowPlaying.setText((st.playing ? "▶ " : "⏸ ") + st.track.title
                    + (st.track.artist.isEmpty() ? "" : " — " + st.track.artist));
            lyricsInfo.setText(Formatter.timestamp(st.positionMs)
                    + (st.track.durationMs > 0 ? " / " + Formatter.timestamp(st.track.durationMs) : "")
                    + "  ·  " + st.lyricsInfo + (st.lineCount > 0 ? " (" + st.lineCount + " lines)" : ""));
            currentLine.setText(st.currentLine != null ? st.currentLine : "");
        }
        discordInfo.setText(st.lastStatus != null ? "Discord: " + st.lastStatus : "Discord: nothing sent yet");
        errorView.setText(st.error != null ? st.error : "");
        errorView.setVisibility(st.error != null ? View.VISIBLE : View.GONE);
    }

    private void refreshSetup() {
        boolean hasToken = !app.settings().token.isEmpty();
        tokenState.setText(hasToken ? "✓ Discord token" : "✗ Discord token");
        boolean notif = notificationAccessGranted();
        notifState.setText((notif ? "✓" : "✗") + " Notification access");
        fileState.setText((app.hasFileAccess() ? "✓" : "✗") + " File access");
        PowerManager pm = getSystemService(PowerManager.class);
        boolean exempt = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryState.setText((exempt ? "✓" : "○") + " Unrestricted battery");
    }

    private boolean notificationAccessGranted() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName me = new ComponentName(this, MediaListenerService.class);
        for (String s : flat.split(":")) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ actions

    private void testToken(String token) {
        toast("Checking…");
        app.io.execute(() -> {
            String msg;
            try {
                msg = "Token works: " + new DiscordClient(token.trim()).whoAmI();
            } catch (Exception e) {
                msg = "Token check failed: " + e.getMessage();
            }
            final String m = msg;
            app.main.post(() -> toast(m));
        });
    }

    private void openNotificationAccess() {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(this, MediaListenerService.class).flattenToString());
        } else {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    private void requestFileAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshSetup();
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) app.engine().reload();
    }

    @SuppressWarnings("BatteryLife")
    private void requestBatteryExemption() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ------------------------------------------------------------------ widgets

    private interface Setter<T> extends BiConsumer<com.lyricsstatus.poweramp.core.Settings, T> {}

    private View toggle(String title, String desc, Function<com.lyricsstatus.poweramp.core.Settings, Boolean> get,
                        Setter<Boolean> set) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(0, dp(8), 0, dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15, false));
        if (desc != null) {
            TextView d = text(desc, 12, false);
            d.setAlpha(0.65f);
            texts.addView(d);
        }
        l.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(get.apply(app.settings()));
        sw.setOnCheckedChangeListener((b, v) -> {
            com.lyricsstatus.poweramp.core.Settings s = app.settings();
            set.accept(s, v);
            app.updateSettings(s);
        });
        l.addView(sw);
        l.setOnClickListener(v -> sw.toggle());
        return l;
    }

    private View textField(String title, Function<com.lyricsstatus.poweramp.core.Settings, String> get, Setter<String> set) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(8), 0, 0);
        TextView t = text(title, 13, false);
        t.setAlpha(0.8f);
        l.addView(t);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(get.apply(app.settings()));
        e.addTextChangedListener(debounced(v -> {
            com.lyricsstatus.poweramp.core.Settings s = app.settings();
            set.accept(s, v);
            app.updateSettings(s);
        }));
        l.addView(e);
        return l;
    }

    private View number(String title, Function<com.lyricsstatus.poweramp.core.Settings, String> get, Setter<Long> set) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(title, 15, false);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(get.apply(app.settings()));
        e.setMinWidth(dp(80));
        e.setGravity(Gravity.END);
        e.addTextChangedListener(debounced(v -> {
            try {
                long n = Long.parseLong(v.trim());
                com.lyricsstatus.poweramp.core.Settings s = app.settings();
                set.accept(s, n);
                app.updateSettings(s);
            } catch (NumberFormatException ignored) {
            }
        }));
        l.addView(e);
        return l;
    }

    private TextWatcher debounced(java.util.function.Consumer<String> onChange) {
        return new TextWatcher() {
            private Runnable pending;

            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (pending != null) app.main.removeCallbacks(pending);
                final String v = s.toString();
                pending = () -> onChange.accept(v);
                app.main.postDelayed(pending, 700);
            }
        };
    }

    private void section(String title) {
        TextView t = text(title.toUpperCase(), 12, true);
        t.setAlpha(0.7f);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(20), 0, dp(6));
        root.addView(t);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14808080);
        bg.setCornerRadius(dp(14));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(4), 0, dp(4));
        return l;
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String s) {
        TextView t = text(s, 15, true);
        t.setPadding(0, dp(12), 0, dp(2));
        return t;
    }

    private TextView hint(String s) {
        TextView t = text(s, 12, false);
        t.setAlpha(0.65f);
        return t;
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
