package com.dietpizza.rxplayer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile (and startup profile) for :app.
 *
 * Run with a phone connected (API 33+ needs no root), with some photos and videos on it:
 *
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * The plugin installs the `nonMinifiedRelease` app + this test APK, runs this class and copies the
 * result to `app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt`, which
 * then get merged into the next `assembleRelease`. Commit those files. The hand-written
 * `app/src/main/baselineProfiles/baseline-prof.txt` stays as a floor (it is merged too).
 *
 * Journeys mirror plan §10: cold start to Gallery, album grid scroll, album open + fling,
 * Videos folder list + folder, Settings.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage,
        // Startup profile drives dex layout (baselineProfile.dexLayoutOptimization in :app).
        includeInStartupProfile = true,
    ) {
        grantStorageAccess()
        startToGallery()
        scrollAlbums()
        openAlbumAndFling()
        browseVideos()
        openSettings()
    }
}
