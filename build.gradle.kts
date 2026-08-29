plugins {
    id("io.github.yuroyami.kitessot") version "3.1.0"

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
    version = "0.1.0"
    appId = "com.yuroyami.pingy"
    jvmTarget = 21

    modules {
        shared = ":shared"
        androidApps(":androidApp")
        desktopApps(":desktopApp")
    }

    ios {
        bundleIdSuffix = ".ios"
    }

    // Authorization gate for the icon-install tasks; also feeds the desktop
    // installer icons directly (those generate into build/ automatically).
    logo {
        foreground = file("shared/src/commonMain/composeResources/drawable/pingy_raster.png")
        backgroundColor = "#0B0E13"
    }

    // Identity constants (appName/version/appId) generated into commonMain.
    buildConfig { }

    // Compose Desktop identity + build number, propagated on every build.
    desktop { }
}
