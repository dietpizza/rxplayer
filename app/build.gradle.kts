plugins {
    alias(libs.plugins.rx.android.application)
    alias(libs.plugins.rx.hilt)
}

android {
    namespace = "com.dietpizza.rxplayer"
    defaultConfig {
        applicationId = "com.dietpizza.rxplayer"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            optimization { enable = true }
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:gallery"))
    implementation(project(":feature:videos"))
    implementation(project(":feature:network"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
}
