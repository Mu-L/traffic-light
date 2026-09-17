plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.leekleak.shizukuintegration"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.timber)
}