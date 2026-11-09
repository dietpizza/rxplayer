package com.dietpizza.rxplayer.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to the first Gallery frame (plan §10 budget: < 500 ms), with and without the profile.
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.dietpizza.rxplayer.baselineprofile.StartupBenchmark
 * ```
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { grantStorageAccess() },
    ) {
        startToGallery()
    }
}
