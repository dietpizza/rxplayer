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
    dependencies {
        val bom = libs.lib("androidx-compose-bom")
        add("implementation", platform(bom))
        add("implementation", libs.lib("androidx-compose-ui"))
        add("implementation", libs.lib("androidx-compose-ui-graphics"))
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("implementation", libs.lib("androidx-compose-material3"))
        add("implementation", libs.lib("androidx-compose-material-icons-extended"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
    }
}
