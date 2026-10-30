package com.lyricsstatus.poweramp;

import android.content.Context;
import android.content.SharedPreferences;

import com.lyricsstatus.poweramp.core.Settings;
import com.lyricsstatus.poweramp.core.StatusSender;

/** SharedPreferences storage for {@link Settings} and the status-restore bookkeeping. */
public final class Prefs implements StatusSender.Store {

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public Settings load() {
        Settings d = new Settings();
        Settings s = new Settings();
        s.token = sp.getString("token", d.token);
        s.showTimestamp = sp.getBoolean("showTimestamp", d.showTimestamp);
        s.showLabel = sp.getBoolean("showLabel", d.showLabel);
        s.advanced = sp.getBoolean("advanced", d.advanced);
        s.customEmoji = sp.getString("customEmoji", d.customEmoji);
        s.customStatus = sp.getString("customStatus", d.customStatus);
        s.sendTimeOffset = sp.getLong("sendTimeOffset", d.sendTimeOffset);
        s.autoOffset = sp.getBoolean("autoOffset", d.autoOffset);
        s.autoOffsetSamples = sp.getInt("autoOffsetSamples", d.autoOffsetSamples);
        s.enabled = sp.getBoolean("enabled", d.enabled);
        s.onlyPoweramp = sp.getBoolean("onlyPoweramp", d.onlyPoweramp);
        s.useLocalLrc = sp.getBoolean("useLocalLrc", d.useLocalLrc);
        s.useEmbedded = sp.getBoolean("useEmbedded", d.useEmbedded);
        s.useLrclib = sp.getBoolean("useLrclib", d.useLrclib);
        s.onlineWhenLocalUnsynced = sp.getBoolean("onlineWhenLocalUnsynced", d.onlineWhenLocalUnsynced);
        s.providePowerampLyrics = sp.getBoolean("providePowerampLyrics", d.providePowerampLyrics);
        s.noLyricsStatus = sp.getString("noLyricsStatus", d.noLyricsStatus);
        s.restoreStatus = sp.getBoolean("restoreStatus", d.restoreStatus);
        s.pauseClearSeconds = sp.getInt("pauseClearSeconds", d.pauseClearSeconds);
        s.statusExpirySeconds = sp.getInt("statusExpirySeconds", d.statusExpirySeconds);
        s.minUpdateSeconds = sp.getInt("minUpdateSeconds", d.minUpdateSeconds);
        s.mergeLines = sp.getBoolean("mergeLines", d.mergeLines);
        s.keepAliveMode = sp.getInt("keepAliveMode", d.keepAliveMode);
        s.theme = sp.getString("theme", d.theme);
        s.menuSounds = sp.getBoolean("menuSounds", d.menuSounds);
        s.censor = sp.getBoolean("censor", d.censor);
        s.customFilterOn = sp.getBoolean("customFilterOn", d.customFilterOn);
        s.customFilter = sp.getString("customFilter", d.customFilter);
        s.menuVolume = sp.getInt("menuVolume", d.menuVolume);
        return s;
    }

    public void save(Settings s) {
        sp.edit()
                .putString("token", s.token)
                .putBoolean("showTimestamp", s.showTimestamp)
                .putBoolean("showLabel", s.showLabel)
                .putBoolean("advanced", s.advanced)
                .putString("customEmoji", s.customEmoji)
                .putString("customStatus", s.customStatus)
                .putLong("sendTimeOffset", s.sendTimeOffset)
                .putBoolean("autoOffset", s.autoOffset)
                .putInt("autoOffsetSamples", s.autoOffsetSamples)
                .putBoolean("enabled", s.enabled)
                .putBoolean("onlyPoweramp", s.onlyPoweramp)
                .putBoolean("useLocalLrc", s.useLocalLrc)
                .putBoolean("useEmbedded", s.useEmbedded)
                .putBoolean("useLrclib", s.useLrclib)
                .putBoolean("onlineWhenLocalUnsynced", s.onlineWhenLocalUnsynced)
                .putBoolean("providePowerampLyrics", s.providePowerampLyrics)
                .putString("noLyricsStatus", s.noLyricsStatus)
                .putBoolean("restoreStatus", s.restoreStatus)
                .putInt("pauseClearSeconds", s.pauseClearSeconds)
                .putInt("statusExpirySeconds", s.statusExpirySeconds)
                .putInt("minUpdateSeconds", s.minUpdateSeconds)
                .putBoolean("mergeLines", s.mergeLines)
                .putInt("keepAliveMode", s.keepAliveMode)
                .putString("theme", s.theme)
                .putBoolean("menuSounds", s.menuSounds)
                .putBoolean("censor", s.censor)
                .putBoolean("customFilterOn", s.customFilterOn)
                .putString("customFilter", s.customFilter)
                .putInt("menuVolume", s.menuVolume)
                .apply();
    }

    @Override
    public String loadOriginal() {
        return sp.getString("originalStatus", null);
    }

    @Override
    public void saveOriginal(String json) {
        sp.edit().putString("originalStatus", json).apply();
    }
}
