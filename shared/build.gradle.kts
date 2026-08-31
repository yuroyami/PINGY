import io.github.yuroyami.kitessot.kiteSsot

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.cocoapods)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Backing fields and context parameters are stable language features
        // since Kotlin 2.4; only the expect/actual-classes opt-in remains.
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }

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
                // The .def includes shared/native/icmp_core.h, the one protocol
                // implementation Android, the JVM and iOS all compile.
                compilerOpts("-I${projectDir}/native")
            }
        }
    }

    // iOS configuration. KiteSSOT handles pbxproj version/bundleId/appName
    // propagation via the `syncIosConfig` task hooked into framework linking.
    cocoapods {
        summary = "${kiteSsot.appName.get()} Common Code (Platform-agnostic)"
        homepage = "www.github.com/yuroyami/PINGY"
        version = kiteSsot.version.get()
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
            }
        }

        commonMain.dependencies {
            /* Forcing Kotlin libs to match the compiler */
            implementation(libs.kotlin.stdlib)

            /* Explicitly specifying a newer coroutines version */
            implementation(libs.kotlin.coroutines.core)

            /* Official JetBrains Kotlin Date 'n time manager (i.e: generating date from epoch) */
            implementation(libs.kotlinx.datetime)

            /* JSON codec for persisted panel specs */
            implementation(libs.kotlinx.serialization.json)

            /* Preferences DataStore (KMP core) for cross-session persistence */
            implementation(libs.androidx.datastore.preferences)

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

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.coroutines.test)
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

        // Output goes to build/, never back into src/. Writing a compiled binary
        // into the source tree made it a tracked artifact: the committed dylib
        // carried an absolute developer path as its install name, declared a
        // minimum macOS of whatever the build machine happened to run, and was
        // only ad-hoc signed. None of that is distributable.
        val outDir = layout.buildDirectory.dir("nativeLibs/native/$platformDir-$archDir")
        val outFile = outDir.map { it.file("libpingy_icmp.$libExt") }

        val buildJvmNative = tasks.register<Exec>("buildJvmNative") {
            group = "build"
            description = "Compile libpingy_icmp for the host JVM"

            val srcFile = layout.projectDirectory.file("native/icmp_ping.c")
            val coreHeader = layout.projectDirectory.file("native/icmp_core.h")

            inputs.file(srcFile)
            inputs.file(coreHeader)
            outputs.file(outFile)

            doFirst { outDir.get().asFile.mkdirs() }

            val javaHome = System.getProperty("java.home")
            val args = mutableListOf(
                "cc", "-shared", "-fPIC", "-O2", "-fvisibility=hidden",
                "-Wall", "-Wextra",
                "-I${projectDir}/native",
                "-I$javaHome/include",
                "-I$javaHome/include/$jniInclude",
            )
            if (hostOs.isMacOsX) {
                // A stable, portable identity instead of the build machine's
                // absolute path and whatever SDK it happened to have.
                args += listOf(
                    "-install_name", "@rpath/libpingy_icmp.dylib",
                    "-mmacosx-version-min=11.0",
                )
            }
            args += listOf("-o", outFile.get().asFile.absolutePath, srcFile.asFile.absolutePath)
            commandLine(args)
        }

        // Packaged into the jar from build/, so `src/jvmMain/resources` stays
        // free of generated binaries.
        sourceSets.named("jvmMain") {
            resources.srcDir(layout.buildDirectory.dir("nativeLibs"))
        }
        tasks.named("jvmProcessResources") { dependsOn(buildJvmNative) }
    }
}
