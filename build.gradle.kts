plugins {
    id("com.yuroyami.kmpssot") version "1.0.3"

    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
    alias(libs.plugins.kotlin.cocoapods).apply(false)

    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.plugin).apply(false)

    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)

    alias(libs.plugins.buildConfig).apply(false)
}

kmpSsot {
    appName         = "Pingy"
    versionName     = "0.1.0"
    bundleIdBase    = "com.yuroyami.pingy"
    iosBundleSuffix = ".ios"
    javaVersion     = 21

    sharedModule     = "shared"
    androidAppModule = "androidApp"


    appLogoXml = file("shared/src/commonMain/composeResources/drawable/pingy_vector.xml")
    appLogoPng = file("shared/src/commonMain/composeResources/drawable/pingy_raster.png")

    // locales auto-detected from shared/src/commonMain/composeResources/values-*
    // (Pingy has none yet, so the list stays empty.)
}
