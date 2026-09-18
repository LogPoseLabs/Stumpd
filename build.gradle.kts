// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // Hilt's Gradle plugin needs javapoet 1.13+; AGP/Crashlytics pull in 1.10.0 on this
        // classpath, which is the parent classloader for subproject plugins.
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jlleitschuh.gradle.ktlint") version ("11.2.0")
    // 4.4.1 is the floor for the Crashlytics Gradle plugin 3.x below; on 4.4.0 the release
    // build fails outright at :app:minifyReleaseWithR8.
    id("com.google.gms.google-services") version "4.4.2" apply false // Firebase
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}

// Force Java 17 for all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }
}
