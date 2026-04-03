import org.gradle.api.Project
import java.io.File
import java.util.Properties

object AppConfig {
    val localProperties = Properties().apply {
        val file = File("local.properties")
        if (file.exists()) load(file.inputStream())
    }

    const val javaVersion = 21

    const val compileSdk = 36
    const val minSdk = 26

    const val appName = "PINGY"

    const val versionName = "0.1.0"
    val versionCode = ("1" + versionName.split(".").joinToString("") { it.padStart(3, '0') }).toInt()

    fun Project.updateIOSVersion() {
        val pbxprojFile = File("${rootDir}/iosApp/iosApp.xcodeproj/project.pbxproj")
        if (!pbxprojFile.exists()) {
            logger.warn("project.pbxproj not found at: ${pbxprojFile.absolutePath}")
            return
        }

        val original = pbxprojFile.readText()
        val updated = original
            .replace(Regex("""MARKETING_VERSION = [^;]+;"""), "MARKETING_VERSION = $versionName;")
            .replace(Regex("""CURRENT_PROJECT_VERSION = [^;]+;"""), "CURRENT_PROJECT_VERSION = $versionCode;")
            .replace(Regex("""INFOPLIST_KEY_CFBundleDisplayName = [^;]+;"""), "INFOPLIST_KEY_CFBundleDisplayName = $appName;")

        if (updated != original) {
            pbxprojFile.writeText(updated)
            logger.lifecycle("✅ Xcode version updated to $versionName ($versionCode)")
        } else {
            logger.warn("⚠️ Version fields not found in project.pbxproj")
        }

        // Sync app name to Config.xcconfig
        val xcconfigFile = File("${rootDir}/iosApp/Configuration/Config.xcconfig")
        if (xcconfigFile.exists()) {
            val xcOriginal = xcconfigFile.readText()
            val xcUpdated = xcOriginal.replace(Regex("""APP_NAME=.*"""), "APP_NAME=$appName")
            if (xcUpdated != xcOriginal) {
                xcconfigFile.writeText(xcUpdated)
                logger.lifecycle("✅ Config.xcconfig APP_NAME updated to $appName")
            }
        }
    }
}
