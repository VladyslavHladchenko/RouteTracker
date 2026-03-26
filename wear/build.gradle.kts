plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val golemioApiKey = providers.gradleProperty("golemioApiKey")
    .orElse(providers.environmentVariable("GOLEMIO_API_KEY"))
    .orElse("")
    .get()
val ciDebugKeystorePath = providers.environmentVariable("CI_DEBUG_KEYSTORE_PATH").orNull
val ciDebugKeystorePassword = providers.environmentVariable("CI_DEBUG_KEYSTORE_PASSWORD")
    .orElse("android")
    .get()
val ciDebugKeyAlias = providers.environmentVariable("CI_DEBUG_KEY_ALIAS")
    .orElse("androiddebugkey")
    .get()
val ciDebugKeyPassword = providers.environmentVariable("CI_DEBUG_KEY_PASSWORD")
    .orElse(ciDebugKeystorePassword)
    .get()

android {
    namespace = "com.example.routetracker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.routetracker"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GOLEMIO_API_KEY",
            "\"${golemioApiKey.escapeBuildConfigString()}\"",
        )
    }

    signingConfigs {
        if (ciDebugKeystorePath != null) {
            create("ciDebug") {
                storeFile = file(ciDebugKeystorePath)
                storePassword = ciDebugKeystorePassword
                keyAlias = ciDebugKeyAlias
                keyPassword = ciDebugKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (ciDebugKeystorePath != null) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("routeTracker.moduleDir", project.projectDir.absolutePath)
                val screenshotTaskType = when {
                    project.findProperty("roborazzi.test.record") == "true" -> "record"
                    project.findProperty("roborazzi.test.verify") == "true" -> "verify"
                    project.findProperty("roborazzi.test.compare") == "true" -> "compare"
                    else -> "compare"
                }
                it.systemProperty("routeTracker.screenshotTaskType", screenshotTaskType)
            }
        }
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
    implementation(libs.compose.ui.tooling)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.protolayout)
    implementation(libs.androidx.protolayout.material3)
    implementation(libs.guava)
    implementation(libs.androidx.tiles.tooling.preview)
    implementation(libs.androidx.watchface.complications.data.source.ktx)
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.watchface.complications.rendering)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.watchface.complications.rendering)
    debugImplementation(libs.androidx.tiles.renderer)
    debugImplementation(libs.androidx.tiles.tooling)
}

fun String.escapeBuildConfigString(): String = replace("\\", "\\\\").replace("\"", "\\\"")
