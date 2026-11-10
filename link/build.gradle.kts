plugins {
    id("org.jetbrains.kotlin.jvm")
}

// The phone, TV and PC link: pure Kotlin, so the Android app and the Windows app share one copy.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":dsp"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
