<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" alt="RX Player icon" width="112" />

# RX Player

**Your photos and videos, one beautiful player.**

A local-first Android gallery and video player built with Jetpack Compose and Material 3 Expressive.
Browse your photos, watch your videos, and stream from your NAS, all in one app.

![Android 14+](https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer%20%2B%20FFmpeg-FF6F00)
![Version](https://img.shields.io/badge/version-0.1.6-blue)

<!-- TODO: replace with a hero image or a short GIF of the app -->
<img src="docs/screenshots/hero.png" alt="RX Player hero" width="720" />

</div>

---

## Contents

- [Highlights](#highlights)
- [Screenshots](#screenshots)
- [Features](#features)
  - [Gallery](#-gallery)
  - [Photo & video viewer](#-photo--video-viewer)
  - [Videos library](#-videos-library)
  - [Video player](#-video-player)
  - [Network streaming](#-network-streaming)
  - [Search](#-search)
  - [Settings](#-settings)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Building](#building)
- [Permissions](#permissions)

---

## Highlights

- 🖼️ **One app for photos and videos.** A full gallery with albums, favourites and slideshows, plus an MX-style video library.
- 🎬 **A player that feels fast.** Gesture-driven controls, seek-bar frame previews, swipe-to-seek, hold-to-speed-up and pinch-to-zoom.
- 🔊 **Never fights other apps for audio.** RX Player doesn't take audio focus, so Spotify or a YouTube PiP keep playing. Mute is a first-class control.
- 🌐 **Stream from your network.** SMB, SFTP, FTP/FTPS and WebDAV servers, browsed like local folders.
- 🎧 **Plays almost anything.** Media3 ExoPlayer with the FFmpeg audio decoder for AC3, EAC3, DTS, TrueHD and more.
- 📱 **Native Android feel.** One Activity per screen, system transitions, predictive back, dynamic colour and a pure-black theme.

---

## Screenshots

> Screenshots are placeholders. Drop images into [`docs/screenshots/`](docs/screenshots/) using the file names below.

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/gallery.png" alt="Gallery" width="220" /><br/><sub><b>Gallery</b></sub></td>
    <td align="center"><img src="docs/screenshots/album.png" alt="Album" width="220" /><br/><sub><b>Album grid</b></sub></td>
    <td align="center"><img src="docs/screenshots/viewer.png" alt="Photo viewer" width="220" /><br/><sub><b>Photo viewer</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/videos.png" alt="Videos" width="220" /><br/><sub><b>Videos library</b></sub></td>
    <td align="center"><img src="docs/screenshots/folder.png" alt="Video folder" width="220" /><br/><sub><b>Video folder</b></sub></td>
    <td align="center"><img src="docs/screenshots/search.png" alt="Search" width="220" /><br/><sub><b>Search & filters</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/network.png" alt="Network servers" width="220" /><br/><sub><b>Network servers</b></sub></td>
    <td align="center"><img src="docs/screenshots/server.png" alt="Add server" width="220" /><br/><sub><b>Add server</b></sub></td>
    <td align="center"><img src="docs/screenshots/settings.png" alt="Settings" width="220" /><br/><sub><b>Settings</b></sub></td>
  </tr>
</table>

<p align="center">
  <img src="docs/screenshots/player.png" alt="Video player" width="720" /><br/>
  <sub><b>Full-screen player</b></sub>
</p>

<p align="center">
  <img src="docs/screenshots/player-seek-preview.png" alt="Seek preview" width="360" />
  <img src="docs/screenshots/player-tracks.png" alt="Track selection" width="360" /><br/>
  <sub><b>Seek-bar frame preview</b> · <b>Audio & subtitle tracks</b></sub>
</p>

---

## Features

### 🖼️ Gallery

<img src="docs/screenshots/gallery.png" alt="Gallery" width="240" align="right" />

- Every folder with photos or videos shows up as an **album**, plus **Camera**, **All videos** and **Favourites**.
- **Custom albums**: create, rename and delete them, and add or remove items without touching the files.
- **Pin** albums to keep them at the top.
- **Sort** albums by date modified, name, item count or size, ascending or descending.
- Album grids grouped by date (**Today**, **Yesterday**, …) with a **fast scroller**.
- **Pinch to change the grid's column count**, and **drag to select** many items at once.
- **Multi-select actions**: share, favourite, add to album, move, copy, rename and delete.
- **Folder operations**: new folder, rename folder, delete folder.
- **Slideshow** with options for photos, videos and GIFs, shuffle, loop, and seconds per photo.

<br clear="right" />

### 🔍 Photo & video viewer

<img src="docs/screenshots/viewer.png" alt="Viewer" width="240" align="right" />

- Swipe through an album's photos, GIFs and videos in one pager.
- **Pinch and double-tap to zoom** photos.
- Videos **play inline** and **morph into the full player** using the same player instance, with no rebuffering.
- **Details** sheet with EXIF info.
- Favourite, share, delete and start a slideshow straight from the viewer.
- Opens images and videos shared or opened from **other apps** (`ACTION_VIEW`).

<br clear="right" />

### 📂 Videos library

<img src="docs/screenshots/videos.png" alt="Videos" width="240" align="right" />

- MX Player–style list of **video folders**, with pinned folders on top.
- **List or grid** layout, with configurable fields: video count, size, date, path, duration and resolution.
- Sort by name, date, size, length or video count, using **natural ordering** (so `Episode 2` comes before `Episode 10`).
- **New** badges, **watched** marks and **progress** indicators (`42% watched`).
- Favourites, select all, move, copy, rename and delete.
- Watch history, which you can clear while keeping resume positions.

<br clear="right" />

### 🎬 Video player

<img src="docs/screenshots/player.png" alt="Player" width="360" align="right" />

**Gestures**
- Double-tap the left or right side to **skip back or forward** (step length is configurable).
- Double-tap the centre to **play or pause**.
- **Swipe to seek**, with adjustable sensitivity.
- **Press and hold to speed up** (hold speed is configurable).
- **Pinch to zoom** or fill the screen.
- **Swipe down to close** the player.

**Controls**
- **Seek-bar thumbnail previews**, generated in the background and cached.
- **Playback speed**, **aspect modes** (fit, fill, crop, 100%, stretch) and **rotation lock**.
- **Lock controls** to prevent accidental touches.
- **Mute / unmute** without stealing audio focus.
- **Shuffle**, previous / next, and **auto-play the next video** in the folder.
- **Screenshot** the current frame.
- **Picture-in-picture**, with play/pause and skip controls.

**Tracks & subtitles**
- Pick **audio tracks** and **subtitle tracks**, or **load a subtitle from a file**.
- **Sidecar subtitles** (`.srt`, `.ass`, `.ssa`, `.vtt`) with the same name as the video load automatically.
- Adjustable **subtitle text size** and an optional **background box**.

**Smart playback**
- **Resume where you left off**: ask, always or never, with a *Start over* option.
- **Pauses when headphones disconnect** (wired or Bluetooth), so sound never jumps to the speaker.
- **Pauses during calls** and can resume after the call ends.
- **Hardware and Bluetooth media keys** through a MediaSession.
- **Fast seeking in fragmented MP4s** that have no index, using a cached seek table.
- **Media info** panel: codecs, decoders, resolution, frame rate, bitrates and HDR / Dolby Vision.
- Clear error messages for damaged files, unsupported codecs and decoder failures.

<br clear="right" />

### 🌐 Network streaming

<img src="docs/screenshots/network.png" alt="Network" width="240" align="right" />

- Add **SMB (2/3)**, **SFTP**, **FTP / FTPS** and **WebDAV (HTTP/HTTPS)** servers.
- **Test connection** before saving.
- SMB domain and guest access, FTP passive mode and TLS, and SFTP **trust-on-first-use host keys** with a warning when a key changes.
- Passwords are **encrypted with an Android Keystore** AES-GCM key.
- Browse remote folders like local ones, with a media-only filter or **show all files**.
- **Stream videos and view photos** directly from the server.
- **Rename and delete** remote files and folders.

<br clear="right" />

### 🔎 Search

<img src="docs/screenshots/search.png" alt="Search" width="240" align="right" />

- Search photos and videos by **file or folder name**.
- Filters for **type**, **date** (last 7 / 30 days, this year, custom range), **size** and **duration**.

<br clear="right" />

### ⚙️ Settings

<img src="docs/screenshots/settings.png" alt="Settings" width="240" align="right" />

- **Appearance**: system, light or dark theme, **dynamic colour** from your wallpaper, and **pure black** for OLED.
- **Folders & albums**: video layout, default sort, gallery columns, and **show hidden folders** (`.nomedia` / dot-folders).
- **Player gestures**, **playback**, **calls & headphones** and **subtitles**: everything above can be turned on or off.
- **Storage & history**: clear the thumbnail cache and watch history.

<br clear="right" />

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4, JVM 17 |
| UI | Jetpack Compose, Material 3 Expressive |
| Navigation | One `Activity` per screen; tabs are plain state |
| Playback | Media3 ExoPlayer 1.9 + Jellyfin FFmpeg audio decoder, `media3-session`, `media3-ui-compose` |
| DI | Hilt |
| Storage | Room (library data, playback state, servers), DataStore (settings) |
| Images | Coil 3 (+ video frames, animated images), ExifInterface |
| Network FS | smbj (SMB), sshj + BouncyCastle (SFTP), Apache commons-net (FTP/FTPS), OkHttp (WebDAV) |
| Performance | Baseline Profiles, R8, JankStats (debug) |

## Architecture

The project is a multi-module Gradle build with convention plugins in `build-logic/`.

```
:app                 MainActivity (onboarding gate + Gallery / Videos / Network tabs), Hilt root

:core:model          Plain Kotlin models
:core:designsystem   Theme, colours, type, shapes, shared components and icons
:core:ui             RxActivity base class, thumbnails, slideshow sheet
:core:database       Room database, DAOs, migrations
:core:datastore      Settings repositories (appearance, library, player, slideshow, subtitles)
:core:media          MediaStore sources, file operations, hidden-media rules
:core:netfs          SMB / SFTP / FTP / WebDAV file systems, Media3 data sources, secret storage
:core:player         PlaybackController, gesture state machine, seek previews, tracks, subtitles
:core:data           Repositories that join MediaStore, Room and network sources

:feature:onboarding  Permission onboarding
:feature:gallery     Albums and album grids
:feature:videos      Video folders and lists
:feature:player      Viewer pager + full-screen player + PiP
:feature:network     Server list, add/edit server, remote browser
:feature:search      Search with filters
:feature:settings    Settings

:baselineprofile     Baseline Profile generator and startup benchmark
```

- **Activities, not a navigation library.** Each full-screen destination is its own `Activity`, so system transitions and predictive back work out of the box.
- **One player, many surfaces.** A single `ExoPlayer` owned by `PlaybackController` serves the inline viewer, the full player and PiP.
- **MediaStore is the source of truth** for what exists on the device; Room stores what the app knows about it (resume positions, favourites, albums, history, servers).

## Building

**Requirements:** Android Studio (recent), JDK 17+, an Android 14+ (API 34) device or emulator.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:installRelease
```

> Judge smoothness on **release** builds. Debug builds run Compose unoptimised.

### Release signing

Release builds read signing details from `keystore.properties` in the project root (gitignored). Copy the example and fill it in:

```bash
cp keystore.properties.example keystore.properties
```

Without it, release builds fall back to the debug key so they still install locally.

### Tests & profiles

```bash
./gradlew test
```

```bash
./gradlew :app:generateReleaseBaselineProfile
```

The baseline profile needs a connected device.

## Permissions

| Permission | Why |
|---|---|
| All files access (`MANAGE_EXTERNAL_STORAGE`) | **Required.** Browse, play, rename, move, copy and delete your media. |
| Notifications | *Optional.* Playback controls in picture-in-picture. |
| Internet / network state | Streaming from network servers. |

RX Player is a personal, sideloaded app. It isn't published on Google Play.
