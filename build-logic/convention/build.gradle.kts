plugins {
    `kotlin-dsl`
}

group = "rx.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") { id = "rx.android.application"; implementationClass = "rx.AndroidApplicationConventionPlugin" }
        register("androidLibrary") { id = "rx.android.library"; implementationClass = "rx.AndroidLibraryConventionPlugin" }
        register("androidLibraryCompose") { id = "rx.android.library.compose"; implementationClass = "rx.AndroidLibraryComposeConventionPlugin" }
        register("androidFeature") { id = "rx.android.feature"; implementationClass = "rx.AndroidFeatureConventionPlugin" }
        register("hilt") { id = "rx.hilt"; implementationClass = "rx.HiltConventionPlugin" }
    }
}
