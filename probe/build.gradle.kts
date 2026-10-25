// Throwaway stage 0 probe (docs/spike.md): talks to the real Timemore Dot and records every frame to JSONL.
// Same toolchain as :app so the spike code can be lifted into it later; nothing here ships.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.mateuxlucax.caparao.probe"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mateuxlucax.caparao.probe"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.0-probe"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
}
