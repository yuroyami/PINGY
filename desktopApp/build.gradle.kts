import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.plugin)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)

    /* Dispatchers.Main on desktop rides the Swing event loop */
    implementation(libs.kotlin.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.yuroyami.pingy.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // packageName/version/bundle id come from the root kiteSsot { desktop { } } block.
        }
    }
}
