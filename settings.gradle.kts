pluginManagement {
    repositories {
        google()
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

rootProject.name = "caparao"
include(":app")
// Wear OS companion and what it shares with the app (docs/adr/0023-watch-companion.md).
include(":wear")
include(":wearlink")
