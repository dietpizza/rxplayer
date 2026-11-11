import java.util.Properties

plugins {
    alias(libs.plugins.rx.android.application)
    alias(libs.plugins.rx.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.dietpizza.rxplayer"
    defaultConfig {
        applicationId = "com.dietpizza.rxplayer"
        versionCode = 2
        versionName = "0.1.4"
    }
    packaging {
        // BouncyCastle (sshj/SFTP in :core:netfs) ships the same license file in three jars.
        resources { excludes += "/META-INF/LICENSE.md" }
    }
    // Release signing comes from keystore.properties in the project root (gitignored; see
    // keystore.properties.example). Without it, release falls back to the debug key so the
    // optimized build still installs locally.
    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }
    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            optimization { enable = true }
            // Debug builds run Compose unoptimized and are not representative of real smoothness.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    // Off for now: with the startup profile covering every journey, R8 emitted unverifiable code
    // for media3's MediaCodecVideoRenderer (VerifyError on opening a video). Re-enable once the
    // startup profile is regenerated from the cold-start-only test, and check a video still opens.
    dexLayoutOptimization = false
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
