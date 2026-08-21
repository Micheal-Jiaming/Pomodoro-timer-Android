plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The VERSION file at the project root is the single source of truth for the
// version, so it cannot drift from what the app reports. versionName is the file
// verbatim; versionCode is derived from it (1.1.0 -> 10100) so it always rises
// with it, which is what Google Play and `adb install -r` require. A missing or
// unparsable file yields the obviously-wrong name "0.0.0" rather than breaking
// the build, and the code is floored at 1 because Android rejects 0.
val versionFile = rootProject.file("VERSION")
val appVersionName: String =
    if (versionFile.isFile) versionFile.readText().trim() else "0.0.0"
val appVersionCode: Int = appVersionName
    .split(".")
    .map { it.toIntOrNull() ?: 0 }
    .let { parts ->
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        maxOf(major * 10_000 + minor * 100 + patch, 1)
    }

android {
    namespace = "com.pomodoro.timer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pomodoro.timer"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    // viewModelScope and the coroutines it drags in
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
