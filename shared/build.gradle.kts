plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvmToolchain(AppConfig.javaVersion)

    android {
        namespace = "com.yuroyami.pingy"
        compileSdk = AppConfig.compileSdk
        minSdk = AppConfig.minSdk
        androidResources { enable = true }
    }

    // Activating iOS targets (iosMain)
    listOf(
        iosSimulatorArm64(), //We enable this only if we're planning to test on a simulator
        iosArm64()
    ).forEach {
        it.compilations.getByName("main") {
            @Suppress("unused") val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
        }
    }

    // iOS configuration
    cocoapods {
        summary = "${AppConfig.appName} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/PINGY"
        version = AppConfig.versionName
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = false
        }

        pod("SPLPing")
    }

    sourceSets {
        all {
            languageSettings {
                optIn("org.jetbrains.compose.resources.ExperimentalResourceApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("kotlin.RequiresOptIn")
                optIn("kotlin.experimental.ExperimentalNativeApi")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.ExperimentalUnsignedTypes")
                optIn("kotlin.ExperimentalStdlibApi")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
                optIn("kotlinx.cinterop.ExperimentalForeignApi") //for iOS
                optIn("kotlinx.cinterop.BetaInteropApi") //for iOS
                optIn("kotlin.time.ExperimentalTime")
                enableLanguageFeature("ExplicitBackingFields") //same as -Xexplicit-backing-fields compiler flag
                enableLanguageFeature("NestedTypeAliases") //-Xnested-type-aliases
                enableLanguageFeature("ExpectActualClasses") //-Xexpect-actual-classes
                enableLanguageFeature("ContextParameters") //Xcontext-parameters
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer coroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation(libs.kotlinx.datetime)

            /* Compose core dependencies */
            implementation(libs.bundles.compose.multiplatform)

            /* ViewModel support */
            implementation(libs.compose.viewmodel)

            /* Navigation support with the modern nav3 library */
            implementation(libs.bundles.navigation3)

            /* Logging */
            implementation(libs.logging.kermit)
        }

        androidMain.dependencies {
            /* Backward compatibility APIs from Google's Jetpack AndroidX */
            /* Contains AndroidX Libs: Core (+CoreSplashScreen +CorePiP), AppCompat, Activity Compose, DocumentFile */
            implementation(libs.bundles.jetpack.androidx.extensions)

            /* Extended coroutine support for Android threading */
            implementation(libs.kotlin.coroutines.android)
        }

        iosMain.dependencies {
            /* Nothing needed here */
        }
    }
}

with(AppConfig) {
    updateIOSVersion() //Uncomment if Xcode build fails, for some reason it breaks Gradle build from within Xcode
}

buildConfig {
    buildConfigField("APP_NAME", AppConfig.appName)
    buildConfigField("APP_VERSION", AppConfig.versionName)
    buildConfigField("DEBUG", false)
}