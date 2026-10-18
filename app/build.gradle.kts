plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.olaapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.olaapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sem dependências externas: o app usa só o framework Android,
// então o APK é leve e roda em qualquer arquitetura (ARM, ARM64, x86).
