plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.search" }
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    testImplementation(libs.kotlinx.coroutines.test)
}
