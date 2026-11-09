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
    // R8 mapping upload for the release build only (#333); the app module applies it.
    id("io.sentry.android.gradle") version "6.26.0" apply false
}
