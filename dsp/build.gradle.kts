plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}

// Writes the home-theatre test clips as raw PCM; tools/theatre/make-clips.sh turns them into Dolby Digital.
tasks.register<JavaExec>("theatreClips") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("dev.autotune.dsp.tools.WriteTheatreClips")
    args((project.findProperty("out") as String?) ?: layout.buildDirectory.dir("theatre").get().asFile.path)
}
