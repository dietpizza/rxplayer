plugins {
    alias(libs.plugins.rx.android.library)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.media" }
dependencies {
    api(project(":core:model"))
    testImplementation(libs.kotlinx.coroutines.test)
}
