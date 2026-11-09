# Rx Player — Plan

> Living document. **✅** = decided with you · *(proposed)* = my default, push back if wrong · **❓** = still open (see §14).

## 0. Vision

A local-first media app that is **one thing, not two**: a Material 3 Expressive gallery for photos *and* videos, where a video in the gallery plays inline and morphs — same player instance, no rebuffer — into a full-screen player with MX-grade controls and smoothness.

Two non-negotiables:
1. **The player feels better than MX.** Gestures are instant, seeking shows frames, and nothing janks.
2. **It never fights other apps for audio.** YouTube in PiP, Spotify, etc. keep playing. Rx Player mixes; it never takes audio focus. Mute and unmute is a first-class control everywhere.

### Project facts (from the existing template)
- Package `com.dietpizza.rxplayer`, single `:app` module today (will be split, see §3)
- minSdk 34 (Android 14), target/compile SDK 37, AGP 9.3, Kotlin 2.2, Compose BOM 2026.02
- **Distribution: personal / sideload ✅.** No Play policy constraints, so we can use `MANAGE_EXTERNAL_STORAGE`, `SYSTEM_ALERT_WINDOW`, and so on.

---

## 1. Scope

| Area | v1 | v2 | Not planned |
|---|---|---|---|
| Local photos: full gallery ✅ | ● | | |
| Local videos: MX-style library and player ✅ | ● | | |
| Network: SMB, WebDAV, FTP, SFTP ✅ | ● | | |
| Network: direct URL / HLS / DASH, DLNA | | ? | |
| Music tab (library, queue, now playing) ✅ | | ● | |
| File ops: rename, move, copy, delete ✅ | ● | | |
| Trash, locked vault, hidden folders | | ? | |
| Open video/image files from other apps (`ACTION_VIEW`) ✅ | ● | | |
| Player screenshot ✅ | ● | | |
| Sleep timer, A-B repeat, frame step | | | ● |
| Photo editing | | | ● |
| Online subtitle search, libass | | | ● |
| Floating popup player, background-audio toggle | | | ● |
| Per-file player prefs (remembered tracks, zoom, speed) | | ? | |
| ML/semantic search | | | ● |

---

## 2. Tech stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose, **Material 3 Expressive** (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, shape morphing, `LoadingIndicator`, `FloatingToolbar`, button groups) |
| Navigation | **One Activity per full-screen destination ✅** (no Compose navigation library). The OS activity transitions and predictive back come for free. See §3.2a |
| Playback | **Media3 ExoPlayer ✅** + FFmpeg audio decoder extension (AC3/EAC3/DTS/TrueHD/etc). Use the prebuilt `org.jellyfin.media3:media3-ffmpeg-decoder`, so we don't have to build FFmpeg ourselves. Also `media3-ui-compose` (`PlayerSurface`) and `media3-session` |
| DI | **Hilt ✅** |
| DB | Room + Flow |
| Prefs | DataStore (Proto) |
| Images | Coil 3 (+ `coil-video`, + `AnimatedImageDecoder`) |
| Background work | WorkManager (thumbnail pre-gen, seek-sprite gen, reconcile) |
| Network FS | `smbj` (SMB2/3), `sshj` (SFTP), Apache `commons-net` (FTP/FTPS), OkHttp for WebDAV (PROPFIND) |
| Secrets | Android Keystore AES-GCM key; ciphertext stored in Room ✅ |
| Perf | Baseline Profile, R8 full mode |

---

## 3. Architecture

### 3.1 Modules ✅ multi-module
```
:app                      – MainActivity (permission gate + tabs), Hilt root, MediaSessionService
:build-logic              – convention plugins (android-library, compose, hilt, feature)

:core:model               – pure Kotlin data classes (MediaItem, Folder, Album, ServerConfig…)
:core:common              – dispatchers, Result types, utils
:core:designsystem        – theme, expressive components, icons, motion tokens
:core:ui                  – RxActivity base (theme, edge-to-edge, no-flash first frame) + shared composables:
                            MediaGrid, Thumbnail, FastScroller, SelectionBar
:core:database            – Room DB, DAOs, migrations
:core:datastore           – Preferences DataStore for settings
:core:media               – MediaStore source, ContentObserver, file ops, EXIF
:core:netfs               – SMB/WebDAV/FTP/SFTP clients + Media3 DataSources
:core:player              – PlaybackController (single ExoPlayer), seek-preview engine,
                            gesture state machine, PiP helper, auto-pause
:core:data                – repositories joining media + database + netfs

:feature:onboarding       – permission onboarding
:feature:gallery          – Albums grid, album detail grid
:feature:viewer           – horizontal pager: photos + inline video
:feature:player           – full-screen player UI
:feature:videos           – MX-style folder list, video list, playlists, history
:feature:network          – server list, add/edit server, remote browser
:feature:search
:feature:settings
(:feature:music           – v2)
```

### 3.2 Patterns
- **UDF/MVI-lite:** each screen's ViewModel exposes one `StateFlow<UiState>` and takes `onEvent(Event)`. One-off effects go through a `Channel`.
- **Source of truth split:**
  - **MediaStore** says *what exists locally* (it is fast, the system keeps it indexed, and it already has thumbnails).
  - **Room** holds *what we know about it*: resume position, watched state, favourites, custom albums, playlists, history, servers.
  - Repositories join the two in memory using a stable `mediaKey` (§7.1). *(proposed)* v1 does **not** mirror MediaStore into Room, so there is no second index to keep in sync. We revisit this only if the folder-list queries turn out slow.
- **One player, many surfaces.** `PlaybackController` (`@Singleton`) owns the single `ExoPlayer`. The inline viewer, the full player and PiP all attach to the same instance. `MediaSessionService` wraps it for the notification and PiP controls.

### 3.2a Navigation: Activities, not Compose navigation ✅
- **Every full-screen destination is its own `Activity`**, opened with `startActivity` and closed with `finish()`. We use the system's own activity open/close animations and its cross-activity predictive back. We don't override transitions, so the app feels native on every Android version and OEM skin.
- **Tabs are not navigation.** Gallery · Videos · Network live inside `MainActivity` as plain state (`rememberSaveable` + `SaveableStateHolder` to keep each tab's scroll position). Switching tabs is an instant swap with no animation (§9). Back from Videos or Network goes to Gallery; back from Gallery leaves the app.
- **`RxActivity`** (`:core:ui`) is the base class for every activity. It handles edge-to-edge, `RxTheme` driven live by `AppearanceRepository` (so a settings change restyles every open activity), system-bar icon contrast, and holding the first frame until the theme is known.
- **Feature modules own their activities** and declare them in their own `AndroidManifest.xml`. Each exposes `XActivity.intent(context, args)`. Features never depend on each other: a screen gets an `onOpenX` callback, and the caller (usually `:app`) builds the intent. Arguments are passed as intent extras (ids, mediaKeys, paths), never whole objects.
- **Activity map:**

  | Activity | Module | Contains |
  |---|---|---|
  | `MainActivity` | :app | onboarding gate, the three tabs |
  | `SettingsActivity` ✅ (M0) | :feature:settings | settings |
  | `AlbumActivity` | :feature:gallery | album detail grid |
  | `FolderActivity` | :feature:videos | folder video list (also used for playlists) |
  | `PlayerActivity` | :feature:player | **viewer pager + full player in one activity** (see below), PiP, `ACTION_VIEW` target |
  | `ServerActivity` / `RemoteBrowserActivity` | :feature:network | add/edit server, remote file browser |
  | `SearchActivity` | :feature:search | search |
- **Why the viewer and the player share one activity:** the inline→full morph (§4.1) must hand the same `SurfaceView` and `ExoPlayer` across without rebuffering, which a cross-activity transition can't do cleanly. So that morph is a state change inside `PlayerActivity`, not navigation. A dedicated player activity is also what PiP wants (`supportsPictureInPicture`, `resizeableActivity`). Otherwise PiP would shrink the whole app.
- Opening a viewer from a grid uses the standard activity transition, not a custom shared-element zoom from the thumbnail, so it stays native (decided here; revisit only if it feels flat).

### 3.3 Audio policy ✅ (important, so it gets its own section)
- `ExoPlayer.setAudioAttributes(attrs, handleAudioFocus = false)` **always**. We never request audio focus, in inline playback or in the full player, so other apps keep playing and the audio mixes.
- Mute and unmute is just `player.volume = 0f / 1f`. It does not touch focus.
- **Mute is one global, sticky state ✅.** It's shared by inline gallery videos and the full player, and it's saved in DataStore so it survives restarts. A fresh install starts muted.
- Since focus isn't doing it for us, we auto-pause ourselves ✅:
  - `ACTION_AUDIO_BECOMING_NOISY` (headphones unplugged) → pause
  - `AudioManager.addOnModeChangedListener` (API 31+, no permission needed) → pause on `MODE_IN_CALL` / `MODE_IN_COMMUNICATION`, and optionally resume when the call ends
- There is no system volume-swipe gesture (§6.2), so hardware volume keys control the stream as normal.

### 3.4 Storage access
- `MANAGE_EXTERNAL_STORAGE` (sideload) for rename, move, copy and delete through `java.io.File`. Each op is followed by `MediaScannerConnection.scanFile` so MediaStore catches up.
- MediaStore is still used for reading the index and the thumbnails.
- `POST_NOTIFICATIONS` for the media notification.

---

## 4. Navigation & screens

**Shell:** an expressive bottom navigation bar on phones (NavigationRail in landscape and on tablets) with tabs **Gallery · Videos · Network**. The Music tab is hidden until v2. Search and Settings live in the top app bar of each tab.

| # | Screen | Notes |
|---|---|---|
| 1 | Onboarding / permissions | All-files access + notifications, with an expressive illustration and a single CTA |
| 2 | **Gallery → Albums** ✅ | Grid of folders/albums (photos + videos). Pinned row on top: Favourites, custom albums, "All videos", "Camera" |
| 3 | Album detail | Media grid, pinch to change column count (2–6), sticky date headers, drag-to-multiselect, fast scroller with date bubble |
| 4 | **Viewer** | Horizontal pager across the album. Photos: zoom/pan, double-tap zoom. Videos: **inline autoplay, muted**, with slim controls (play/pause, mute, scrub, expand) |
| 5 | **Full player** | MX-grade (§6). Reached from the viewer (morph), from the Videos tab, from Network, or from an external `ACTION_VIEW` intent |
| 6 | **Videos → Folders** ✅ | MX-style list: folder name, video count, total size, a NEW badge for unplayed videos. Segmented header: **Folders · Playlists · History** |
| 7 | Folder video list | List/grid toggle. Thumbnail, duration, resume progress bar, size, resolution chip, NEW dot |
| 8 | Playlists / History | User playlists (videos); history = recently played, with resume |
| 9 | **Network → Servers** | Saved servers (SMB/WebDAV/FTP/SFTP), plus an add/edit sheet with a test-connection button |
| 10 | Remote browser | Breadcrumb file browser. Tap a video to open the full player, tap an image to open the viewer. **Rename + delete ✅** (each with a confirm). No move/copy |
| 11 | Search | Filename + folder search with filter chips (type, date range, size, duration) ✅ |
| 12 | Info sheet | Swipe up in the viewer: EXIF, camera/lens/ISO, resolution, codec, path, size. **No location ✅** |
| 13 | File-op flows | Rename dialog, move/copy folder picker, **delete = confirm dialog → permanent ✅** |
| 14 | Settings | §8 |
| 15 | External open ✅ | `ACTION_VIEW` for `video/*` and `image/*` (file, content URIs). A video opens straight into the full player; an image opens in a single-item viewer. No resume/history for content URIs we can't map to a path |

### 4.1 The blend: viewer ↔ full player ✅
- When the pager settles on a video page, the controller attaches the shared ExoPlayer to that page's surface and prepares and plays it **muted** without taking focus. The neighbouring pages show poster frames only.
- An expand tap, rotating to landscape, or an upward fling **morphs** the inline surface into the full player with a shared-element bounds transform. The player is never re-prepared.
- In the full player, **swipe down** shrinks it back into the viewer page ✅. If `PlayerActivity` was opened straight into the full player (from the Videos tab, Network, or `ACTION_VIEW`), swipe down dismisses it: the video shrinks and fades, then `finish()` hands over to the system close transition.
- All of this happens inside `PlayerActivity` (§3.2a): the viewer and the full player are two states of one screen, not two destinations.
- **Risk:** animating a `SurfaceView` during the shared-element transition. Plan: use `PlayerSurface(SURFACE_TYPE_SURFACE_VIEW)`, keep a single surface hoisted with `movableContentOf`, and animate its bounds. If it artifacts, fall back to `TextureView` during the transition only. This needs an early spike (M1).

---

## 5. Gallery behaviour
- Albums = filesystem folders that contain media, plus virtual albums (Favourites, custom albums). Sort: name, date modified, count. Custom albums are DB-only and never move files ✅.
- Album detail sort: date taken (default), name, size, and asc/desc.
- Multiselect actions: share, favourite, add to album, move, copy, rename (single item), delete.
- **Photo viewer extras ✅**
  - **Ultra HDR / HDR:** decode gainmaps through `ImageDecoder` (API 34) and switch `window.colorMode` to `COLOR_MODE_HDR` while an HDR item is on screen. HDR video renders through the SurfaceView. *Risk:* we need to check that Coil keeps the gainmap. If not, we decode HDR items ourselves.
  - **Animated:** GIF, WebP, HEIC and AVIF play through `AnimatedImageDecoder` (ImageDecoder). Animated AVIF support depends on the device, so we fall back to the first frame.
  - **Info sheet** with EXIF and file details, no location ✅.
- Not in v1: motion photos, editing.

---

## 6. Full player spec

### 6.1 Engine ✅
Media3 ExoPlayer, with the FFmpeg extension for audio only and the renderer mode set to prefer the extension. Video decoding is hardware via MediaCodec. If a file fails to play, we show a clear error naming the codec rather than failing silently. A software video fallback (libmpv) is v2 at most.

### 6.2 Gestures ✅ (opinionated, a few configurable)
| Gesture | Action | Configurable |
|---|---|---|
| Single tap | Show/hide controls (immediately; a second tap within the double-tap window switches to play/pause) | — |
| **Double tap anywhere** | **Play / pause** with a centre icon pop | on/off |
| **Horizontal swipe** | **Live seek ✅**: the video itself jumps as you drag (`SeekParameters.CLOSEST_SYNC` while dragging, throttled to one seek in flight at a time). Overlay shows `+0:42 / 12:31`. Release does one exact seek. No thumbnail bubble | sensitivity |
| Long press | **2× speed while held** (toast "2× ▶▶"), release returns to the previous speed | on/off, speed (1.5/2/3×) |
| Pinch | Zoom; snaps to Fit and Fill/crop, with a % indicator | on/off |
| **Swipe down** | Minimise back to the viewer or list (§4.1) | on/off |
| Vertical swipe left/right | **Nothing.** No brightness or volume gestures ✅ | — |

Gesture arbitration lives in a single state machine in `:core:player` (pointerInput with touch slop and axis lock). This gives no conflicts between horizontal seek, swipe-down and pinch. The seek step applies to the ⏪/⏩ buttons and to keyboard/remote keys.

### 6.3 Controls layout *(proposed)*
- **Top bar:** back · title (marquee) · 🔇 mute toggle (always visible) · audio track · subtitles · ⋮ (speed, aspect, orientation, info, PiP)
- **Centre:** ⏮ · ⏪ · **▶/⏸** (expressive shape-morphing button) · ⏩ · ⏭
- **Bottom:** seek bar (with a preview bubble, chapter ticks when present) · elapsed / tap-to-toggle remaining · 🔒 lock · aspect cycle (Fit → Fill → Crop → 100% → Stretch) · rotate-lock
- Controls auto-hide after 3s. Status and nav bars are hidden immersively. The display cutout is used in landscape.
- **Lock:** hides everything except a lock button that fades in on tap.

### 6.4 Features
- **Seek thumbnail preview ✅, only when dragging the bottom seek bar** (swipe-seek is live, see §6.2)
  - Local files: when a file opens, a background job generates a low-res sprite (one frame every N seconds, about 160px wide) progressively with `MediaMetadataRetriever.getScaledFrameAtTime(OPTION_CLOSEST_SYNC)`, cached on disk keyed by `mediaKey + size + mtime`. While scrubbing, we show the nearest frame that's ready.
  - Network files: generated lazily, with frames fetched on demand at a lower density.
- **Resume ✅:** by default, **ask each time** with a Resume `12:31` / Start over dialog (MX style). The setting can change it to auto or never.
- Speed 0.25–4×, aspect modes, audio track picker, subtitle track picker
- **Subtitles: basic ✅.** Embedded tracks, auto-load of a same-name sidecar (`.srt`, `.ass`, `.vtt`), manual load from a file, size setting, on/off. ASS styling is flattened (Media3's limit, which we accept).
- **Orientation ✅** follows the video's aspect (landscape video → landscape) and has a rotate-lock button
- **Auto-PiP ✅** on home/swipe-up, with a settings toggle. PiP actions: play/pause, ±seek. The PiP aspect matches the video.
- Playlist context: "next/prev" follows the source (folder order, playlist, or album) and auto-plays the next item (setting)
- **Screenshot ✅:** a button in the ⋮ sheet. It grabs the current frame at full resolution (`PixelCopy` from the SurfaceView) and saves it to `Pictures/Rx Player/`, which shows up as an album in Gallery. A snackbar offers View / Share.
- **Leaving the player ✅:** if auto-PiP doesn't kick in (it's off, the screen is locked, or you navigate away), playback **pauses** and resumes when you return. There's no background play, and the media notification exists only during PiP.
- Keep the screen on while playing
- HDR video passthrough

---

## 7. Data model (Room)

### 7.1 Identity
`mediaKey: String`
- Local files: `file://<absolute path>`. MediaStore IDs are not stable across rescans or moves; paths are, and we update keys ourselves on in-app rename/move.
- Network files: `<scheme>://<serverId>/<path>`, e.g. `smb://3/Movies/x.mkv`
- We also keep `size` and `mtime` to re-match a file that was renamed outside the app *(proposed best-effort reconcile)*.

### 7.2 Tables
```
playback_state                     -- resume + watched + history (one row per media)
  media_key       TEXT PK
  title           TEXT
  source          TEXT  -- LOCAL | SMB | WEBDAV | FTP | SFTP
  position_ms     INTEGER
  duration_ms     INTEGER
  completed       INTEGER  -- watched flag (>= 95% or reached end)
  play_count      INTEGER
  last_played_at  INTEGER  -- History = WHERE source = 'LOCAL' ORDER BY last_played_at DESC
  size_bytes, mtime  INTEGER  -- re-match fingerprint

favourite
  media_key  TEXT PK
  added_at   INTEGER

album                              -- custom (virtual) albums
  id          INTEGER PK
  name        TEXT
  cover_key   TEXT NULL
  created_at  INTEGER
  sort_index  INTEGER

album_item
  album_id   INTEGER FK → album (CASCADE)
  media_key  TEXT
  added_at   INTEGER
  PK(album_id, media_key)

playlist
  id, name, created_at, updated_at

playlist_item
  playlist_id  FK → playlist (CASCADE)
  media_key    TEXT
  position     INTEGER
  PK(playlist_id, position)

network_server
  id           INTEGER PK
  protocol     TEXT  -- SMB | WEBDAV | FTP | FTPS | SFTP
  name, host   TEXT
  port         INTEGER
  base_path    TEXT
  username     TEXT NULL
  secret_ct    BLOB NULL   -- AES-GCM ciphertext (Keystore key)
  secret_iv    BLOB NULL
  options_json TEXT        -- domain, SMB version, passive FTP, TLS, host-key fingerprint
  last_used_at INTEGER
```
- NEW badge = no `playback_state` row for the file.
- Network files keep resume position and watched state, but **never appear in History ✅**.
- Rename/move inside the app runs one transaction that rewrites `media_key` in every table.
- Migrations: exported schemas plus `AutoMigration` where possible. Each schema change gets a test.

### 7.3 Not in Room
- Settings → Proto DataStore
- Thumbnails and seek sprites → disk cache (`cacheDir/thumbs`, `cacheDir/sprites`) with an LRU size cap
- Media index → MediaStore (live)

---

## 8. Settings *(proposed. Opinionated defaults, a short list on purpose ✅)*

**Appearance**
- Theme: System / Light / Dark
- Dynamic colour (default on) ✅ · Pure black (AMOLED) in dark mode ✅
- Brand seed colour **coral red ✅** for when dynamic colour is off

**Library**
- Default grid columns · Video list view: list/grid · Default sort
- Show `.nomedia` and hidden folders: **off by default ✅**

**Player**
- Seek step: 5 / **10** / 15 / 30 s ✅
- Swipe-seek sensitivity (slider) ✅
- Double-tap to play/pause: on ✅ · Long-press speed: on, 2× ✅ · Pinch to zoom: on · Swipe down to minimise: on
- Seek thumbnail preview: on
- Resume: **ask** ✅ / auto / never
- Auto-play next: on
- Auto-PiP: on ✅
- Inline videos in the gallery: autoplay (on). Mute is a global sticky toggle, not a setting ✅
- Pause on headphone unplug ✅ · Pause during calls ✅ (resume after the call: on)
- Default orientation: Auto by aspect ✅ / Sensor / Landscape

**Subtitles**
- Auto-load sidecar files · Text size · Background box on/off

**Network**
- Manage servers · Connection timeout

**Storage**
- Clear thumbnail and sprite cache (shows its size) · Clear history

**About**
- Version, licences

---

## 9. Visual design ✅
- Material 3 Expressive throughout: expressive motion scheme (springy), shape morphing on the play/pause button and the FAB, a wavy `LinearWavyProgressIndicator` for the inline resume bar, and button groups in the player's ⋮ sheet
- Dynamic colour by default, with an AMOLED toggle. **The player is always pure black** whatever the theme.
- Edge-to-edge everywhere. Predictive back with an expressive animation.
- **Switching bottom-nav tabs has no animation ✅.** Tapping an unselected tab swaps the content instantly: no fade, scale, slide or stack effect. Every other screen change is an Activity, so it gets the **system's** activity transition and predictive back (§3.2a). There are no custom page transitions.
- Seed colour: **coral red ✅** (e.g. `#FF5A4E`, tuned in M0). The icon is a coral play glyph.
- Typography: **system default font ✅**, using the M3 type scale with emphasized styles for headers
- **Adaptive basics ✅:** NavigationRail on wide screens, grid columns follow width, the player handles fold and resize. No two-pane layouts in v1.

---

## 10. Performance budgets *(proposed)*
| Metric | Target |
|---|---|
| Cold start → first Albums frame | < 500 ms (mid-range, baseline profile) |
| Grid scroll jank | 0 dropped frames at 120 Hz over a 2k-item album |
| Tap thumbnail → first video frame | < 300 ms local |
| Inline → full morph | 60/120 fps, no rebuffer, no black flash |
| Seek preview while scrubbing | frame bubble updates ≥ 15 fps once the sprite is ready |
| Gesture latency | first visual response on the next frame |

Tooling: JankStats in debug builds, Compose compiler stability reports, and Perfetto traces by hand. No macrobenchmark suite in v1 (§10a). The baseline profile is still generated.

## 10b. Repo & release ✅
- `git init` at the start of M0, with a small commit per step and a `.gitignore` covering `local.properties`, keystores and build outputs. You push wherever you like.
- Releases are a **manually installed signed APK**. The keystore stays outside the repo, and the signing config reads from `~/.gradle/gradle.properties`. No in-app updater.

## 10a. Testing ✅
- **Unit tests only:** ViewModels, repositories, the gesture state machine, the mediaKey rename/move rewrite, Keystore secret round-trip (Robolectric), Room migrations.
- No screenshot, instrumented or benchmark suites in v1.

---

## 11. Milestones
| # | Milestone | Done when |
|---|---|---|
| **M0** | Foundations | build-logic, module skeleton, Hilt, theme/design system, nav shell with three tabs, permissions onboarding |
| **M1** | Core player + spike | `PlaybackController` + MediaSessionService, full player with controls, swipe-seek, double-tap pause, mute, no-focus audio policy, auto-pause, resume. **Spike:** SurfaceView shared-element morph |
| **M2** | Videos tab | MX folder list, video list (list/grid), NEW badges, resume bars, auto-next |
| **M3** | Player polish | seek sprites, long-press 2×, pinch zoom, lock, aspect, tracks, subtitles (basic), speed, orientation, auto-PiP, swipe-down minimise |
| **M4** | Gallery | Albums, album detail grid (pinch columns, multiselect), viewer pager, **inline video + morph**, HDR, animated formats, info sheet |
| **M5** | Data features | favourites, custom albums, playlists, history, file ops (rename/move/copy/delete), search |
| **M6** | Network | server CRUD with encrypted secrets, SMB/WebDAV/FTP/SFTP browsers, seekable Media3 DataSources, sidecar subs over the network |
| **M7** | Perf & polish | baseline profile, manual perf pass against §10, adaptive basics, accessibility pass, settings complete |
| v2 | Music tab | library, queue, now playing, mini-player |

---

## 12. Risks
1. **SurfaceView morph** (inline → full) can show artifacts, so it gets a spike in M1, with a TextureView fallback.
2. **Seek sprites on network files** are slow over SMB/FTP, so they're lazy and low-density, and the setting can turn them off.
3. **The network DataSources must support random access.** SMB (smbj) and SFTP (sshj) have it. FTP needs `REST` offsets, which some servers handle badly. WebDAV uses HTTP Range.
4. **Coil and gainmaps:** the Ultra HDR path may need a custom decoder.
5. **The no-focus policy** means we have to own every interruption case ourselves (calls, unplug, alarms?).

## 13. Decision log
| # | Decision | Date |
|---|---|---|
| 1 | Personal/sideload distribution; all-files access OK | 2026-11-09 |
| 2 | Tabs: Gallery (photos+videos, albums-only) · Videos (MX folder list) · Network. Music deferred to v2 | 2026-11-09 |
| 3 | Engine: Media3 ExoPlayer + FFmpeg audio ext | 2026-11-09 |
| 4 | Network v1: SMB, WebDAV, FTP, SFTP (no URL/DLNA) | 2026-11-09 |
| 5 | Inline muted autoplay in viewer, morphs into full player (same instance) | 2026-11-09 |
| 6 | **Never request audio focus**, inline or full; mute toggle everywhere; self-managed pause on unplug/calls | 2026-11-09 |
| 7 | Gestures: swipe-seek, double-tap = play/pause, long-press 2×, pinch, swipe-down minimise, seek thumbnails. **No brightness/volume gestures** | 2026-11-09 |
| 8 | Config: opinionated; seek step + swipe sensitivity + key toggles only | 2026-11-09 |
| 9 | Subtitles: basic (embedded, sidecar, size) | 2026-11-09 |
| 10 | Auto-PiP with settings toggle; no popup player / background-audio toggle | 2026-11-09 |
| 11 | Multi-module + Hilt; Room + DataStore; Keystore-encrypted server secrets | 2026-11-09 |
| 12 | Room stores: resume/watched, favourites, custom albums, history, playlists. No per-file player prefs | 2026-11-09 |
| 13 | File ops v1: rename/move/copy/delete (no trash / vault / hidden folders) | 2026-11-09 |
| 14 | Photo viewer: Ultra HDR, animated formats, EXIF+map info sheet | 2026-11-09 |
| 15 | Thumbnails: MediaStore first, own disk cache fallback | 2026-11-09 |
| 16 | Search: filename + folder with filters | 2026-11-09 |
| 17 | Visuals: dynamic colour + AMOLED toggle; player always black | 2026-11-09 |
| 18 | Orientation: auto by video aspect + rotate lock | 2026-11-09 |
| 19 | Info sheet has no location/map | 2026-11-09 |
| 20 | Delete: confirm dialog, then permanent | 2026-11-09 |
| 21 | Mute: one global sticky state, persisted | 2026-11-09 |
| 22 | Handle `ACTION_VIEW` for videos + images from other apps | 2026-11-09 |
| 23 | Network files: resume yes, history no | 2026-11-09 |
| 24 | MX extras: screenshot only (to Pictures/Rx Player) | 2026-11-09 |
| 25 | Adaptive basics (rail, width-aware grids), no two-pane | 2026-11-09 |
| 26 | Testing: unit tests only | 2026-11-09 |
| 27 | Brand: coral red seed, system default font | 2026-11-09 |
| 28 | Leaving player without PiP → pause; no background play | 2026-11-09 |
| 29 | Network tab: browse/play + rename/delete (no move/copy) | 2026-11-09 |
| 30 | Resume default: ask each time | 2026-11-09 |
| 31 | Swipe-seek is live; thumbnail bubble only on seek-bar drag | 2026-11-09 |
| 32 | Respect .nomedia by default | 2026-11-09 |
| 33 | git init, commit per step; manual signed APK releases | 2026-11-09 |
| 34 | Tab switches are instant (no transition); settings toggle rows don't highlight, only the switch shows state | 2026-11-09 |
| 35 | **Activities for navigation** (one per full-screen destination, system transitions); Navigation 3 removed; tabs are state in MainActivity; viewer + full player share `PlayerActivity` | 2026-11-09 |

## 14. Open questions
Every product question is answered. The rest get settled during the build:
1. The exact coral hex and the icon artwork (M0)
2. The SurfaceView morph spike outcome. This decides the TextureView fallback (M1)
3. Whether Coil keeps gainmaps, or Ultra HDR needs a custom decoder (M4)
4. The FTP `REST` seeking quality on your actual servers (M6). Live swipe-seek over FTP/SMB may need heavier throttling
