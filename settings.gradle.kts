pluginManagement {
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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Orbit"

include(":app")
include(":core-ui")
include(":core-domain")
include(":core-data")
include(":core-security")
include(":core-ai")
include(":core-sync")
include(":feature-today")
include(":feature-agenda")
include(":feature-video-inbox")
include(":feature-attention")
