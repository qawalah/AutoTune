pluginManagement {
    plugins {
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.jetbrains.compose") version "1.7.3"
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AutoTune"
include(":dsp")
// The phone, TV and PC link, shared by the Android app and the Windows app.
include(":link")
// The Windows app (Compose for Desktop). It builds and tests anywhere; its installer is made on Windows (see .github).
include(":desktop")
// The Android module only loads when an Android SDK is present, so ":dsp:test" works anywhere.
if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || file("local.properties").exists()) {
    include(":app")
}
