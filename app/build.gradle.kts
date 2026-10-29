plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// CI passes -PversionName=1.2.3 -PversionCode=42; local builds fall back to dev values.
val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

// Release signing comes only from environment variables (CI secrets or the local shell), never from the repo.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.mateuxlucax.caparao"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mateuxlucax.caparao"
        // Android 12+: BLUETOOTH_SCAN with neverForLocation, no location permission (docs/adr/0009-min-sdk-31.md).
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // A separate package so a debug build installs next to the release one instead of over it (their signers differ).
        debug {
            applicationIdSuffix = ".debug"
            // JaCoCo XML for SonarQube: createDebugUnitTestCoverageReport (runs testDebugUnitTest first).
            enableUnitTestCoverage = true
        }
        release {
            // R8: Compose without shrinking is ~25 MB. Room and WorkManager ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Debug builds bundle the recorded sessions so the emulator can replay them (AppContainer.replay).
    sourceSets["debug"].assets.srcDir("src/test/resources")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    // ViewModel factories in Compose (viewModel()) and lifecycle-aware state collection (docs/adr/0011-code-structure.md).
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // Brews and their curves (docs/adr/0003-offline-first.md, docs/adr/0004-minimal-dependencies.md).
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    // One upload job per brew, with a network constraint and backoff (docs/adr/0016-upload-jobs.md).
    implementation("androidx.work:work-runtime:2.12.0")

    testImplementation("junit:junit:4.13.2")
    // android.jar only ships a stub of org.json; JVM tests read the recorded sessions with the real one.
    testImplementation("org.json:json:20250517")
    // runTest with virtual time for the connection logic, Dispatchers.setMain for ViewModel tests.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-junit4 pulls Espresso 3.5, which calls InputManager.getInstance (removed in recent Android).
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
