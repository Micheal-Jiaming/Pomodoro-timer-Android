// Which project the build contains, and where it is allowed to download from.
// Gradle reads this file before anything else, so it cannot use values defined in
// build.gradle.kts.

// Where the Gradle *plugins* come from (the Android and Kotlin plugins). Separate
// from the dependency repositories below, because plugins are resolved earlier, in
// a phase where the dependency block does not yet exist.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Only these two repositories, declared here and nowhere else. FAIL_ON_PROJECT_REPOS
    // turns a `repositories {}` block inside app/build.gradle.kts into a build
    // *error* rather than quietly merging it. That is deliberate: it means the
    // complete list of places this build can fetch code from is the repository lines
    // visible in this file — three distinct hosts, google(), mavenCentral() and
    // gradlePluginPortal(), the last only for plugins. That is what makes the
    // dependency surface auditable by reading one file, and F-Droid does audit it.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Not the directory name, which contains spaces. This feeds Gradle's project name
// and so appears in build output and task paths; "Pomodoro timer Android" would be
// awkward to type on a command line.
rootProject.name = "PomodoroTimer"
// One module. `:app` is the directory app/, holding the whole application.
include(":app")
