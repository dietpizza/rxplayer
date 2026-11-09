plugins {
    alias(libs.plugins.rx.android.library)
    alias(libs.plugins.rx.hilt)
}
android { namespace = "com.dietpizza.rxplayer.core.database" }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
}
