plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Build number from CI so every GitHub build installs as an update over the previous one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "app.voidnotes"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.voidnotes"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Firebase Realtime Database for shared notes (repository variable VOID_FIREBASE_URL); can also be set in the app.
        val syncUrl = (System.getenv("VOID_FIREBASE_URL") ?: "").replace("\"", "")
        buildConfigField("String", "SYNC_URL", "\"$syncUrl\"")
    }

    signingConfigs {
        create("release") {
            // Secrets from GitHub Actions take priority; the bundled key keeps updates installable otherwise.
            val ksPath = System.getenv("VOID_KEYSTORE_PATH")
            storeFile = if (ksPath.isNullOrBlank()) rootProject.file("signing/voidnotes.jks") else file(ksPath)
            storePassword = System.getenv("VOID_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "voidnotes"
            keyAlias = System.getenv("VOID_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "voidnotes"
            keyPassword = System.getenv("VOID_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "voidnotes"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
