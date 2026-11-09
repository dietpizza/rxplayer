plugins {
    alias(libs.plugins.rx.android.library.compose)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.ui" }
dependencies {
    api(project(":core:designsystem"))
    api(project(":core:datastore"))
    api(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Thumbnails (MediaThumbnail + the shared ImageLoader). `api` so features can pass Coil
    // requests / use AsyncImage with the shared loader if they need more than MediaThumbnail.
    api(libs.coil.compose)
    implementation(libs.coil.video)
}
