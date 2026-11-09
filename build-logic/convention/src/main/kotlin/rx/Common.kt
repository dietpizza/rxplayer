package rx

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 34

internal fun Project.configureKotlinAndroid(common: CommonExtension) {
    common.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.addAll(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
        }
    }
}

internal fun Project.configureCompose(common: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    common.buildFeatures.compose = true
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        // Compose compiler stability reports + metrics, off by default:
        //   ./gradlew :feature:gallery:compileReleaseKotlin -Prx.composeReports=true
        // Output: <module>/build/compose-reports (*-classes.txt, *-composables.txt/csv) and
        // <module>/build/compose-metrics (*-module.json).
        if (providers.gradleProperty("rx.composeReports").orNull.toBoolean()) {
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        }
        // Types the compiler can't infer as stable but that we treat as immutable (see file).
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("build-logic/compose-stability.conf"),
        )
    }
    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui"))
        add("implementation", libs.lib("androidx-compose-ui-graphics"))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("implementation", libs.lib("androidx-compose-material3"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }
}
