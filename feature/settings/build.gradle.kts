plugins { alias(libs.plugins.rx.android.feature) }
android { namespace = "com.dietpizza.rxplayer.feature.settings" }
dependencies { implementation(project(":core:datastore")) }
