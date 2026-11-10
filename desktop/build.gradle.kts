import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// AutoTune for Windows: the phone measures, the PC plays the test clips and keeps the EQ in Equalizer APO.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":link"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // announces the PC on the Wi-Fi the way the phone's NsdManager looks for it
    implementation("org.jmdns:jmdns:3.6.5")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.7")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

// Renders the window to build/shots/pc-*.png without a screen (see ShotTool.kt).
tasks.register<JavaExec>("shots") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.autotune.pc.ShotToolKt")
    args(layout.buildDirectory.dir("shots").get().asFile.path)
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "dev.autotune.pc.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "AutoTune"
            // the same version as the phone app: gradle.properties is the only version source
            packageVersion = project.property("APP_VERSION_NAME") as String
            description = "AutoTune for Windows"
            vendor = "AutoTune"
            copyright = "AutoTune"
            licenseFile.set(rootProject.file("licenses/Inter-OFL.txt"))
            modules("java.instrument", "jdk.unsupported", "java.naming", "java.logging")
            windows {
                menuGroup = "AutoTune"
                shortcut = true
                menu = true
                // per user: installs without an administrator, like an Obtainium update on the phone
                perUserInstall = true
                dirChooser = false
                // never change: Windows uses it to upgrade the installed app in place
                upgradeUuid = "49ef6674-cdaa-4f85-bae5-08210fbe768f"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}
