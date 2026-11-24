plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aesthetic.framed"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aesthetic.framed"
        minSdk = 35
        targetSdk = 37
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // CameraX 1.7.0
    val cameraxVersion = "1.7.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ExifInterface 1.4.2
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Google MediaPipe Tasks Vision 1.2.3 (Real-time NPU/GPU object detection & tracking)
    implementation("com.google.mediapipe:tasks-vision:1.2.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Strict Offline Guard: Enforce zero INTERNET permission in merged manifest
tasks.register("verifyNoInternetPermission") {
    description = "Fails the build if the merged AndroidManifest contains android.permission.INTERNET"
    group = "verification"
    doLast {
        val mergedManifests = fileTree(layout.buildDirectory.dir("intermediates/merged_manifests")) {
            include("**/AndroidManifest.xml")
        }
        var foundInternet = false
        mergedManifests.files.forEach { file ->
            if (file.exists() && file.readText().contains("android.permission.INTERNET")) {
                foundInternet = true
            }
        }
        if (foundInternet) {
            throw GradleException("PRIVACY VIOLATION: Merged AndroidManifest contains 'android.permission.INTERNET'! Framed must remain 100% offline.")
        }
    }
}

tasks.matching { it.name.startsWith("process") && it.name.endsWith("Manifest") }.configureEach {
    finalizedBy("verifyNoInternetPermission")
}
