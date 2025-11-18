plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sounds"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sounds"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    aaptOptions {
        // This is perfect! Keeps your .tflite model from being corrupted.
        noCompress += ".tflite"
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- UPDATED TENSORFLOW LITE DEPENDENCIES ---
    // We'll use a newer version for more stability
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    // This adds GPU support, which will make your ML model run much faster
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
    // We are not using the 'tensorflow-lite-support' library, so it's removed.
    // ---

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}