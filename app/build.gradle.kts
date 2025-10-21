import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    id("com.google.dagger.hilt.android")
    id("org.jmailen.kotlinter") version "5.1.0"
    id("com.google.devtools.ksp")
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
    ignoreFormatFailures = false
    ignoreLintFailures = false
}


tasks.lintKotlinMain {
    dependsOn(tasks.formatKotlinMain)
}

val localProperties = Properties().apply {
    rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.let { file -> file.inputStream().use { load(it) } }
}

android {
  namespace = "org.grakovne.lissen"
  compileSdk = 36
  
  lint {
        disable.add("MissingTranslation")
    }

    defaultConfig {
        applicationId = "org.grakovne.lissen"
        minSdk = 28
        targetSdk = 36
        versionCode = 10702
        versionName = "1.7.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        val acraReportLogin = "VxOasuhbz9DP5HTy"
        val acraReportPassword = "21E1sv6utE36sbpm"

        buildConfigField("String", "ACRA_REPORT_LOGIN", "\"$acraReportLogin\"")
        buildConfigField("String", "ACRA_REPORT_PASSWORD", "\"$acraReportPassword\"")

        if (project.hasProperty("RELEASE_STORE_FILE")) {
            signingConfigs {
                create("release") {
                    storeFile = file(project.property("RELEASE_STORE_FILE")!!)
                    storePassword = project.property("RELEASE_STORE_PASSWORD") as String?
                    keyAlias = project.property("RELEASE_KEY_ALIAS") as String?
                    keyPassword = project.property("RELEASE_KEY_PASSWORD") as String?

                    // Optional, specify signing versions used
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
                    // Includes the default ProGuard rules files that are packaged with
                    // the Android Gradle plugin. To learn more, go to the section about
                    // R8 configuration files.
                    getDefaultProguardFile("proguard-android-optimize.txt"),

                    // Includes a local, custom Proguard rules file
                    "proguard-rules.pro"
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
    kotlinOptions {
        jvmTarget = "21"
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
  buildToolsVersion = "35.0.0"
  
}

dependencies {
    implementation(project(":lib"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.material3)

    implementation(libs.androidx.material)
    implementation(libs.compose.shimmer.android)

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation (libs.hoko.blur)

    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    ksp(libs.hilt.android.compiler)

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
    implementation (libs.timber)

    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.acra.core)
    implementation(libs.acra.http)
    implementation(libs.acra.toast)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
