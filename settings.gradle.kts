pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Rx Player"
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:datastore")
include(":core:ui")
include(":core:database")
include(":core:media")
include(":core:data")
include(":core:player")
include(":core:netfs")
include(":feature:onboarding")
include(":feature:gallery")
include(":feature:videos")
include(":feature:network")
include(":feature:settings")
include(":feature:player")
