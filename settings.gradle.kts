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
// Wear OS companion and what it shares with the app (#107).
include(":wear")
include(":wearlink")
// Baseline Profile generation and Macrobenchmark (#312); neither ships with the app.
include(":baselineprofile")
include(":macrobenchmark")
