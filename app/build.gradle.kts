plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "win.swarsel.shopservation"
    compileSdk = 34

    defaultConfig {
        applicationId = "win.swarsel.shopservation"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.8.0"
    }

    val keystorePath = System.getenv("SHOPSERVATION_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SHOPSERVATION_KEYSTORE_PASS")
                keyAlias = System.getenv("SHOPSERVATION_KEY_ALIAS") ?: "shopservation"
                keyPassword = System.getenv("SHOPSERVATION_KEY_PASS")
                    ?: System.getenv("SHOPSERVATION_KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
