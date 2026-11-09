plugins {
    alias(libs.plugins.rx.android.library)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.datastore" }
dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
}
