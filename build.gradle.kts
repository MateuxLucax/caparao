buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin); this pins a newer Kotlin than its default.
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.compose.compiler.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Room's annotation processor (#88, 2026-10 amendment).
    alias(libs.plugins.ksp) apply false
    // R8 mapping upload for the release build only (#333); the app module applies it.
    alias(libs.plugins.sentry.android) apply false
    // Profile generation and its benchmarkRelease variant (#312).
    alias(libs.plugins.baselineprofile) apply false
}
