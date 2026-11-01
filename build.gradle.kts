// Top-level build file.
plugins {
    // Declare plugin versions here (resolved from the version catalog),
    // apply them in module build files; do not apply at root.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}