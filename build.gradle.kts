// Versions are pinned as a known-good set: AGP 8.5.2 needs Gradle 8.7 and JDK 17,
// and Compose compiler 1.5.14 is the one that matches Kotlin 1.9.24.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
