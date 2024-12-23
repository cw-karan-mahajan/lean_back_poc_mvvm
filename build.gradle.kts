buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jacoco:org.jacoco.core:0.8.11")
        classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.7.1")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.50")
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}