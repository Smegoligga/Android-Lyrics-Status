# Android Lyrics Status

Shows the synced lyrics of whatever you're playing on Android as your Discord status. Works with any music app; Poweramp is fully supported.

An Android port of [LyricsStatus](https://github.com/OvalQuilter/lyrics-status) by OvalQuilter.

[![Build](https://github.com/Smegoligga/Android-Lyrics-Status/actions/workflows/build.yml/badge.svg)](https://github.com/Smegoligga/Android-Lyrics-Status/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/Smegoligga/Android-Lyrics-Status?include_prereleases&label=release)](https://github.com/Smegoligga/Android-Lyrics-Status/releases)

## Features

- Lyrics from `.lrc` files, tags embedded in your music, or [LRCLIB](https://lrclib.net), plus manual search
- Millisecond-accurate timing
- Same status format and placeholders as the original
- Restores your normal status when you pause
- Poweramp extras: always finds local lyrics, acts as a lyrics plugin, sleeps while idle
- Optional swear/slur censor and custom word filter
- Themes: Classic (Discord) and CS 1.6, with menu sounds

## Install

Download the APK from [Releases](https://github.com/Smegoligga/Android-Lyrics-Status/releases) and open it on your phone. Needs Android 8.0+.

## Setup

1. Paste your Discord token and tap **Save**.
2. Allow **notification access** (needed to read what's playing).
3. Allow **file access** to use your own `.lrc` files and embedded lyrics.
4. Optionally allow unrestricted battery so it keeps working with the screen off.

If you use Poweramp, pause and resume it once after installing.

To get your token: open discord.com/app in a browser on a computer, press F12, go to Network, click any request to `discord.com/api` and copy the `Authorization` header. Don't share it with anyone.

## Settings

| Setting | What it does |
|---|---|
| Search lyrics | Find lyrics by song, artist or album. Use a result for the current song, or an album for your whole album |
| Status format | Timestamp, label, or your own template with placeholders like `{lyrics}`, `{song_name}`, `{song_author}` |
| Lyrics delay | Minimum time between status changes. 3 s is recommended to avoid Discord rate limits |
| Merge skipped lines | Shows lines skipped by the delay together instead of dropping them |
| Keep running while music plays | Silent (default) keeps updating in the background without a notification. Notification is the most reliable. Off saves the most battery |
| Only follow Poweramp | Ignore other apps and let the app sleep while Poweramp is idle |
| Censor / word filter | Mask swears, slurs or your own words. Off by default |
| Theme | Classic (Discord) or CS 1.6. CS 1.6 has optional menu sounds |

## Building from source

The GitHub Actions workflow builds an APK on every push to `main` and publishes it as a pre-release named after `versionName` in `app/build.gradle`. Release notes come from `release-notes/<version>.md`.

To build locally you need JDK 17 and the Android SDK (platform 35):

```bash
./gradlew assembleRelease
```

The APK ends up in `app/build/outputs/apk/release/`.

## Disclaimer

Changing your status with a user token is against Discord's Terms of Service. Use at your own risk.

## Credits

- [LyricsStatus](https://github.com/OvalQuilter/lyrics-status) by OvalQuilter
- [Poweramp API](https://github.com/maxmpz/powerampapi) by Maksim Petrov
- [LRCLIB](https://lrclib.net)

Licensed under [GPL-2.0](LICENSE).
