plugins {
    alias(libs.plugins.rx.android.library)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.data" }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:media"))
    testImplementation(libs.kotlinx.coroutines.test)
}
