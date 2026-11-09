plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.onboarding" }
dependencies {
    // rememberLauncherForActivityResult for the POST_NOTIFICATIONS request.
    implementation(libs.androidx.activity.compose)
}
