// Root build file. Plugins are declared here and applied in each module.
// AGP 9 compiles Kotlin itself, so the app module needs no separate Kotlin Android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
