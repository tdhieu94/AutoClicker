plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hieu.autoclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hieu.autoclicker"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Khoá ký cố định lấy từ GitHub Secrets (CI giải mã ra file và truyền qua biến môi trường),
    // để mỗi bản build mới cài đè lên bản cũ được. Không có khoá thì dùng debug key.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "autoclicker"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
