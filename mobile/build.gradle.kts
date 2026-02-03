plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) version "2.0.21"
    alias(libs.plugins.kotlin.compose) version "2.0.21"
    alias(libs.plugins.hilt.android)
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.kapt")
}

import java.util.Properties
import org.gradle.api.GradleException

android {
    namespace = "com.thesisapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thesisapp"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val localProperties = Properties().apply {
            val candidateFiles = listOf(
                project.file("local.properties"),
                rootProject.file("local.properties")
            )
            val propsFile = candidateFiles.firstOrNull { it.exists() }
                ?: throw GradleException(
                    "Missing local.properties. Create one at either mobile/local.properties or <root>/local.properties and add SUPABASE_URL and SUPABASE_KEY."
                )
            propsFile.inputStream().use { load(it) }
        }

        fun requireLocalProperty(name: String): String {
            val value = localProperties.getProperty(name)
                ?.trim()
                ?.trim('"')
                ?.trim()
                ?: ""
            if (value.isBlank()) {
                throw GradleException("Missing or blank $name in local.properties")
            }
            return value
        }

        val supabaseUrl = requireLocalProperty("SUPABASE_URL")
        val supabaseKey = requireLocalProperty("SUPABASE_KEY")

        val lowerUrl = supabaseUrl.lowercase()
        if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1")) {
            throw GradleException(
                "SUPABASE_URL appears to point to localhost ($supabaseUrl). Set it to your real Supabase project URL like https://<project-ref>.supabase.co"
            )
        }
        if (!lowerUrl.startsWith("https://")) {
            throw GradleException("SUPABASE_URL must start with https:// (was: $supabaseUrl)")
        }

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.play.services.wearable)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.ui.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    wearApp(project(":wear"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    val serialization_version = "1.8.1"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")

    val coroutinesVersion = "1.7.3" // or latest stable
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    val filamentVersion = "1.32.1"
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:filament-utils-android:$filamentVersion")
    implementation("com.google.android.filament:gltfio-android:$filamentVersion")

    val tfVersion = "2.13.0"
    implementation("org.tensorflow:tensorflow-lite:$tfVersion")
    
    // MPAndroidChart for goal progress graphs
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Retrofit for HTTP calls to the Python metrics API
    val retrofitVersion = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
}