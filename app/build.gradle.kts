plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.autotune"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.autotune"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.property("APP_VERSION_CODE") as String).toInt()
        versionName = project.property("APP_VERSION_NAME") as String
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // the walk-through driver waits for commands: it runs only when asked for with -Dwalk.dir=... (tools/walk/start.sh)
            val walk = System.getProperty("walk.dir")
            if (walk == null) it.exclude("**/WalkDriver*") else {
                it.systemProperty("walk.dir", walk)
                System.getProperty("robolectric.dependency.dir")?.let { d ->
                    it.systemProperty("robolectric.dependency.dir", d); it.systemProperty("robolectric.offline", "true")
                }
                it.outputs.upToDateWhen { false }
                it.maxHeapSize = "3g"
                it.testLogging.showStandardStreams = true
            }
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "AutoTune-${versionName}-${buildType.name}.apk"
        }
    }
}

dependencies {
    implementation(project(":dsp"))
    implementation(project(":link"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    // the first-time-user walk-through (app/src/test/.../walk): the real screens on the JVM, with a pretend car
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
