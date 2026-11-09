package com.dietpizza.rxplayer.baselineprofile

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
