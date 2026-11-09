plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/**
 * Generates :app's baseline + startup profiles and holds the startup macrobenchmark.
 * Nothing here runs during a normal build; see BaselineProfileGenerator for the commands.
 */
android {
    namespace = "com.dietpizza.rxplayer.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Use the phone that is plugged in; no Gradle-managed emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
