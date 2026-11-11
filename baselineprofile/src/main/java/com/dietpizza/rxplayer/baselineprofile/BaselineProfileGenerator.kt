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
 * photo/video viewer (swipe, zoom, video page), Videos folder list + folder, Settings.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Cold start only: this is the startup profile, which drives the dex layout
     * (baselineProfile.dexLayoutOptimization in :app). Keep it to what really runs at launch;
     * with every journey in it, the "startup" set was most of the app (media3 included) and R8
     * emitted unverifiable code for MediaCodecVideoRenderer.
     */
    @Test
    fun startup() = rule.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
    ) {
        grantStorageAccess()
        startToGallery()
    }

    /** Everything after launch: baseline profile only (precompiled, not laid out as startup). */
    @Test
    fun journeys() = rule.collect(
        packageName = targetPackage,
        includeInStartupProfile = false,
    ) {
        grantStorageAccess()
        startToGallery()
        scrollAlbums()
        openAlbumAndFling()
        openViewer()
        browseVideos()
        openSettings()
    }
}
