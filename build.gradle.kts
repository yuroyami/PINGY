plugins {
    val kotlin = "2.0.0-Beta4" //"1.9.22"
    id("org.jetbrains.kotlin.multiplatform") version kotlin apply false
    id("org.jetbrains.kotlin.android") version kotlin apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlin apply false

    val agp = "8.4.0-alpha13"
    id("com.android.application") version agp apply false
    id("com.android.library") version agp apply false

    val compose = "1.6.1"
    id("org.jetbrains.compose") version compose apply false
}
