plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.network" }
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:netfs"))
    testImplementation(libs.kotlinx.coroutines.test)
}
