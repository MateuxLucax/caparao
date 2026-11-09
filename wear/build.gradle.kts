// Wear OS companion: mirrors the phone's scale and brew over the Data Layer (#107).
plugins {
    alias(libs.plugins.android.application)
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
            // Same rule as the phone: without the release key the APK stays unsigned, never debug-signed (#327).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // WatchTests render the watch screen on the JVM, like the phone's screen tests.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":wearlink"))
    implementation(libs.activity.compose)
    // One Compose version for the app, its tests and ui-test-manifest: mixed versions break the screen tests.
    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.lifecycle.runtime.compose)
    // The Ongoing Activity during a brew.
    implementation(libs.wear.ongoing)
    implementation(libs.core)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
