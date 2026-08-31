plugins {
    id("io.github.yuroyami.kitessot") version "4.1.0"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
}

kiteSsot {
    appName = "Pingy"
    jvmTarget = 21

    // jpackage refuses an app-version whose first component is zero
    // ("The first number in an app-version cannot be zero or negative"), which
    // silently produced no Desktop installer at all. The marketing version
    // stays 0.1.0 everywhere; only the Desktop package version is pinned.
    // jpackage refuses an app-version whose first component is zero ("The first
    // number in an app-version cannot be zero or negative"), which silently
    // produced no Desktop installer at all. kiteSsot has no 0.x mapping, so the
    // Desktop target opts out of version propagation and sets its own package
    // version in desktopApp/build.gradle.kts. Android and iOS still get 0.1.0.
    version("0.1.0") {
        skip(desktop)
    }

    id("com.yuroyami.pingy") {
        ios { suffix = ".ios" }
    }

    modules {
        shared = ":shared"
        androidApps(":androidApp")
        desktopApps(":desktopApp")
    }

    // Declaring the art is enough for the desktop installer icons: they generate into
    // build/ and get packaged. rewrite { } arms the Android res + iOS asset catalog
    // install, which only happens when ./gradlew kiteRewriteLogo runs.
    logo {
        foreground = file("shared/src/commonMain/composeResources/drawable/pingy_raster.png")
        backgroundColor = "#0B0E13"
        rewrite { }
    }

    // Identity constants (appName/version/id) generated into commonMain.
    buildConfig { }

    // Compose Desktop identity and build number flow on their own now that :desktopApp
    // is a detected Compose Desktop app, so no desktop { } block is needed here.
}
