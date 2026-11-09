plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.ksp)
    alias(libs.plugins.sentry.android)
    // Adds the nonMinifiedRelease and benchmarkRelease variants and the generateReleaseBaselineProfile task (#312).
    alias(libs.plugins.baselineprofile)
}

// CI passes -PversionName=1.2.3 -PversionCode=42; local builds fall back to dev values.
val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

// The release signing job passes the Sentry DSN from the SENTRY_DSN secret; every other build has none, so remote
// diagnostics stay a no-op (#333).
val sentryDsn = (findProperty("sentryDsn") as String?) ?: ""
val sentryAuthToken: String? = System.getenv("SENTRY_AUTH_TOKEN")
// The DSN's path is the project ID, which sentry-cli accepts in place of the slug (#331 has no project-slug secret).
val sentryProjectId = sentryDsn.trimEnd('/').substringAfterLast('/', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

// Release signing comes only from environment variables (CI secrets or the local shell), never from the repo.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.mateuxlucax.caparao"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mateuxlucax.caparao"
        // Android 12+: BLUETOOTH_SCAN with neverForLocation, no location permission (#93).
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Read only by the root package (AppContainer), which hands it to the data-layer implementation (#333).
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
        // True only in debug and the two benchmark variants: gates the scripted scale and the benchmark seed (#312).
        buildConfigField("boolean", "SCRIPTED_SCALE", "false")
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
            buildConfigField("boolean", "SCRIPTED_SCALE", "true")
        }
        release {
            // R8: Compose without shrinking is ~25 MB. Room and WorkManager ship their own keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Only the release key may sign a release: Android refuses an update signed by a different key, so a
            // debug-signed release could force every user to uninstall. Without the keystore the APK stays unsigned
            // (app-release-unsigned.apk); scripts/release-apk.sh builds and signs one locally (#327).
            signingConfig = signingConfigs.findByName("release")
        }
        // Created here, before the Baseline Profile plugin finalizes the DSL, so both variants can carry the
        // debug source set and drive the scripted scale. Neither variant is a release APK (#312).
        create("nonMinifiedRelease") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            isProfileable = true
            buildConfigField("boolean", "SCRIPTED_SCALE", "true")
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            // The plugin normally leaves its benchmark build type unminified; keep the reflection hooks readable.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            isProfileable = true
            buildConfigField("boolean", "SCRIPTED_SCALE", "true")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        // The scripted scale and the benchmark fixture live in src/debug; only these two non-shipping variants include
        // them, so a release APK still has no debug entry points (#312). Both source sets need the directory: the built-in
        // Kotlin compiler does not read Kotlin files from java.srcDirs.
        getByName("nonMinifiedRelease").java.srcDir("src/debug/java")
        getByName("nonMinifiedRelease").kotlin.srcDir("src/debug/java")
        getByName("benchmarkRelease").java.srcDir("src/debug/java")
        getByName("benchmarkRelease").kotlin.srcDir("src/debug/java")
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

    // Robolectric reads the merged resources and assets (the recorded sessions) from the debug build.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's SDK 37 sandbox reaches into the JDK's FileDescriptor internals.
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            // JVM screen tests need this sandbox access; no screenshot tooling remains.
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

// #333: the SDK is wired by hand below, nothing is uploaded without a token, and no bytecode is instrumented.
sentry {
    autoInstallation {
        enabled.set(false)
    }
    telemetry.set(false)
    tracingInstrumentation {
        enabled.set(false)
    }
    includeSourceContext.set(false)
    // Local and PR builds have no token: skip mapping generation/upload instead of failing the build.
    includeProguardMapping.set(sentryAuthToken != null)
    autoUploadProguardMapping.set(sentryAuthToken != null)
    sentryAuthToken?.let { authToken.set(it) }
    System.getenv("SENTRY_ORG")?.takeIf { it.isNotBlank() }?.let { org.set(it) }
    (System.getenv("SENTRY_PROJECT")?.takeIf { it.isNotBlank() } ?: sentryProjectId)?.let { projectName.set(it) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.activity.compose)
    // FileProvider for the export files shared through the share sheet (#76).
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    // Past the BOM: ButtonGroup, LoadingIndicator, the wavy progress and the button shapes arrive in 1.5 (#109).
    implementation(libs.compose.material3)
    // ViewModel factories in Compose (viewModel()) and lifecycle-aware state collection (#95).
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // Brews and their curves (#87, #88).
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    // One upload job per brew, with a network constraint and backoff (#100).
    implementation(libs.work.runtime)
    // Crashes, ANRs and app events behind the Settings opt-in (#333). Core SDK only: no NDK and no
    // auto-installed integrations.
    implementation(libs.sentry.android.core)
    // Installs the Baseline Profile on sideloaded builds, where Play cannot provide it (#312).
    implementation(libs.profileinstaller)

    // The watch companion (#107): Data Layer messages and opening the watch app.
    implementation(project(":wearlink"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.wear.remote.interactions)
    // play-services-wearable pulls Fragment 1.0, whose activity results lint rejects for MainActivity.
    implementation(libs.fragment)

    testImplementation(libs.junit4)
    // android.jar only ships a stub of org.json; JVM tests read the recorded sessions with the real one.
    testImplementation(libs.json)
    // runTest with virtual time for the connection logic, Dispatchers.setMain for ViewModel tests.
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose screens on the JVM (#88, 2026-10-29 amendment): test-only, nothing ships.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.espresso.core)


    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // ui-test-junit4 pulls Espresso 3.5, which calls InputManager.getInstance (removed in recent Android).
    androidTestImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)

    // The journeys that generate the release profile (#312); its APKs never ship.
    baselineProfile(project(":baselineprofile"))
}

// Keep the generated profile in the source tree, next to the release variant it belongs to (#312).
baselineProfile {
    saveInSrc = true
}

// Material 3 Expressive components (#109) are still marked experimental.
kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}
