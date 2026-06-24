plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("com.google.devtools.ksp") version "2.3.8" apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
}