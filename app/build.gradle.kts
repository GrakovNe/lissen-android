import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  
  id("com.google.dagger.hilt.android")
  id("org.jmailen.kotlinter") version "5.5.0"
  id("com.google.devtools.ksp")
  id("kotlin-parcelize")
  id("com.starter.easylauncher") version "6.4.1"
}

easylauncher {
  buildTypes {
    register("debug") {
      filters(chromeLike(label = "DEBUG", ribbonColor = "#FF6F3F", labelColor = "#FFFFFF", labelPadding = 15))
    }
  }
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

tasks.withType<JavaCompile>().configureEach {
  if (name.startsWith("hiltJavaCompile")) {
    doFirst {
      val original = options.annotationProcessorPath ?: return@doFirst
      options.annotationProcessorPath = original.filter { !it.name.contains("moshi-kotlin-codegen") }
    }
  }
}

configurations.all {
  resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

android {
  namespace = "org.grakovne.lissen"
  compileSdk = 37
  
  lint {
    disable.add("MissingTranslation")
  }
  
  defaultConfig {
    applicationId = "org.grakovne.lissen"
    minSdk = 28
    targetSdk = 37
    versionCode = 11011
    versionName = "1.10.12-release"
    
    testInstrumentationRunner = "org.grakovne.lissen.HiltTestRunner"
    
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
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }
  
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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
  testOptions {
    packaging {
      resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/LICENSE-notice.md"
      }
    }
  }
  buildToolsVersion = "36.0.0"
  
  testOptions {
    unitTests.all {
      it.useJUnitPlatform()
    }
  }
}

dependencies {
  implementation(libs.androidx.navigation.compose)
  implementation(libs.material)
  implementation(libs.material3)
  
  implementation(libs.androidx.media3.ffmpeg.decoder)
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
  
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.exoplayer.dash)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.datasource)
  implementation(libs.androidx.media3.database)
  
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
  implementation(libs.zip4j)
  
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
  
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testRuntimeOnly(libs.junit.platform.launcher)
  
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.hilt.android.testing)
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0-alpha01")
  kspAndroidTest(libs.hilt.android.compiler)
}
