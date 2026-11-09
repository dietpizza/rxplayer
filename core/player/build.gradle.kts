plugins {
    alias(libs.plugins.rx.android.library.compose)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.player" }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    api(libs.androidx.media3.ui.compose)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.kotlinx.coroutines.test)
}
