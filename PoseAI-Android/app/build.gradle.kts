import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.poseai.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.poseai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // 关闭收费，全部免费：移除所有内购相关代码
    }

    signingConfigs {
        create("release") {
            val ks = File("${System.getProperty("user.home")}/.android/release-key.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "poseai_release"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "poseai"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "poseai_release"
            } else {
                // Fall back to debug keystore for CI/local builds when no release keystore is provided.
                storeFile = File("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Auto-enable release signing (keystore exists) or fall back to debug signing.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Disable lint during release builds to avoid flaky network-dependent lint resolution
        // and prevent OOM from running lint + R8 simultaneously.
        checkReleaseBuilds = false
        abortOnError = false
        fatal += "MissingTranslation"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn", "-Xjvm-default=all")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")

    // ML Kit 姿态检测（Android 端与 Vision 姿态检测对应）
    implementation("com.google.mlkit:pose-detection:17.0.0")
    // ML Kit 图像标签（Android 端与 MobileNetV2/Places365 场景分类对应，离线 ImageNet 标签）
    implementation("com.google.mlkit:image-labeling:17.0.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}