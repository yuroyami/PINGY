enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal() // kmp-ssot: remove once com.yuroyami.kmpssot is live on Gradle Plugin Portal
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pingy"
include(":androidApp")
include(":shared")
