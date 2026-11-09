plugins { alias(libs.plugins.rx.android.library.compose) }
android { namespace = "com.dietpizza.rxplayer.core.designsystem" }
dependencies { api(project(":core:model")) }
