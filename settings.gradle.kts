enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Fallback mirror: repo.maven.apache.org (mavenCentral's default host)
        // is unreachable from some networks; repo1 serves the same content.
        maven("https://repo1.maven.org/maven2/")
    }
}

rootProject.name = "Pingy"
include(":androidApp")
include(":shared")
include(":desktopApp")
