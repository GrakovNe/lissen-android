plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.dagger.hilt.android") version "2.59.1" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
}