plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.mtschirs.eidelweiss"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.mtschirs.eidelweiss"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Official, unmodified EAC implementation published by Governikus under EUPL-1.2.
    implementation("com.governikus:ausweisapp:2.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
