import com.yuroyami.kmpssot.KmpSsotExtension

val ssot = rootProject.extensions.getByType(KmpSsotExtension::class.java)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.buildConfig)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.yuroyami.pingy"
        compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        androidResources { enable = true }
    }

    // JVM (desktop) target: library-only, no UI entry point. Exists to let us
    // share the JNI-based ICMP engine with any future desktop driver. The host
    // toolchain compiles `native/icmp_ping.c` into `libpingy_icmp.{dylib,so}`
    // via the `buildJvmNative` Exec task below, bundled into the jar under
    // `/native/{darwin,linux}-{arm64,x86_64}/`, and extracted at first use by
    // `com.yuroyami.pingy.utils.NativeIcmpPing`.
    jvm()

    // Activating iOS targets (iosMain)
    listOf(
        iosSimulatorArm64(), //We enable this only if we're planning to test on a simulator
        iosArm64()
    ).forEach {
        it.compilations.getByName("main") {
            @Suppress("unused") val nsKVO by cinterops.creating {
                defFile("src/nativeInterop/cinterop/NSKeyValueObserving.def")
            }
            @Suppress("unused") val icmpPing by cinterops.creating {
                defFile("src/nativeInterop/cinterop/IcmpPing.def")
            }
        }
    }

    // iOS configuration. kmpSsot handles pbxproj version/bundleId/appName
    // propagation via the `syncIosConfig` task hooked into framework linking.
    cocoapods {
        summary = "${ssot.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/PINGY"
        version = ssot.versionName.get()
        ios.deploymentTarget = "14.0"
        podfile = project.file("../iosApp/Podfile")
        framework {
            baseName = "shared"
            isStatic = false
        }
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

        jvmMain.dependencies {
            /* Desktop flavor of coroutines — Dispatchers.IO etc. */
            implementation(libs.kotlin.coroutines.core)
        }
    }
}

// Host-OS compile of the unprivileged-ICMP JNI shim for the JVM target.
// Android compiles the same C source via CMake/NDK (see
// `androidApp/src/main/cpp/CMakeLists.txt`); here we just shell out to `cc`,
// which resolves to clang on macOS and gcc on Linux. The resulting shared
// library is written into `src/jvmMain/resources/native/<plat>-<arch>/` so
// `jvmProcessResources` picks it up and packages it into the jar.
//
// Windows is skipped entirely — Winsock has no SOCK_DGRAM+IPPROTO_ICMP, so
// NativeIcmpPing returns null there instead.
run {
    val hostOs = org.gradle.internal.os.OperatingSystem.current()
    val hostArch = System.getProperty("os.arch").lowercase()
    val platformSpec: Triple<String, String, String>? = when {
        hostOs.isMacOsX -> Triple("darwin", "dylib", "darwin")
        hostOs.isLinux -> Triple("linux", "so", "linux")
        else -> null
    }
    val archDir: String? = when {
        hostArch.contains("aarch64") || hostArch.contains("arm64") -> "arm64"
        hostArch.contains("x86_64") || hostArch.contains("amd64") -> "x86_64"
        else -> null
    }

    if (platformSpec != null && archDir != null) {
        val (platformDir, libExt, jniInclude) = platformSpec

        val buildJvmNative = tasks.register<Exec>("buildJvmNative") {
            group = "build"
            description = "Compile libpingy_icmp for the host JVM"

            val srcFile = layout.projectDirectory.file("native/icmp_ping.c")
            val outDir = layout.projectDirectory.dir(
                "src/jvmMain/resources/native/$platformDir-$archDir"
            )
            val outFile = outDir.file("libpingy_icmp.$libExt")

            inputs.file(srcFile)
            outputs.file(outFile)

            doFirst { outDir.asFile.mkdirs() }

            val javaHome = System.getProperty("java.home")
            commandLine(
                "cc",
                "-shared",
                "-fPIC",
                "-O2",
                "-fvisibility=hidden",
                "-I$javaHome/include",
                "-I$javaHome/include/$jniInclude",
                "-o", outFile.asFile.absolutePath,
                srcFile.asFile.absolutePath,
            )
        }

        tasks.named("jvmProcessResources") {
            dependsOn(buildJvmNative)
        }
    }
}

buildConfig {
    buildConfigField("APP_NAME", ssot.appName.get())
    buildConfigField("APP_VERSION", ssot.versionName.get())
    buildConfigField("DEBUG", false)
}
