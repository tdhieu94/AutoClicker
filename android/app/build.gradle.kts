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
        // Trên GitHub Actions, mỗi lần build có số thứ tự tăng dần (GITHUB_RUN_NUMBER),
        // dùng làm versionCode để app biết khi nào có bản mới hơn.
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (runNumber != null) 100 + runNumber else 3
        versionName = if (runNumber != null) "1.3.$runNumber" else "1.3-dev"
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
    buildFeatures { buildConfig = true }
}
