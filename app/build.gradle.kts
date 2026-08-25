import java.util.Properties

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

// Release signing credentials, read from local.properties. Neither the keystore
// nor its passwords are in this repository: the keystore is gitignored and the
// passwords live in local.properties, which is gitignored too. So anyone else who
// clones this has none of them — and neither does F-Droid's build server, which
// compiles from source and signs with its own key. Every value is therefore
// optional, and when any one is missing the release build is simply left
// *unsigned* instead of failing. That trade is deliberate: an unsigned APK
// announces itself the moment you try to install it, whereas a build that refuses
// to configure looks like the project is broken.
val signingProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.isFile) propsFile.inputStream().use { load(it) }
}

// Paths here resolve against the app module, so "release.keystore" in
// local.properties means app/release.keystore. Requiring isFile means a stale or
// mistyped path behaves exactly like no path at all, rather than failing much
// later inside the signing task with a less obvious message.
val releaseKeystore = signingProps.getProperty("RELEASE_STORE_FILE")
    ?.let { file(it) }
    ?.takeIf { it.isFile }

android {
    namespace = "io.github.michealjiaming.pomodoro"
    compileSdk = 34

    // Declared only when all four pieces are present. The release build type below
    // looks the config up by name and receives null if it was never created, which
    // is what produces the unsigned fallback described above.
    signingConfigs {
        val storePass = signingProps.getProperty("RELEASE_STORE_PASSWORD")
        val aliasName = signingProps.getProperty("RELEASE_KEY_ALIAS")
        val keyPass = signingProps.getProperty("RELEASE_KEY_PASSWORD")
        if (releaseKeystore != null && storePass != null &&
            aliasName != null && keyPass != null
        ) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = storePass
                keyAlias = aliasName
                keyPassword = keyPass
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.michealjiaming.pomodoro"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            // Null whenever the credentials above were absent, and an APK with no
            // signing config is emitted unsigned. Android will not install such a
            // file, which is the intended outcome: better a package that plainly
            // cannot be installed than one signed with the shared debug key, which
            // anyone could forge an update against.
            signingConfig = signingConfigs.findByName("release")
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
