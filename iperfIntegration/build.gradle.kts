plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.leekleak.iperfintegration"
    compileSdk = 37

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "30.0.16248370"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
