plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.player" }
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    testImplementation(libs.kotlinx.coroutines.test)
}
