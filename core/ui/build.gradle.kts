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
}
