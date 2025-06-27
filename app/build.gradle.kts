plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") version "4.4.0" apply false
}

android {
    namespace = "com.jc01rho.youtubeautomaticuploader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jc01rho.youtubeautomaticuploader"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/DEPENDENCIES"
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Fragment and Activity
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)

    // YouTube API and Google Auth
    implementation(libs.google.api.client)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.youtube)
    implementation(libs.google.auth.library)
    implementation(libs.google.oauth2.client)

    // Network and async operations
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)

    // HTTP clients
    implementation(libs.google.http.client)
    implementation(libs.jackson.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
