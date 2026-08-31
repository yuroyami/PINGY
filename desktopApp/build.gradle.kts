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
            // No MSI. Winsock exposes no SOCK_DGRAM + IPPROTO_ICMP, so a Windows
            // installer would ship an app that cannot ping anything. Add the
            // target back together with a real Windows transport, not before.
            targetFormats(TargetFormat.Dmg, TargetFormat.Deb)

            packageName = "Pingy"

            // Package version only; the product's marketing version stays 0.1.0.
            // jpackage requires a non-zero first component.
            packageVersion = "1.0.0"
        }
    }
}
