// Generates the release Baseline Profile from real app journeys (#312). Its APKs never ship.
plugins {
    id("com.android.test")
    // Consumer, app target and producer wiring: generateReleaseBaselineProfile and the nonMinifiedRelease app.
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.mateux.caparao.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // Same floor as the app; the profile generator runs on the attached emulator or device.
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    // The profile is generated on the emulator (#312); the plugin skips emulators by default.
    skipBenchmarksOnEmulator = false
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
