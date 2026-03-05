pluginManagement {repositories {
    google {
        content {
            includeGroupByRegex("com\\.android.*")
            includeGroupByRegex("com\\.google.*")
            includeGroupByRegex("androidx.*")
        }
    }
    mavenCentral()
    gradlePluginPortal()
}
    // ADD THIS BLOCK:
    plugins {
        // Specify the version here. 1.9.22 or 2.0.0 are common stable versions.
        kotlin("jvm") version "1.9.22"
    }
}

rootProject.name = "ShadowLink"
include(":app")