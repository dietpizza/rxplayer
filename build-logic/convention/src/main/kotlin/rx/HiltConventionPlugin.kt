package rx

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
        // With AGP's built-in Kotlin, KSP tasks don't always wait for the upstream modules'
        // classes: on a clean build, :core:ui's KSP could run before :core:datastore compiled
        // and fail with "AppearanceRepository could not be resolved". Make each kspXKotlin
        // task depend on its variant's compile classpath so those artifacts are built first.
        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
            val variant = name.removePrefix("ksp").removeSuffix("Kotlin")
            val classpath = variant.replaceFirstChar { it.lowercase() } + "CompileClasspath"
            configurations.findByName(classpath)?.let { config ->
                dependsOn(
                    config.incoming.artifactView {
                        attributes { attribute(ARTIFACT_TYPE, "android-classes-jar") }
                        lenient(true)
                    }.files,
                )
            }
        }
    }
}

private val ARTIFACT_TYPE: Attribute<String> = Attribute.of("artifactType", String::class.java)
