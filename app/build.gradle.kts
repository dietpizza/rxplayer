plugins {
    alias(libs.plugins.rx.android.application)
    alias(libs.plugins.rx.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.dietpizza.rxplayer"
    defaultConfig {
        applicationId = "com.dietpizza.rxplayer"
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        // BouncyCastle (sshj/SFTP in :core:netfs) ships the same license file in three jars.
        resources { excludes += "/META-INF/LICENSE.md" }
    }
    buildTypes {
        release {
            optimization { enable = true }
            // Until a real keystore is configured (plan §10b), sign release with the debug key so
            // the optimized build can be installed locally. Debug builds run Compose unoptimized
            // and are not representative of real smoothness.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// The baseline-profile plugin clones release into nonMinifiedRelease but doesn't undo the
// `optimization` block, so R8 still obfuscated the profiling build and the captured profile was
// full of scrambled names that match nothing in release. Force minification off at variant level.
androidComponents {
    beforeVariants(selector().withBuildType("nonMinifiedRelease")) {
        it.isMinifyEnabled = false
        it.shrinkResources = false
    }
}

baselineProfile {
    // Generation needs a device; never run it implicitly from assembleRelease. Regenerate with
    // ./gradlew :app:generateReleaseBaselineProfile (see BaselineProfileGenerator KDoc).
    automaticGenerationDuringBuild = false
    // Lay out startup classes first in the primary dex (startup profile -> dex layout).
    dexLayoutOptimization = true
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
    // Debug-only frame metrics (JankStats), installed by a debug-manifest provider.
    debugImplementation(libs.androidx.metrics.performance)

    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))
    implementation(project(":core:netfs"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:gallery"))
    implementation(project(":feature:videos"))
    implementation(project(":feature:network"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:player"))
    implementation(project(":feature:search"))

    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
}
