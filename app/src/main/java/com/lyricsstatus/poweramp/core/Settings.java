package com.lyricsstatus.poweramp.core;

/**
 * All user settings. Defaults match OvalQuilter's LyricsStatus v3 (src/Settings.ts) where an equivalent exists.
 */
public final class Settings {
    // credentials
    public String token = "";

    // view (same defaults as Settings.view in LyricsStatus)
    public boolean showTimestamp = true;
    public boolean showLabel = true;
    public boolean advanced = false;
    public String customEmoji = "🎶";
    public String customStatus = "[{timestamp}] [{lyrics}]";

    // timings (same defaults as Settings.timings)
    public long sendTimeOffset = 500;
    public boolean autoOffset = true;
    public int autoOffsetSamples = 3;

    // new for the Android/Poweramp version
    public boolean enabled = true;
    /** Universal by default: follow whichever app is playing. Poweramp gets extra features either way. */
    public boolean onlyPoweramp = false;
    public boolean useLocalLrc = true;
    public boolean useEmbedded = true;
    public boolean useLrclib = true;
    /** Prefer online synced lyrics when the local/embedded ones are unsynced. */
    public boolean onlineWhenLocalUnsynced = true;
    /** Act as a Poweramp lyrics plugin (feeds LRCLIB results into Poweramp's lyrics view). */
    public boolean providePowerampLyrics = true;
    /** Status while a song with no synced lyrics plays. Empty = clear the status. */
    public String noLyricsStatus = "";
    /** Put back the status you had before LyricsStatus started overwriting it. */
    public boolean restoreStatus = true;
    /** Seconds of pause before the status is cleared / restored. */
    public int pauseClearSeconds = 15;
    /** How long each status lives on Discord (OvalQuilter used 60 s) so a crashed app never leaves a stale line. */
    public int statusExpirySeconds = 60;
    /**
     * Battery saver: minimum seconds between status updates. 0 = every line (like the original).
     * Fewer requests means the phone's radio can sleep more between lines.
     */
    public int minUpdateSeconds = 0;
    /** App colour theme id (see Theme in the Android layer). */
    public String theme = "classic";
    /** CS 1.6 theme menu sounds (off by default, like smegolas.com) and their volume 0-100. */
    public boolean menuSounds = false;
    /** Mask strong swears and slurs in the status (off = nothing is censored). */
    public boolean censor = false;
    /** Mask the user's own words, listed comma- or line-separated. */
    public boolean customFilterOn = false;
    public String customFilter = "";
    public int menuVolume = 70;

    public Settings copy() {
        Settings s = new Settings();
        s.token = token;
        s.showTimestamp = showTimestamp;
        s.showLabel = showLabel;
        s.advanced = advanced;
        s.customEmoji = customEmoji;
        s.customStatus = customStatus;
        s.sendTimeOffset = sendTimeOffset;
        s.autoOffset = autoOffset;
        s.autoOffsetSamples = autoOffsetSamples;
        s.enabled = enabled;
        s.onlyPoweramp = onlyPoweramp;
        s.useLocalLrc = useLocalLrc;
        s.useEmbedded = useEmbedded;
        s.useLrclib = useLrclib;
        s.onlineWhenLocalUnsynced = onlineWhenLocalUnsynced;
        s.providePowerampLyrics = providePowerampLyrics;
        s.noLyricsStatus = noLyricsStatus;
        s.restoreStatus = restoreStatus;
        s.pauseClearSeconds = pauseClearSeconds;
        s.statusExpirySeconds = statusExpirySeconds;
        s.minUpdateSeconds = minUpdateSeconds;
        s.theme = theme;
        s.menuSounds = menuSounds;
        s.censor = censor;
        s.customFilterOn = customFilterOn;
        s.customFilter = customFilter;
        s.menuVolume = menuVolume;
        return s;
    }

    /** Emoji shown next to the status: simple view always uses 🎶 like the original. */
    public String emoji() {
        return advanced ? customEmoji : "🎶";
    }
}
