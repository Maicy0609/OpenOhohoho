plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.open.ohohoho"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.open.ohohoho"
        // Shizuku api 13.1.5 要求 minSdk >= 24 (Android 7.0)
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // 签名信息从环境变量读取（CI 由 GitHub Secrets 注入），本地无环境变量时跳过签名
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        create("release") {
            if (keystorePassword != null) {
                storeFile = file(System.getenv("KEYSTORE_FILE") ?: "keystore/release.jks")
                storePassword = keystorePassword
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true          // 代码压缩/混淆
            isShrinkResources = true        // 资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Shizuku API (runs with system-level privileges via ADB / root)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
