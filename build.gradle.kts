buildscript {
    dependencies {
        classpath("org.jacoco:org.jacoco.core:0.8.7")
        classpath("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:2.7.1")
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    id ("com.google.dagger.hilt.android") version "2.50" apply false
}
