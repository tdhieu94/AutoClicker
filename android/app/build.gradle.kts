plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hieu.autoclicker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hieu.autoclicker"
        minSdk = 24
        targetSdk = 36
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
    // Hai bản phân phối:
    //  - github: tải APK từ GitHub Releases, tự kiểm tra và cài bản cập nhật.
    //  - play:   đăng lên CH Play, có quảng cáo AdMob, KHÔNG tự cập nhật (Google cấm).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
        }
        create("play") {
            dimension = "store"
            // Tên gói riêng để cài song song với bản GitHub. Đổi trước lần tải lên CH Play đầu tiên nếu muốn,
            // vì sau khi đã đăng thì không đổi được nữa.
            applicationId = "com.hieu.autoclicker.play"
            fun prop(name: String) = (project.findProperty(name) as String?).orEmpty()
            manifestPlaceholders["admobAppId"] = prop("admobAppId")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("admobBannerId")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${prop("admobInterstitialId")}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

dependencies {
    "playImplementation"("com.google.android.gms:play-services-ads:24.4.0")
    "playImplementation"("com.google.android.ump:user-messaging-platform:3.2.0")
}
