package com.dietpizza.rxplayer.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * Shared UI journeys for the profile generator and the macrobenchmarks.
 *
 * Selectors are resource-id free: they use `Modifier.testTag`s exposed through
 * `testTagsAsResourceId` (set on every RxActivity's content root), so `By.res(tag)` matches the
 * bare tag. Keep the tag strings in sync with [Tags] in the app's modules.
 */
internal const val TARGET_PACKAGE = "com.dietpizza.rxplayer"

/** Overridable with `-Pandroid.testInstrumentationRunnerArguments.targetAppId=...`. */
internal val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: TARGET_PACKAGE

internal object Tags {
    const val TAB_GALLERY = "tab_gallery"
    const val TAB_VIDEOS = "tab_videos"
    const val GALLERY_ALBUMS = "gallery_albums"
    const val GALLERY_ALBUM = "gallery_album"
    const val ALBUM_GRID = "album_grid"
    const val VIDEOS_FOLDERS = "videos_folders"
    const val VIDEOS_FOLDER = "videos_folder"
    const val FOLDER_LIST = "folder_list"
    const val FOLDER_GRID = "folder_grid"
    const val SETTINGS_LIST = "settings_list"
    const val OPEN_SETTINGS = "open_settings"
    // Not in the app yet; the viewer journey falls back to the selectors below until they exist.
    const val ALBUM_PHOTO = "album_photo"
    const val ALBUM_VIDEO = "album_video"
    const val VIEWER_PAGER = "viewer_pager"
}

/** Content descriptions used as fallbacks (feature/gallery strings album_photo / album_video, VideoPage). */
private object Descs {
    const val PHOTO = "Photo"
    const val VIDEO = "Video"
    const val FULL_SCREEN = "Full screen"
}

private const val TIMEOUT_MS = 5_000L

/**
 * Grants all-files access (the app's only hard permission) so launch lands on the tabs instead of
 * onboarding. `appops` needs no root on API 30+ for the shell user.
 */
internal fun MacrobenchmarkScope.grantStorageAccess() {
    device.executeShellCommand("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
    // Notifications are optional; grant so no system dialog can cover the UI.
    device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
}

/** Cold start and wait until the Gallery tab has drawn its album grid (or its empty state). */
internal fun MacrobenchmarkScope.startToGallery() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.res(Tags.TAB_GALLERY)), TIMEOUT_MS)
    device.wait(Until.hasObject(By.res(Tags.GALLERY_ALBUMS)), TIMEOUT_MS)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollAlbums() {
    val grid = device.findObject(By.res(Tags.GALLERY_ALBUMS)) ?: return
    grid.flingBothWays(device)
}

/** Open the first folder album and fling its grid both ways, then go back. */
internal fun MacrobenchmarkScope.openAlbumAndFling() {
    val album = device.waitFor(By.res(Tags.GALLERY_ALBUM)) ?: return
    album.click()
    val grid = device.waitFor(By.res(Tags.ALBUM_GRID)) ?: run { device.pressBack(); return }
    device.waitForIdle()
    grid.flingBothWays(device)
    device.pressBack()
    device.wait(Until.hasObject(By.res(Tags.GALLERY_ALBUMS)), TIMEOUT_MS)
}

/** Videos tab: scroll the folder list, open the first folder, scroll it, go back. */
internal fun MacrobenchmarkScope.browseVideos() {
    val tab = device.waitFor(By.res(Tags.TAB_VIDEOS)) ?: return
    tab.click()
    val folders = device.waitFor(By.res(Tags.VIDEOS_FOLDERS)) ?: return
    device.waitForIdle()
    folders.flingBothWays(device)
    val folder = device.waitFor(By.res(Tags.VIDEOS_FOLDER)) ?: return
    folder.click()
    // The folder opens as a list or a grid depending on the saved view mode.
    device.waitFor(By.res(Pattern.compile("${Tags.FOLDER_LIST}|${Tags.FOLDER_GRID}")))
        ?.also { device.waitForIdle() }
        ?.flingBothWays(device)
    device.pressBack()
    device.wait(Until.hasObject(By.res(Tags.VIDEOS_FOLDERS)), TIMEOUT_MS)
}

/** Open Settings from the current tab's top bar, scroll it, go back. */
internal fun MacrobenchmarkScope.openSettings() {
    val button = device.waitFor(By.res(Tags.OPEN_SETTINGS)) ?: device.waitFor(By.desc("Settings")) ?: return
    button.click()
    val list = device.waitFor(By.res(Tags.SETTINGS_LIST)) ?: return
    device.waitForIdle()
    list.flingBothWays(device)
    device.pressBack()
}

/**
 * Photo/video viewer (PlayerActivity, viewer mode): open the first album, open a photo, swipe
 * through a few pages, double-tap zoom in/out and pinch in/out, then reach a video page (by
 * swiping, else by opening a video tile) and let it play briefly. Every step degrades to a no-op
 * when the library has no photos or no videos.
 */
internal fun MacrobenchmarkScope.openViewer() {
    val album = device.waitFor(By.res(Tags.GALLERY_ALBUM)) ?: return
    album.click()
    device.waitFor(By.res(Tags.ALBUM_GRID)) ?: run { device.pressBack(); return }
    device.waitForIdle()

    val photo = device.waitFor(By.res(Tags.ALBUM_PHOTO)) ?: device.waitFor(By.desc(Descs.PHOTO))
    if (photo != null) {
        photo.click()
        if (awaitViewer()) {
            zoomAndReset()
            var onVideo = false
            repeat(MAX_VIEWER_SWIPES) { i ->
                if (onVideo) return@repeat
                // Alternate direction after the first few so we stay near loaded pages.
                swipePage(forward = i < MAX_VIEWER_SWIPES - 2)
                onVideo = device.hasObject(By.desc(Descs.FULL_SCREEN))
            }
            if (onVideo) SystemClock.sleep(VIDEO_PLAY_MS)
            device.pressBack()
            device.wait(Until.hasObject(By.res(Tags.ALBUM_GRID)), TIMEOUT_MS)
            if (onVideo) { leaveAlbum(); return }
        }
    }

    // No video reached by swiping: open one straight from the grid if the album has any.
    val video = device.findObject(By.res(Tags.ALBUM_VIDEO)) ?: device.findObject(By.desc(Descs.VIDEO))
    if (video != null) {
        video.click()
        if (awaitViewer()) {
            SystemClock.sleep(VIDEO_PLAY_MS)
            swipePage(forward = true)
            swipePage(forward = false)
            device.pressBack()
            device.wait(Until.hasObject(By.res(Tags.ALBUM_GRID)), TIMEOUT_MS)
        }
    }
    leaveAlbum()
}

private const val MAX_VIEWER_SWIPES = 6
private const val VIDEO_PLAY_MS = 2_000L

private fun MacrobenchmarkScope.leaveAlbum() {
    device.pressBack()
    device.wait(Until.hasObject(By.res(Tags.GALLERY_ALBUMS)), TIMEOUT_MS)
}

/** The viewer has no tag yet; wait for the activity switch, then for its pager when tagged. */
private fun MacrobenchmarkScope.awaitViewer(): Boolean {
    device.wait(Until.gone(By.res(Tags.ALBUM_GRID)), TIMEOUT_MS)
    device.waitFor(By.res(Tags.VIEWER_PAGER))
    device.waitForIdle()
    return !device.hasObject(By.res(Tags.ALBUM_GRID))
}

/** Horizontal swipe through the centre, clear of the edge zones the pager ignores. */
private fun MacrobenchmarkScope.swipePage(forward: Boolean) {
    val w = device.displayWidth
    val y = device.displayHeight / 2
    val (from, to) = if (forward) w * 4 / 5 to w / 5 else w / 5 to w * 4 / 5
    device.swipe(from, y, to, y, 20)
    device.waitForIdle()
}

/** Double-tap in and out, then pinch open and closed (pinch needs the tagged/scrollable pager). */
private fun MacrobenchmarkScope.zoomAndReset() {
    val x = device.displayWidth / 2
    val y = device.displayHeight / 2
    repeat(2) {
        device.click(x, y)
        device.click(x, y)
        device.waitForIdle()
    }
    val target = device.findObject(By.res(Tags.VIEWER_PAGER)) ?: device.findObject(By.scrollable(true))
    target?.run {
        setGestureMargin(device.displayWidth / 5)
        pinchOpen(0.6f)
        device.waitForIdle()
        // Pinching closed clamps at fit scale, so the pager swipes pages again instead of panning.
        pinchClose(0.6f)
        device.waitForIdle()
    }
}

private fun UiDevice.waitFor(selector: BySelector): UiObject2? =
    wait(Until.findObject(selector), TIMEOUT_MS)

/** Keep flings away from the system gesture areas at the edges. */
private fun UiObject2.flingBothWays(device: UiDevice) {
    setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        fling(Direction.DOWN)
        device.waitForIdle()
    }
    fling(Direction.UP)
    device.waitForIdle()
}
