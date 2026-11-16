plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A local, explicitly instrumented AAR is opt-in. The published AAR remains the
// default and cannot execute any native lab fault. Fail closed for missing files.
val eidelweissLabAarPath = providers.gradleProperty("eidelweissLabAar").orNull
val eidelweissLabAarFile = eidelweissLabAarPath?.let { file(it) }
require(eidelweissLabAarFile == null || eidelweissLabAarFile.isFile) {
    "Instrumented AAR missing: $eidelweissLabAarPath"
}

android {
    namespace = "de.mtschirs.eidelweiss"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.mtschirs.eidelweiss"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("boolean", "EIDELWEISS_LAB_AAR",
            if (eidelweissLabAarFile != null) "true" else "false")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
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
    // Instrumented EAC2 is only usable with a locally compiled Qt Android AAR.
    // Never package both native SDK versions (their service classes conflict).
    if (eidelweissLabAarFile != null) {
        implementation(files(eidelweissLabAarFile))
    } else {
        implementation("com.governikus:ausweisapp:2.6.2")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
