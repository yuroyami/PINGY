import com.android.build.api.variant.AndroidComponentsExtension
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.yuroyami.pingy.android"
    compileSdk = providers.gradleProperty("android.compileSdk").get().toInt()

    // applicationId, versionCode/Name, manifestPlaceholders[appName],
    // compileOptions (java version), resourceConfigurations — handled by kiteSsot.

    signingConfigs {
        file("${rootDir}/keystore/pingykey.jks").takeIf { it.exists() }?.let { keystoreFile ->
            create("keystore") {
                storeFile = keystoreFile

                val localProperties = Properties().apply {
                    val file = File("local.properties")
                    if (file.exists()) load(file.inputStream())
                }
                localProperties.apply {
                    keyAlias = getProperty("keystore.keyAlias")
                    keyPassword = getProperty("keystore.keyPassword")
                    storePassword = getProperty("keystore.storePassword")
                }
            }
        }
    }

    defaultConfig {
        minSdk = providers.gradleProperty("android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("android.targetSdk").get().toInt()

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("keystore")?.let { config ->
            signingConfig = config
        }

        // Ship the unprivileged-ICMP native lib only for the ABIs the app actually runs on.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Build the native ICMP shim (shared/native/icmp_ping.c) into libpingy_icmp.so
    // via CMake. Consumed at runtime by com.yuroyami.pingy.utils.NativeIcmpPing.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
        debug {
            applicationIdSuffix = ".dev"
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/INDEX.LIST"
            pickFirsts += "META-INF/versions/9/previous-compilation-data.bin"
            pickFirsts += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/license/**"
            excludes += "META-INF/native-image/**"

        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs & Android App Bundles.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(projects.shared)
}

/**
 * Package the legal texts into the APK from the canonical files at the repo root.
 *
 * These used to be duplicated under src/main/assets and kept in step by hand,
 * which lasted exactly as long as the first regeneration: the notices were
 * rewritten at the root and the packaged copy silently stayed two dependency
 * sets behind. Copying at build time means there is one source of truth and the
 * two cannot drift.
 */
abstract class StageLegalAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val documents: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun stage() {
        fs.sync {
            into(outputDir.dir("legal"))
            from(documents)
            // Keep the dependency licences in their own folder so an in-app
            // viewer can address them by a predictable path.
            from(licenseDir) { into("licenses") }
        }
    }
}

val stageLegalAssets = tasks.register<StageLegalAssets>("stageLegalAssets") {
    group = "build"
    description = "Copy the canonical legal texts into the packaged assets"
    documents.from(
        rootProject.layout.projectDirectory.file("LICENSE"),
        rootProject.layout.projectDirectory.file("NOTICE"),
        rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md"),
        rootProject.layout.projectDirectory.file("PRIVACY_POLICY.md"),
    )
    licenseDir.set(rootProject.layout.projectDirectory.dir("licenses"))
}

// AGP refuses a Provider on the SourceSet API and points at the Variant API for
// generated directories, which is what this is.
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
        stageLegalAssets,
        StageLegalAssets::outputDir,
    )
}
