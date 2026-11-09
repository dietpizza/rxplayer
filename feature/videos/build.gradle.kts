plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.videos" }
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    testImplementation(libs.kotlinx.coroutines.test)
}
