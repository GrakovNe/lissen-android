import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)

  id("com.google.dagger.hilt.android")
  id("org.jmailen.kotlinter") version "5.2.0"
  id("com.google.devtools.ksp")
}

android {
  namespace = "org.grakovne.lissen.wear"
  compileSdk = 36

  defaultConfig {
    applicationId = "org.grakovne.lissen.wear"
    minSdk = 31
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_21
    }
  }
  useLibrary("wear-sdk")
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":lib"))

  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.hilt.android)
  implementation(libs.androidx.runtime.livedata)
  ksp(libs.hilt.android.compiler)

  implementation(libs.play.services.wearable)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.wear.compose.material)
  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.androidx.wear.tooling.preview)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.mediarouter)
  implementation(libs.horologist.compose.layout)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.wear.compose.navigation)
  implementation(libs.horologist.ui.media)
  implementation(libs.horologist.ui.audio)
  implementation(libs.horologist.audio)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

}