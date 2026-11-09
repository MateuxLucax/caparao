buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin); this pins a newer Kotlin than its default.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Room's annotation processor (#88, 2026-10 amendment).
    id("com.google.devtools.ksp") version "2.3.12" apply false
    // Profile generation and its benchmarkRelease variant (#312).
    id("androidx.baselineprofile") version "1.5.0" apply false
}
