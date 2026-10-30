// Wear OS companion: mirrors the phone's scale and brew over the Data Layer (docs/adr/0023-watch-companion.md).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionName = (findProperty("versionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.mateuxlucax.caparao.wear"
    compileSdk = 37

    defaultConfig {
        // The phone app's id and signing key: the Data Layer only pairs apps that share both.
        applicationId = "io.github.mateuxlucax.caparao"
        // Wear OS 3+.
        minSdk = 30
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
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
        // Matches the phone's debug build, which it pairs with.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // WatchScreenshotsTest renders the watch screen on the JVM, like the phone's ScreenshotsTest.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            it.systemProperty("roborazzi.test.record", project.hasProperty("screenshots").toString())
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":wearlink"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // The Ongoing Activity during a brew.
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.core:core:1.19.1")

    testImplementation("junit:junit:4.13.2")
    // docs/screenshots/watch, test-only, nothing ships.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
