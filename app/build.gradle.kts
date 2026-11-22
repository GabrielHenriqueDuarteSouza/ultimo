plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// O workflow do GitHub passa a plataforma/build-tools que o runner realmente tem instalados.
val sdkPlatform = (findProperty("sdkPlatform") as String?)?.toIntOrNull() ?: 34
val sdkBuildTools = findProperty("sdkBuildTools") as String?

android {
    namespace = "com.example.aimobot"
    compileSdk = sdkPlatform
    if (sdkBuildTools != null) buildToolsVersion = sdkBuildTools

    defaultConfig {
        applicationId = "com.example.aimobot"
        minSdk = 30          // AccessibilityService.takeScreenshot exige API 30+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
