import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin module: data models and validation rules.
// It has no Android or Firebase dependencies, so its unit tests run on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
