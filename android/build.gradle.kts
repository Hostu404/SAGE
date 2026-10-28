buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself ("built-in Kotlin"); the Compose
        // compiler plugin is pinned to libs.versions.toml's `kotlin`, so the
        // Kotlin Gradle plugin has to be that same version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
