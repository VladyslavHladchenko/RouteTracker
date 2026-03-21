plugins {
    alias(libs.plugins.android.application)
}

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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.wearable)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
