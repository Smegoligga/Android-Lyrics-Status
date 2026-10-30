# LyricsStatus for Poweramp

Your Discord custom status follows the lyrics of whatever **Poweramp** is playing on your Android phone, line by line, in sync.

This is an Android port of [**LyricsStatus** by OvalQuilter](https://github.com/OvalQuilter/lyrics-status). The status format, placeholders, defaults, auto-offset timing and Discord request are ported from LyricsStatus v3. Instead of the Spotify API it uses Poweramp and your own music files.

```
Poweramp ──► track + file path (Poweramp broadcast)
         └─► position to the millisecond (Android media session)
                    │
lyrics:  .lrc next to the file → embedded tags → cache → LRCLIB
                    │
         line due? (position + Discord delay + 100 ms)
                    │
Discord  PATCH users/@me/settings  { custom_status: "[1:23] Song lyrics - …" }
```

## Where lyrics come from

The app checks these sources in order and uses the first synced lyrics it finds:

1. **`.lrc` files** with the same name as the song (`01 - Song.lrc` next to `01 - Song.flac`). It also looks for `Song.flac.lrc`, `Artist - Title.lrc`, and files in a `Lyrics/` subfolder. Encoding is detected automatically (UTF-8, UTF-16, GBK and Windows-1252).
2. **Embedded lyrics** in the file's tags:
   - MP3: ID3v2.2, v2.3 and v2.4 `USLT`, `SYLT` (including karaoke-style) and `TXXX:LYRICS`
   - FLAC, Ogg Vorbis and Opus: `LYRICS`, `SYNCEDLYRICS` and `UNSYNCEDLYRICS`
   - M4A and ALAC: `©lyr`
   - WAV and AIFF: ID3 chunk
   - APE, MPC and WavPack: APEv2
3. **[LRCLIB](https://lrclib.net)**, a free synced-lyrics database (the same source LyricsStatus v3 uses). Results are matched by duration and cached on the phone.

Plain (unsynced) lyrics can't drive a status line by line. If your local lyrics are unsynced, the app tries LRCLIB for a synced version.

**Bonus:** the app also registers as a **Poweramp lyrics plugin**. When Poweramp has no lyrics for a track, it shows the ones from LRCLIB inside Poweramp too. You can turn this off in the settings.

## Getting the APK

You can't install this from a store, so you build it once. The repo already has a GitHub Actions workflow that does this.

1. Create a new GitHub repository and upload the contents of this folder.
2. Open the **Actions** tab. The *Build APK* workflow runs on every push and takes about 3 minutes.
3. Open the finished run and download **LyricsStatus-Poweramp-apk** under *Artifacts*. It's a zip containing the APK.
4. Optional: push a tag like `v1.0.0` and the APK is also attached to a GitHub Release.

You can also build it with Android Studio (open the folder, then *Build → Build APK*) or with `./gradlew assembleRelease`.

Every build is signed with the same key from `keystore/`, so a new APK installs over the old one and keeps your settings.

## Setup on the phone

1. Install the APK. You'll need to allow installs from your browser or file manager.
2. Open **LyricsStatus** and complete each item in *Setup*:
   - **Discord token:** paste it, tap *Save*, then *Test*. This is the same token LyricsStatus used. The easiest way to get it is on a computer: open discord.com/app in a browser, press F12, go to *Network*, click any `api` request and copy the `Authorization` header.
   - **Notification access (required):** this is how the app reads Poweramp's exact playback position. It also keeps the app running in the background. Notification content is never read.
   - **File access:** needed to read `.lrc` files and tags. Without it, only LRCLIB is used.
   - **Unrestricted battery (recommended):** stops Android from cutting the app's network access while the screen is off.
3. Play something in Poweramp. The card at the top shows the track, where the lyrics came from, the current line, and what was sent to Discord.

> Poweramp caches its list of plugin apps. After installing, **pause and resume Poweramp once**, or restart it, so it starts sending events to this app.

## Settings (vs. the original)

| LyricsStatus | Here | Default |
|---|---|---|
| Timestamp / label | *Show timestamp*, *Show "Song lyrics - " label* | on / on → `[1:23] Song lyrics - line` |
| Advanced: custom status + emoji | *Custom format*, *Template*, *Emoji* | `[{timestamp}] [{lyrics}]`, 🎶 |
| Autooffset / samples / send offset | *Timing* section | auto, 3 samples, 500 ms |
| — | *Only follow Poweramp* (off = any player, online lyrics only) | on |
| — | *Restore my status when idle* (puts your normal status back after pausing) | on, after 15 s |
| — | *When a song has no synced lyrics* (template, empty = restore) | empty |
| — | Sources: `.lrc`, embedded, LRCLIB, Poweramp plugin | all on |

**Template placeholders** (same as LyricsStatus, plus two new ones):

- Lyrics: `{lyrics}`, `{lyrics_upper}`, `{lyrics_lower}`, `{lyrics_letters_only}`, `{lyrics_upper_letters_only}`, `{lyrics_lower_letters_only}`
- Time: `{timestamp}`, `{duration}` (new)
- Song name: `{song_name}`, `{song_name_upper}`, `{song_name_lower}`, `{song_name_cropped}`, `{song_name_upper_cropped}`, `{song_name_lower_cropped}`
- Artist: `{song_author}`, `{song_author_upper}`, `{song_author_lower}`
- Album: `{album}` (new)

The emoji can be unicode, or `<:name:id>` for a Nitro emoji.

**Small behaviour changes from the original:**

- Lines are re-sent after you seek backwards.
- During long instrumental gaps, the status is refreshed before Discord's 60-second expiry so it doesn't vanish.
- After a rate limit, the newest line is sent instead of a backlog.
- The app sleeps until the next line is due instead of polling 60 times a second, to save battery.

## Troubleshooting

- **"Grant notification access"** even though you granted it: toggle it off and on again in Android settings. Some phones (Xiaomi, Huawei, Oppo) also need *Autostart* allowed.
- **"Discord rejected the token (401)":** your token changed. This happens when you log out or change your password. Paste the new one.
- **"No synced lyrics found (file not accessible)":** grant *File access*, then tap *Find lyrics again*.
- **Lines appear late or early:** keep *Auto offset* on, or turn it off and adjust *Manual offset*. A higher value sends lines earlier.
- **Wrong lyrics from LRCLIB:** put a correct `.lrc` next to the file, since local files always win. Then tap *Clear cache*.

## ⚠️ Discord Terms of Service

Like the original LyricsStatus, this app changes your status through Discord's API using your user token. That is against Discord's Terms of Service, so **use it at your own risk**. Your token gives full access to your account:

- It's stored only in the app's private storage.
- It's excluded from Android backups.
- It's sent only to `discord.com`.

## Credits and license

- [LyricsStatus](https://github.com/OvalQuilter/lyrics-status) by **OvalQuilter**, the original idea and the code this port is based on (GPL-2.0).
- [Poweramp API](https://github.com/maxmpz/powerampapi) by Maksim Petrov, for the track broadcasts and lyrics plugin protocol.
- [LRCLIB](https://lrclib.net) for synced lyrics.

Because this is derived from LyricsStatus, it is released under the same license, **GPL-2.0** (see `LICENSE`).
