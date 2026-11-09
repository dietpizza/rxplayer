plugins {
    alias(libs.plugins.rx.android.library)
    alias(libs.plugins.rx.hilt)
}
android {
    namespace = "com.dietpizza.rxplayer.core.netfs"
    // R8 keep rules for smbj / sshj / BouncyCastle / commons-net, applied to :app automatically.
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:player"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.smbj)
    implementation(libs.sshj)
    implementation(libs.commons.net)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
}
