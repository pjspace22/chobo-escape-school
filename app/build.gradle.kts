plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // TODO: 제출 전에 팀/앱 이름에 맞게 바꾸기 (namespace, applicationId 둘 다)
    namespace = "com.example.pleosstarter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.pleosstarter"
        // Pleos 요구사항: 차량 사이버보안(사이닝 키 로테이션) 때문에 API 28 이상 필수
        minSdk = 28
        targetSdk = 35
        versionCode = 20
        versionName = "0.19.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 제출용 시연 APK: 별도 서명 키 없이 설치되도록 debug 키로 서명
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.navi)
    implementation(libs.pleos.fused)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
