<div align="center">

# 🎶 LyricsStatus for Poweramp

**Your Discord status, synced to the lyrics of what you're playing in Poweramp.**

[![Build](https://github.com/Smegoligga/poweramp-lyrics-status/actions/workflows/build.yml/badge.svg)](https://github.com/Smegoligga/poweramp-lyrics-status/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Smegoligga/poweramp-lyrics-status?include_prereleases&label=release)](https://github.com/Smegoligga/poweramp-lyrics-status/releases)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Poweramp](https://img.shields.io/badge/Poweramp-v3-E5484D)
[![License](https://img.shields.io/badge/license-GPL--2.0-blue)](LICENSE)

</div>

---

LyricsStatus for Poweramp is an Android port of [**LyricsStatus** by OvalQuilter](https://github.com/OvalQuilter/lyrics-status). It doesn't use the Spotify API. Instead it follows **Poweramp** on your phone, finds synced lyrics for the song, and updates your Discord custom status one line at a time, in time with the music.

```
🎶  [1:23] Song lyrics - and the words appear right on the beat
```

## ✨ Features

- **Your own lyrics first.** It reads `.lrc` files next to your music, then lyrics embedded in the file's tags:
  - MP3: `USLT`, `SYLT` and `TXXX`
  - FLAC, Ogg and Opus: `LYRICS` and `SYNCEDLYRICS`
  - M4A and ALAC: `©lyr`
  - WAV, AIFF and APE
- **Online fallback.** It uses synced lyrics from [LRCLIB](https://lrclib.net), matched by song length and cached on your phone.
- **Accurate timing.** It reads Poweramp's position to the millisecond, and sends each line early by Discord's measured delay.
- **Same formats as the original.** The timestamp, the `Song lyrics -` label, custom templates with all of OvalQuilter's placeholders, and custom emoji (including Nitro emoji) all work.
- **Puts your status back.** It restores the status you had before when you pause or stop.
- **Works as a Poweramp lyrics plugin.** Lyrics from LRCLIB also show up inside Poweramp itself.
- **Battery friendly.** It sleeps completely when Poweramp is idle and wakes only when a line is due. An optional battery saver limits how often the status updates.
- **Small.** It has no third-party libraries and the APK is tiny.

## 📥 Install

1. Download the latest APK from **[Releases](https://github.com/Smegoligga/poweramp-lyrics-status/releases)**.
2. Open it on your phone. Android will ask you to allow installs from your browser or file manager.
3. Continue with [Configuration](#️-configuration).

> Requires Android 8.0 or newer and Poweramp v3 (build 948+).

## ⚙️ Configuration

Open the **LyricsStatus** app. Everything is on one screen.

### 1. Setup (one time)

| Step | What to do | Why |
|---|---|---|
| **Discord token** | Paste it, tap **Save**, then **Test**. You should see your username. | Lets the app change your status. |
| **Notification access** | Tap **Open notification access** and switch **LyricsStatus** on. | **Required.** Gives the exact playback position and keeps the app running in the background. Notifications are never read. |
| **File access** | Tap **Grant file access** and allow *All files access*. | Needed to read `.lrc` files and embedded lyrics. Without it, only LRCLIB is used. |
| **Unrestricted battery** | Tap **Allow background use**. | Recommended, so Android doesn't cut network access when the screen is off. |

Then **pause and resume Poweramp once**. Poweramp only notices new plugin apps when playback starts.

<details>
<summary><b>How do I get my Discord token?</b></summary>

It's the same token the original LyricsStatus uses. The easiest way to find it is on a computer:

1. Open [discord.com/app](https://discord.com/app) in a browser (not the desktop app) and log in.
2. Press **F12** and open the **Network** tab.
3. Click around in Discord, for example switch channels.
4. Click any request to `discord.com/api`, then find **Request Headers → Authorization**.
5. Copy that value and paste it into the app.

⚠️ The token gives full access to your account, so never share it. If you log out or change your password, Discord issues a new one and you'll need to paste it again.
</details>

### 2. Status format

The default looks exactly like OvalQuilter's: `🎶 [1:23] Song lyrics - line`.

| Setting | Effect |
|---|---|
| **Show timestamp** | Adds `[1:23]` before the line. |
| **Show "Song lyrics - " label** | Adds the label before the line. |
| **Custom format** | Uses your own **Template** and **Emoji** instead of the default. |
| **When a song has no synced lyrics** | What to show for songs without lyrics. For example `Listening to {song_name} by {song_author}`. Leave it empty to restore your normal status. |

**Template placeholders**

| Placeholder | Example output |
|---|---|
| `{lyrics}`, `{lyrics_upper}`, `{lyrics_lower}` | the current line, in its original case, upper case or lower case |
| `{lyrics_letters_only}` (also `_upper_` / `_lower_` variants) | the line without `' " , .` |
| `{timestamp}`, `{duration}` | `1:23`, `3:45` |
| `{song_name}`, `{song_name_cropped}` | `Song (Remastered)`, `Song` |
| `{song_name_upper}`, `{song_name_lower}` (also `_cropped` variants) | `SONG`, `song` |
| `{song_author}`, `{song_author_upper}`, `{song_author_lower}` | `Artist`, `ARTIST`, `artist` |
| `{album}` | `Album` |

**Emoji** can be any unicode emoji, or `<:name:id>` / `<a:name:id>` for a Nitro emoji.

### 3. Lyrics sources

The app checks the sources in this order and uses the first **synced** lyrics it finds:

1. **`.lrc` files.** Name the file like the song (`01 - Song.lrc` next to `01 - Song.flac`). The app also checks a `Lyrics/` subfolder and `Artist - Title.lrc`.
2. **Embedded lyrics** in the file's tags.
3. **LRCLIB** online. Results are cached, and so are misses (for 3 days).

| Setting | Effect |
|---|---|
| **Go online if local lyrics aren't synced** | Plain-text lyrics can't follow the music, so the app tries LRCLIB instead. |
| **Act as Poweramp lyrics plugin** | When Poweramp has no lyrics for a song, it shows the LRCLIB ones too. |
| **Find lyrics again / Clear cache** (top card) | Retry a lookup, for example after adding a `.lrc` file or granting file access. |

### 4. General and timing

| Setting | Default | Effect |
|---|---|---|
| **Enabled** | on | Master switch. |
| **Only follow Poweramp** | on | Turn off to follow any music app. Other apps only get online lyrics. |
| **Restore my status when idle** | on, after 15 s | Puts back the status you had before LyricsStatus took over. |
| **Auto offset** | on, 3 samples | Sends each line early by Discord's average response time + 100 ms, like the original. |
| **Manual offset** | 500 ms | Used when auto offset is off. A higher value makes lines appear earlier. |

### 5. Battery

The app is built to stay out of the way.

**While nothing plays:**
- After 10 minutes without playback, it releases its background connection, so Android can stop it completely. Pressing play in Poweramp wakes it automatically.
- On Android 13+, it only receives ongoing notifications (like the media one), not every message or email.

**While music plays:**
- It wakes only when the next line is due, with no polling.
- It reuses one connection to Discord, so there's no new encrypted handshake per line.
- It doesn't write to storage per line.

| Setting | Default | Effect |
|---|---|---|
| **Min. seconds between updates** | 0 (every line) | Each update is a network request that keeps the phone's radio awake. Set 5–10 s to skip fast lines and noticeably reduce drain while music plays. |

> Sleeping only applies with **Only follow Poweramp** on, because other players can't wake the app.

## 🛠️ Building from source

<details open>
<summary><b>Option A: GitHub Actions (no tools needed)</b></summary>

1. **Fork** this repository, or push your changes to it.
2. Open the **Actions** tab. The **Build APK** workflow runs on every push to `main`.
3. When it finishes, the APK is published under **Releases**, named after `versionName` in `app/build.gradle`. It's also available as a downloadable artifact on the workflow run page.

To make a new version, change `versionName` (and increase `versionCode`) in `app/build.gradle`, then push.
</details>

<details>
<summary><b>Option B: Android Studio</b></summary>

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** the project folder and wait for Gradle sync to finish.
3. Choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, or press ▶ with your phone connected.
</details>

<details>
<summary><b>Option C: Command line</b></summary>

Requirements:
- **JDK 17**
- The **Android SDK** with platform 35 and build-tools 35. Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=/path/to/sdk`.

```bash
git clone https://github.com/Smegoligga/poweramp-lyrics-status.git
cd poweramp-lyrics-status
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

The APK is written to `app/build/outputs/apk/release/LyricsStatus-Poweramp-<version>-release.apk`.

Install it over USB with:

```bash
adb install -r app/build/outputs/apk/release/*.apk
```
</details>

> **Signing:** every build is signed with the shared key in `keystore/`, so a new APK installs over the old one and keeps your settings. If you publish your own fork for other people, replace it with a private key.

<details>
<summary><b>Project layout</b></summary>

```
app/src/main/java/com/lyricsstatus/poweramp/
├── core/                    plain Java, no Android code
│   ├── Engine.java           decides which line to send when (port of StatusChanger/index.ts)
│   ├── Formatter.java        status text and placeholders
│   ├── AutoOffset.java       Discord delay compensation
│   ├── LrcParser.java        LRC / enhanced LRC parsing
│   ├── TagLyricsReader.java  embedded lyrics: ID3, FLAC, Ogg, Opus, MP4, APE, WAV, AIFF
│   ├── LocalLyricsFinder.java  .lrc lookup and Poweramp path resolution
│   ├── LrcLibClient.java     LRCLIB lookup
│   ├── LyricsCache.java      on-device lyrics cache
│   ├── LyricsRepository.java order of lyrics sources
│   ├── DiscordClient.java    custom status API
│   └── StatusSender.java     request queue, rate limits, status restore
├── App.java                 wires everything together
├── MediaListenerService.java  media session position (notification access)
├── PowerampReceiver.java    Poweramp track and status broadcasts
├── LyricsRequestReceiver.java  Poweramp lyrics plugin
├── PowerampApi.java         Poweramp API constants
├── Prefs.java               settings storage
└── MainActivity.java        settings screen
```
</details>

## ❓ Troubleshooting

| Problem | Fix |
|---|---|
| "Grant notification access" stays red | Toggle the permission off and on again. On Xiaomi, Huawei or Oppo phones, also allow **Autostart**. |
| Nothing happens in Poweramp | Pause and resume Poweramp once after installing. |
| "Discord rejected the token (401)" | Your token changed. Paste the new one. |
| "No synced lyrics found (file not accessible)" | Grant **File access**, then tap **Find lyrics again**. |
| Lines are early or late | Keep **Auto offset** on, or turn it off and adjust **Manual offset**. |
| Wrong lyrics | Put a correct `.lrc` next to the song (local files always win), then tap **Clear cache**. |

## ⚠️ Disclaimer

Like the original LyricsStatus, this app changes your status through Discord's API with your user token. **That is against Discord's Terms of Service, so use it at your own risk.**

Your token:
- is stored only in the app's private storage,
- is excluded from Android backups,
- is only ever sent to `discord.com`.

## 🙏 Credits

- **[LyricsStatus](https://github.com/OvalQuilter/lyrics-status) by OvalQuilter.** The original idea, and the code this app is ported from.
- **[Poweramp API](https://github.com/maxmpz/powerampapi)** by Maksim Petrov, for the track broadcasts and the lyrics plugin protocol.
- **[LRCLIB](https://lrclib.net)**, for free synced lyrics.

This project is derived from LyricsStatus, so it uses the same license: **[GPL-2.0](LICENSE)**.
