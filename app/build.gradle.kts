import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.mmgpt.shoplistgpt"
    compileSdk = 35  // MM change

    defaultConfig {
        applicationId = "com.mmgpt.shoplistgpt"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        viewBinding = true
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
}

// <— this forces all Kotlin compilations to target 1.8 bytecode
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
    implementation("androidx.core:core-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.opencsv:opencsv:5.7.1")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
