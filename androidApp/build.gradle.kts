plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(AppConfig.javaVersion)
}

android {
    namespace = "com.yuroyami.pingy.android"
    compileSdk = AppConfig.compileSdk

    signingConfigs {
        file("${rootDir}/keystore/pingykey.jks").takeIf { it.exists() }?.let { keystoreFile ->
            create("keystore") {
                storeFile = keystoreFile
                AppConfig.localProperties.apply {
                    keyAlias = getProperty("yuroyami.keyAlias")
                    keyPassword = getProperty("yuroyami.keyPassword")
                    storePassword = getProperty("yuroyami.storePassword")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.yuroyami.pingy"
        minSdk = AppConfig.minSdk
        targetSdk = AppConfig.compileSdk
        versionCode = AppConfig.versionCode
        versionName = AppConfig.versionName

        manifestPlaceholders["appName"] = AppConfig.appName

        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        signingConfigs.findByName("keystore")?.let { config ->
            signingConfig = config
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(AppConfig.javaVersion)
        targetCompatibility = JavaVersion.toVersion(AppConfig.javaVersion)
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

    // This block strips out odd, unused artifacts that the google-shortcuts library brings along,
    // none of which are needed for its main features.
    // This will remove them also from any other library that might use them
    configurations.all {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
        exclude(group = "com.google.android.gms")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)
    implementation(projects.shared)
}