package com.lyricsstatus.poweramp;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lyricsstatus.poweramp.core.DiscordClient;
import com.lyricsstatus.poweramp.core.Engine;
import com.lyricsstatus.poweramp.core.Formatter;
import com.lyricsstatus.poweramp.core.UpdateCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Settings + live status. Every control is built by the current {@link Skin}, so each theme can use its own
 * layout (Discord settings panels, or CS 1.6 VGUI windows). Platform widgets only, no dependencies.
 */
public final class MainActivity extends Activity {

    private static final int REQ_SOUNDS = 7;
    /** Lyrics delay slider range and the recommended value marked on it. */
    private static final int MAX_DELAY = 15, RECOMMENDED_DELAY = 3;

    private App app;
    private Skin skin;
    private Sfx sfx;
    private ScrollView scroll;
    private LinearLayout root;
    private final List<View> anchors = new ArrayList<>();
    /** Tab bar above the page; the page shows only the groups of the selected tab. */
    private LinearLayout tabBar, updateBanner;
    private final List<TextView> tabViews = new ArrayList<>();
    private final List<View> tabLines = new ArrayList<>();
    private int tab;
    private TextView updateState;
    private TextView nowPlaying, lyricsInfo, currentLine, discordInfo, errorView;
    private TextView notifState, fileState, batteryState, tokenState, soundState;
    private Button resetSounds;
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
        com.lyricsstatus.poweramp.core.Settings s = app.settings();
        sfx = new Sfx(this);
        skin = Skin.create(s.theme, this, sfx);
        if (skin.hasSounds()) {
            sfx.setOnStateChange(this::refreshSoundState);
            sfx.load(s.menuSounds, s.menuVolume);
        }

        scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = skin.dp(16);
        root.setPadding(p, p, p, skin.dp(32));
        scroll.addView(root);
        // tab bar and update banner stay put above the scrolling page
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(skin.dp(8), skin.dp(4), skin.dp(8), 0);
        page.addView(tabBar, new LinearLayout.LayoutParams(-1, -2));
        updateBanner = new LinearLayout(this);
        updateBanner.setOrientation(LinearLayout.VERTICAL);
        updateBanner.setPadding(skin.dp(16), skin.dp(8), skin.dp(16), 0);
        updateBanner.setVisibility(View.GONE);
        page.addView(updateBanner, new LinearLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        skin.applyWindow(scroll, root);
        if (Build.VERSION.SDK_INT >= 35) {
            page.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return insets;
            });
        }
        build();
        buildTabs();
        checkForUpdate(false);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sfx.release();
    }

    // ------------------------------------------------------------------ layout

    private static final String[] GROUPS = {"Now Playing", "Setup", "General", "Status Format", "Lyrics Sources",
            "Timing", "Battery", "Options", "Links & Updates"};
    /** Tabs, and which tab each group above is shown in. */
    private static final String[] TABS = {"Now", "Setup", "Status", "Lyrics", "More"};
    private static final int[] TAB_OF_GROUP = {0, 1, 1, 2, 3, 2, 1, 4, 4};
    private int footerIndex = -1;

    private LinearLayout group(int i) {
        int before = root.getChildCount();
        String title = i == 7 && !skin.hasSounds() ? "Appearance" : GROUPS[i];
        LinearLayout g = skin.group(root, title);
        anchors.add(root.getChildAt(before));
        return g;
    }

    private void build() {
        root.addView(skin.header("LRCStatus",
                "Your Discord status follows the lyrics of whatever you're playing. Works with any music app; "
                        + "Poweramp is fully supported with extra features."));

        // CS 1.6 main menu: jump to a window, or Quit
        String[] items = new String[GROUPS.length + 1];
        System.arraycopy(GROUPS, 0, items, 0, GROUPS.length);
        items[GROUPS.length] = "Quit";
        View menu = skin.menu(items, idx -> {
            if (idx < 0) {
                finishAndRemoveTask();
                return;
            }
            showTab(TAB_OF_GROUP[idx]);
            skin.focusGroup(idx);
            View target = anchors.get(idx);
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, target.getTop() - skin.dp(8))));
        });
        if (menu != null) root.addView(menu);

        // ---- now playing
        LinearLayout now = group(0);
        LinearLayout live = skin.box();
        nowPlaying = skin.text("", 16, true);
        lyricsInfo = skin.hint("");
        currentLine = skin.text("", 15, false);
        currentLine.setTypeface(Typeface.create(currentLine.getTypeface(), Typeface.ITALIC));
        currentLine.setTextColor(skin.highlight());
        discordInfo = skin.hint("");
        errorView = skin.text("", 13, true);
        errorView.setTextColor(skin.errorColor());
        live.addView(nowPlaying);
        live.addView(lyricsInfo);
        live.addView(currentLine);
        live.addView(discordInfo);
        live.addView(errorView);
        now.addView(live);
        LinearLayout liveBtns = skin.row();
        LinearLayout searchRow = skin.row();
        searchRow.addView(skin.button("Search lyrics", true, v ->
                startActivity(new Intent(this, SearchActivity.class))));
        now.addView(searchRow);
        liveBtns.addView(skin.button("Find again", false, v -> {
            app.engine().reload();
            toast("Looking up lyrics again");
        }));
        liveBtns.addView(skin.button("Clear cache", false, v -> {
            int n = app.repository().cache().clear();
            app.engine().reload();
            toast("Removed " + n + " cached lyrics");
        }));
        now.addView(liveBtns);

        // ---- setup
        LinearLayout setup = group(1);
        com.lyricsstatus.poweramp.core.Settings s = app.settings();
        tokenState = skin.label("Discord token");
        setup.addView(tokenState);
        EditText token = skin.edit();
        token.setHint("Paste your Discord token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTransformationMethod(PasswordTransformationMethod.getInstance());
        token.setText(s.token);
        setup.addView(token);
        LinearLayout tokenBtns = skin.row();
        tokenBtns.addView(skin.button("Save", true, v -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.token = token.getText().toString().trim().replaceAll("^\"|\"$", "");
            app.updateSettings(ns);
            refreshSetup();
            toast("Token saved");
        }));
        tokenBtns.addView(skin.button("Test", false, v -> testToken(token.getText().toString())));
        tokenBtns.addView(skin.button("Show", false, v -> {
            boolean hidden = token.getTransformationMethod() != null;
            token.setTransformationMethod(hidden ? null : PasswordTransformationMethod.getInstance());
            ((Button) v).setText(hidden ? "Hide" : "Show");
        }));
        setup.addView(tokenBtns);
        setup.addView(skin.hint("Same token LyricsStatus used. Easiest to copy on a computer: open discord.com/app in a "
                + "browser, F12 → Network, click any request to discord.com/api and copy the Authorization header. "
                + "It gives full access to your account, so never share it."));

        notifState = skin.label("");
        setup.addView(notifState);
        setup.addView(skin.hint("Required. Lets the app see what your music app is playing, to the millisecond, and "
                + "keeps it running in the background. Notifications themselves are ignored."));
        setup.addView(skin.button("Open notification access", true, v -> openNotificationAccess()));

        fileState = skin.label("");
        setup.addView(fileState);
        setup.addView(skin.hint("Needed for .lrc files next to your music and for lyrics embedded in tags (Poweramp, "
                + "or other players when the song is in your music library). Without it only online lyrics "
                + "(LRCLIB) are used."));
        setup.addView(skin.button("Grant file access", true, v -> requestFileAccess()));

        batteryState = skin.label("");
        setup.addView(batteryState);
        setup.addView(skin.hint("Recommended, so Android doesn't cut network access while the screen is off."));
        setup.addView(skin.button("Allow background use", true, v -> requestBatteryExemption()));

        // ---- general
        LinearLayout gen = group(2);
        gen.addView(toggle("Enabled", "Update my Discord status", x -> x.enabled, (x, v) -> x.enabled = v));
        gen.addView(toggle("Only follow Poweramp", "Off: follow whichever app is playing. On: Poweramp only, and the "
                + "app can sleep completely while Poweramp is idle", x -> x.onlyPoweramp, (x, v) -> x.onlyPoweramp = v));
        gen.addView(toggle("Restore my status when idle", "Put back the status you had before, after pausing/stopping",
                x -> x.restoreStatus, (x, v) -> x.restoreStatus = v));
        gen.addView(number("Seconds paused before restoring", x -> String.valueOf(x.pauseClearSeconds),
                (x, v) -> x.pauseClearSeconds = (int) clamp(v, 0, 3600)));
        // order shown: Silent, Notification, Off  ->  stored as 1, 2, 0
        final int[] modes = {1, 2, 0};
        int cur = s.keepAliveMode == 2 ? 1 : s.keepAliveMode == 0 ? 2 : 0;
        gen.addView(skin.choice("Keep running while music plays", new String[]{
                "Silent (recommended): keeps updating, no notification",
                "Notification: most reliable, shows a small notification",
                "Off: saves the most battery, may stop until you open the app"}, cur, i -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.keepAliveMode = modes[i];
            app.updateSettings(ns);
        }));

        // ---- status format
        LinearLayout disp = group(3);
        disp.addView(toggle("Show timestamp", "[1:23] before the line", x -> x.showTimestamp, (x, v) -> x.showTimestamp = v));
        disp.addView(toggle("Show \"Song lyrics - \" label", null, x -> x.showLabel, (x, v) -> x.showLabel = v));
        disp.addView(toggle("Custom format", "Use the template and emoji below instead", x -> x.advanced, (x, v) -> x.advanced = v));
        disp.addView(textField("Template", x -> x.customStatus, (x, v) -> x.customStatus = v));
        disp.addView(textField("Emoji (unicode, or <:name:id> with Nitro)", x -> x.customEmoji, (x, v) -> x.customEmoji = v));
        disp.addView(skin.hint("Placeholders: {lyrics} {lyrics_upper} {lyrics_lower} {lyrics_letters_only} {timestamp} "
                + "{duration} {song_name} {song_name_cropped} {song_name_upper} {song_name_lower} {song_author} "
                + "{song_author_upper} {song_author_lower} {album}"));
        disp.addView(textField("When a song has no synced lyrics (empty = restore/clear)", x -> x.noLyricsStatus,
                (x, v) -> x.noLyricsStatus = v));
        disp.addView(skin.hint("e.g. Listening to {song_name} by {song_author}"));
        disp.addView(toggle("Censor swears and slurs", "Masks them in your status, like f***. Off: nothing is censored.",
                x -> x.censor, (x, v) -> x.censor = v));
        disp.addView(toggle("Custom word filter", "Also mask your own words below", x -> x.customFilterOn,
                (x, v) -> x.customFilterOn = v));
        disp.addView(textField("Words to filter (comma-separated)", x -> x.customFilter, (x, v) -> x.customFilter = v));

        // ---- sources
        LinearLayout src = group(4);
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
        src.addView(toggle("Genius", "Also search Genius. Its lyrics aren't synced, so they can't drive your status "
                + "line by line, but they show in Search lyrics and inside Poweramp when LRCLIB has nothing",
                x -> x.useGenius, (x, v) -> x.useGenius = v));

        // ---- timing
        LinearLayout tim = group(5);
        tim.addView(toggle("Auto offset", "Send lines early by Discord's measured delay + 100 ms",
                x -> x.autoOffset, (x, v) -> x.autoOffset = v));
        tim.addView(number("Auto offset samples", x -> String.valueOf(x.autoOffsetSamples),
                (x, v) -> x.autoOffsetSamples = (int) clamp(v, 1, 20)));
        tim.addView(number("Manual offset (ms, when auto is off)", x -> String.valueOf(x.sendTimeOffset),
                (x, v) -> x.sendTimeOffset = clamp(v, -10000, 10000)));
        // Lyrics delay: minimum gap between status updates, with the recommended value marked
        tim.addView(skin.slider("Lyrics delay", s.minUpdateSeconds, MAX_DELAY, RECOMMENDED_DELAY,
                "Recommended: " + RECOMMENDED_DELAY + " s (tap to use)", "s", v -> {
                    com.lyricsstatus.poweramp.core.Settings ns = app.settings();
                    ns.minUpdateSeconds = v;
                    app.updateSettings(ns);
                }, null));
        tim.addView(skin.hint("Minimum time between status changes. Lines that come faster are skipped and the "
                + "newest one is sent. 0 sends every line like the original, but fast songs can then get you "
                + "rate limited by Discord (the app waits it out). The recommended " + RECOMMENDED_DELAY + " s keeps "
                + "almost every line while avoiding bursts. Higher also saves battery."));
        tim.addView(toggle("Merge skipped lines", "When the delay skips lines, show them together "
                + "(line A / line B) instead of only the newest one", x -> x.mergeLines, (x, v) -> x.mergeLines = v));

        // ---- battery
        LinearLayout bat = group(6);
        bat.addView(skin.hint("Each status update is a network request that keeps the radio awake, so a longer "
                + "Lyrics delay (under Timing) uses less battery while music plays: 5–10 s noticeably reduces drain. "
                + "With \"Only follow Poweramp\" on, the app goes fully to sleep after 10 minutes without playback "
                + "and Poweramp wakes it when you press play."));

        // ---- options / appearance
        LinearLayout opt = group(7);
        if (skin.hasSounds()) opt.addView(skin.label("Appearance"));
        opt.addView(skin.themePicker(s.theme, id -> {
            if (id.equals(s.theme)) return;
            app.setTheme(id);
            recreate();
        }));
        if (skin.hasSounds()) buildSounds(opt, s);
        else opt.addView(skin.hint("Classic follows your phone's light/dark mode. CS 1.6 is the Counter-Strike 1.6 "
                + "look from smegolas.com, with its menu sounds."));

        // ---- links & updates
        LinearLayout links = group(8);
        LinearLayout linkRow = skin.row();
        linkRow.addView(iconButton(R.drawable.ic_code, "GitHub", UpdateCheck.REPO_URL));
        linkRow.addView(iconButton(R.drawable.ic_web, "smegolas.com", "https://smegolas.com"));
        links.addView(linkRow);
        links.addView(skin.hint("Version " + installedVersion() + ". Source code, releases and issues are on GitHub."));
        updateState = skin.label("");
        links.addView(updateState);
        LinearLayout updBtns = skin.row();
        updBtns.addView(skin.button("Check for updates now", true, v -> checkForUpdate(true)));
        updBtns.addView(skin.button("Add to Obtainium", false, v -> addToObtainium()));
        links.addView(updBtns);
        String[] freq = {"Every time the app opens", "Once a day", "Once a week", "Never (only when I tap Check now)"};
        links.addView(skin.choice("Look for updates", freq, Math.max(0, Math.min(3, s.updateFrequency)), i -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.updateFrequency = i;
            app.updateSettings(ns);
        }));
        links.addView(skin.hint("When a new version is out, a notice with a download button appears at the top of "
                + "this screen. With Obtainium, updates install from the same GitHub releases (turn on "
                + "\"Include prereleases\" for this app in Obtainium)."));

        // ---- footer
        footerIndex = root.getChildCount();
        TextView foot = skin.hint("");
        foot.setText(android.text.Html.fromHtml("Based on <a href=\"https://github.com/OvalQuilter/lyrics-status\">LyricsStatus</a> "
                + "by OvalQuilter. Lyrics from your files and <a href=\"https://lrclib.net\">LRCLIB</a>.<br><br>"
                + "Changing your status with a user token is against Discord's Terms of Service, the same as with the "
                + "original LyricsStatus. Use at your own risk.", android.text.Html.FROM_HTML_MODE_LEGACY));
        foot.setMovementMethod(LinkMovementMethod.getInstance());
        foot.setLinkTextColor(skin.linkColor());
        foot.setPadding(0, skin.dp(24), 0, 0);
        root.addView(foot);
    }

    /** Options > Audio on smegolas.com: menu sounds checkbox, volume slider (disabled while off). */
    private void buildSounds(LinearLayout opt, com.lyricsstatus.poweramp.core.Settings s) {
        opt.addView(skin.label("Audio"));
        final View[] slider = new View[1];
        opt.addView(skin.toggle("Menu sounds", "Menu sounds when you press buttons, in the CS 1.6 theme only. "
                + "Off by default.", s.menuSounds, on -> {
            int vol = app.settings().menuVolume;
            app.setMenuSounds(on, vol);
            sfx.load(on, vol);
            setEnabledDeep(slider[0], on);
            refreshSoundState();
        }));
        slider[0] = skin.slider("Volume", s.menuVolume, 100, -1, null, "", vol -> {
            app.setMenuSounds(app.settings().menuSounds, vol);
            sfx.setVolume(vol);
        }, sfx::playRelease);
        setEnabledDeep(slider[0], s.menuSounds);
        opt.addView(slider[0]);
        soundState = skin.hint("");
        opt.addView(soundState);
        LinearLayout soundBtns = skin.row();
        soundBtns.addView(skin.button("Import sound files…", false, v -> pickSounds()));
        resetSounds = skin.button("Use built-in sounds", false, v -> {
            sfx.resetToBuiltIn();
            com.lyricsstatus.poweramp.core.Settings cur = app.settings();
            sfx.load(cur.menuSounds, cur.menuVolume);
            refreshSoundState();
            toast("Back to the built-in sounds");
        });
        soundBtns.addView(resetSounds);
        opt.addView(soundBtns);
        opt.addView(skin.hint("The app has its own sounds built in. To use different ones, import files named "
                + "buttonrollover.wav, buttonclick.wav and buttonclickrelease.wav; they replace the built-in ones "
                + "until you tap Use built-in sounds."));
        refreshSoundState();
    }

    private void refreshSoundState() {
        if (soundState == null) return;
        soundState.setText(sfx.describe() + (sfx.enabled() ? "" : "\nTick Menu sounds to hear them.")
                + "\nPlays at your media volume.");
        if (resetSounds != null) resetSounds.setVisibility(sfx.imported() > 0 ? View.VISIBLE : View.GONE);
    }

    private void pickSounds() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "application/octet-stream"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_SOUNDS);
        } catch (Exception e) {
            toast("No file picker available");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SOUNDS || resultCode != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int n = sfx.importFrom(this, uris);
        if (n == 0) {
            toast("None of those were buttonrollover/buttonclick/buttonclickrelease.wav");
            refreshSoundState();
            return;
        }
        // Importing means you want to hear them: switch menu sounds on, then rebuild the screen so the
        // checkbox and slider show it
        com.lyricsstatus.poweramp.core.Settings s = app.settings();
        app.setMenuSounds(true, s.menuVolume);
        toast("Added " + n + " sound file" + (n == 1 ? "" : "s") + ", menu sounds on");
        recreate();
    }

    private static void setEnabledDeep(View v, boolean on) {
        if (v == null) return;
        v.setEnabled(on);
        v.setAlpha(on ? 1f : 0.55f);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setEnabled(on);
        }
    }

    // ------------------------------------------------------------------ live state

    private void refreshLive() {
        Engine.State st = app.engine().state();
        if (st.track == null) {
            nowPlaying.setText("Nothing playing");
            lyricsInfo.setText(MediaListenerService.isConnected() || notificationAccessGranted()
                    ? "Start playing music" : "Grant notification access below");
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

    // ------------------------------------------------------------------ settings rows

    private interface Setter<T> extends BiConsumer<com.lyricsstatus.poweramp.core.Settings, T> {}

    private View toggle(String title, String desc, Function<com.lyricsstatus.poweramp.core.Settings, Boolean> get,
                        Setter<Boolean> set) {
        return skin.toggle(title, desc, get.apply(app.settings()), v -> {
            com.lyricsstatus.poweramp.core.Settings s = app.settings();
            set.accept(s, v);
            app.updateSettings(s);
        });
    }

    private View textField(String title, Function<com.lyricsstatus.poweramp.core.Settings, String> get, Setter<String> set) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, skin.dp(8), 0, 0);
        l.addView(skin.fieldLabel(title));
        EditText e = skin.edit();
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
        l.setPadding(0, skin.dp(4), 0, skin.dp(4));
        l.addView(skin.text(title, 14, false), new LinearLayout.LayoutParams(0, -2, 1f));
        EditText e = skin.edit();
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setText(get.apply(app.settings()));
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(skin.dp(90), -2);
        lp.leftMargin = skin.dp(8);
        l.addView(e, lp);
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

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ tabs

    private void buildTabs() {
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            TextView t = skin.text(TABS[i], 14, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(0, skin.dp(10), 0, skin.dp(7));
            cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
            View line = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, skin.dp(3));
            lp.leftMargin = lp.rightMargin = skin.dp(10);
            cell.addView(line, lp);
            cell.setOnClickListener(v -> {
                showTab(idx);
                scroll.scrollTo(0, 0);
            });
            cell.setContentDescription(TABS[i] + " tab");
            sfx.wire(cell, Sfx.MENU);
            tabBar.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
            tabViews.add(t);
            tabLines.add(line);
        }
        showTab(Math.max(0, Math.min(TABS.length - 1, app.prefs().tab())));
    }

    /** Shows the groups of one tab. Everything above the first group (title, CS menu) belongs to "Now". */
    private void showTab(int t) {
        tab = t;
        app.prefs().setTab(t);
        int first = anchors.isEmpty() ? root.getChildCount() : root.indexOfChild(anchors.get(0));
        int n = root.getChildCount();
        for (int c = 0; c < n; c++) {
            int owner;
            if (c < first) owner = 0;
            else if (footerIndex >= 0 && c >= footerIndex) owner = TABS.length - 1;
            else {
                int g = 0;
                for (int i = 0; i < anchors.size(); i++) if (root.indexOfChild(anchors.get(i)) <= c) g = i;
                owner = TAB_OF_GROUP[g];
            }
            root.getChildAt(c).setVisibility(owner == t ? View.VISIBLE : View.GONE);
        }
        int plain = skin.text("", 14, false).getCurrentTextColor();
        for (int i = 0; i < tabViews.size(); i++) {
            boolean on = i == t;
            TextView v = tabViews.get(i);
            v.setTextColor(on ? skin.highlight() : plain);
            v.setAlpha(on ? 1f : 0.7f);
            tabLines.get(i).setBackgroundColor(on ? skin.highlight() : 0x00000000);
            ((View) v.getParent()).setSelected(on);
        }
    }

    // ------------------------------------------------------------------ links and updates

    private View iconButton(int icon, String label, String url) {
        Button b = skin.button(label, false, v -> open(url));
        android.graphics.drawable.Drawable d = getDrawable(icon);
        if (d != null) {
            d = d.mutate();
            d.setTint(b.getCurrentTextColor());
            int sz = skin.dp(18);
            d.setBounds(0, 0, sz, sz);
            b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(skin.dp(6));
        }
        return b;
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No app can open " + url);
        }
    }

    private String installedVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Opens Obtainium's "add app" screen for this repo; points to Obtainium itself when it isn't installed. */
    private void addToObtainium() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("obtainium://add/" + UpdateCheck.REPO_URL)));
        } catch (android.content.ActivityNotFoundException e) {
            toast("Install Obtainium first, then tap this again");
            open("https://github.com/ImranR98/Obtainium/releases");
        }
    }

    /** On launch (when due) or by hand: look for a newer release and show the notice at the top. */
    private void checkForUpdate(boolean byHand) {
        com.lyricsstatus.poweramp.core.Settings s = app.settings();
        long now = System.currentTimeMillis();
        if (!byHand && !UpdateCheck.due(s.updateFrequency, app.prefs().lastUpdateCheck(), now)) return;
        if (byHand && updateState != null) updateState.setText("Checking GitHub…");
        final String mine = installedVersion();
        app.io.execute(() -> {
            UpdateCheck.Release r = null;
            String err = null;
            try {
                r = UpdateCheck.latest();
            } catch (Exception e) {
                err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            final UpdateCheck.Release fr = r;
            final String fe = err;
            app.main.post(() -> {
                if (isFinishing()) return;
                if (fe == null) app.prefs().setLastUpdateCheck(System.currentTimeMillis());
                boolean newer = UpdateCheck.isNewer(fr, mine);
                if (updateState != null) {
                    updateState.setText(fe != null ? "Couldn't check for updates (" + fe + ")"
                            : newer ? "Version " + fr.version + " is available" : "You have the newest version (" + mine + ")");
                }
                if (newer && (byHand || !fr.version.equals(app.prefs().skippedVersion()))) showUpdate(fr);
            });
        });
    }

    private void showUpdate(UpdateCheck.Release r) {
        updateBanner.removeAllViews();
        LinearLayout box = skin.box();
        TextView t = skin.text("LRCStatus " + r.version + " is available", 15, true);
        t.setTextColor(skin.highlight());
        box.addView(t);
        box.addView(skin.hint("You have " + installedVersion() + ". Download and open the APK to update; your "
                + "settings stay."));
        LinearLayout b = skin.row();
        b.addView(skin.button("Download", true, v -> open(r.apkUrl != null ? r.apkUrl : r.pageUrl)));
        b.addView(skin.button("What's new", false, v -> open(r.pageUrl)));
        b.addView(skin.button("Later", false, v -> {
            app.prefs().setSkippedVersion(r.version);
            updateBanner.setVisibility(View.GONE);
        }));
        box.addView(b);
        updateBanner.addView(box);
        updateBanner.setVisibility(View.VISIBLE);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
