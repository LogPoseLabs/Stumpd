import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp) // resolves once defined in TOML
    alias(libs.plugins.room) // resolves once defined in TOML
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // Firebase
}

// Load keystore properties from local file or environment variables (for CI)
// Files are in app/ directory alongside google-services.json
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.oreki.stumpd"
    compileSdk = 35
    
    // Signing configuration - works on Mac, Windows, Linux
    signingConfigs {
        create("release") {
            // Try local properties file first, then environment variables (CI)
            // Files are in app/ directory alongside google-services.json
            storeFile = file(
                keystoreProperties.getProperty("storeFile") 
                    ?: System.getenv("KEYSTORE_FILE") 
                    ?: "release-keystore.jks"
            )
            storePassword = keystoreProperties.getProperty("storePassword") 
                ?: System.getenv("KEYSTORE_PASSWORD") 
                ?: ""
            keyAlias = keystoreProperties.getProperty("keyAlias") 
                ?: System.getenv("KEY_ALIAS") 
                ?: "stumpd"
            keyPassword = keystoreProperties.getProperty("keyPassword") 
                ?: System.getenv("KEY_PASSWORD") 
                ?: ""
        }
    }
    
    applicationVariants.all {
        outputs.all {
            val variantName = name // e.g., debug, release
            val appName = "Stumpd" // change to your preferred base name
            val versionNameSafe = versionName?.replace("[^A-Za-z0-9._-]".toRegex(), "_") ?: "v"
            val versionCodeSafe = versionCode
            val newName = "${appName}-${variantName}-${versionNameSafe}(${versionCodeSafe}).apk"
        }
    }

    defaultConfig {
        applicationId = "com.oreki.stumpd"
        minSdk = 24
        targetSdk = 34
        versionCode = 12
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Use release signing for debug builds too (for OTA update testing)
            signingConfig = if (keystorePropertiesFile.exists() || System.getenv("KEYSTORE_PASSWORD") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.room.common.jvm)
    
    // Firebase for online sync
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-config-ktx") // For OTA updates
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Google Sign-In for multi-device sync
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Unit Testing
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Room
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
// optional extras:
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.activity:activity-compose:1.8.0")

}