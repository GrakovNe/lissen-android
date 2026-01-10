import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  
  id("com.google.dagger.hilt.android")
  id("org.jmailen.kotlinter") version "5.2.0"
  id("com.google.devtools.ksp")
}

kotlinter {
  reporters = arrayOf("checkstyle", "plain")
  ignoreFormatFailures = false
  ignoreLintFailures = false
}

val localProperties = Properties().apply {
  rootProject.file("local.properties").takeIf { it.exists() }?.let { file -> file.inputStream().use { load(it) } }
}

tasks.named("preBuild") {
  dependsOn("formatKotlin")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

fun gitCommitHash(): String {
  return try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
      .redirectErrorStream(true)
      .start()
    process.inputStream.bufferedReader().use { it.readText().trim() }
  } catch (e: Exception) {
    "stable"
  }
}

android {
  namespace = "org.grakovne.lissen"
  compileSdk = 36
  
  lint {
    disable.add("MissingTranslation")
  }
  
  defaultConfig {
    val commitHash = gitCommitHash()
    
    applicationId = "org.grakovne.lissen"
    minSdk = 28
    targetSdk = 36
    versionCode = 10800
    versionName = "1.8.1-$commitHash"
    
    buildConfigField("String", "GIT_HASH", "\"$commitHash\"")
    
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    
    val acraReportLogin = "8yJ59n0UToCja8LR"
    val acraReportPassword = "kuW9TV7BbJByuIAc"
    
    buildConfigField("String", "ACRA_REPORT_LOGIN", "\"$acraReportLogin\"")
    buildConfigField("String", "ACRA_REPORT_PASSWORD", "\"$acraReportPassword\"")
    
    if (project.hasProperty("RELEASE_STORE_FILE")) {
      signingConfigs {
        create("release") {
          storeFile = file(project.property("RELEASE_STORE_FILE")!!)
          storePassword = project.property("RELEASE_STORE_PASSWORD") as String?
          keyAlias = project.property("RELEASE_KEY_ALIAS") as String?
          keyPassword = project.property("RELEASE_KEY_PASSWORD") as String?
          enableV1Signing = true
          enableV2Signing = true
        }
      }
    }
  }

  
  buildTypes {
    release {
      if (project.hasProperty("RELEASE_STORE_FILE")) {
        signingConfig = signingConfigs.getByName("release")
      }
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
      )
    }
    debug {
      applicationIdSuffix = ".debug"
      versionNameSuffix = " (DEBUG)"
      matchingFallbacks.add("release")
      isDebuggable = true
    }
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  kotlin {
    jvmToolchain(21)
  }
  buildFeatures {
    buildConfig = true
    compose = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1,MIT}"
    }
  }
  buildToolsVersion = "36.0.0"
  
}

dependencies {
  implementation(project(":lib"))
  
  implementation(libs.androidx.navigation.compose)
  implementation(libs.material)
  implementation(libs.material3)
  
  implementation(libs.androidx.material)
  implementation(libs.compose.shimmer.android)
  
  implementation(libs.retrofit)
  implementation(libs.logging.interceptor)
  implementation(libs.okhttp)
  implementation(libs.androidx.browser)
  
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.hoko.blur)
  
  implementation(libs.androidx.paging.compose)
  
  implementation(libs.androidx.compose.material.icons.extended)
  
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.hilt.android)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.lifecycle.process)
  
  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.android.compiler)
  ksp(libs.moshi.kotlin.codegen)
  
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.runtime.livedata)
  
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.database)
  
  implementation(libs.androidx.localbroadcastmanager)
  implementation(libs.timber)
  
  implementation(libs.androidx.glance)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  
  implementation(libs.acra.core)
  implementation(libs.acra.http)
  implementation(libs.acra.toast)
  
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  
  implementation(libs.converter.moshi)
  implementation(libs.moshi)
  implementation(libs.moshi.kotlin)
  
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
