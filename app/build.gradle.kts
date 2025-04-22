import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)

    id("com.google.dagger.hilt.android")
    id("org.jmailen.kotlinter") version "3.15.0"
    id("com.google.devtools.ksp")
}

kotlinter {
    reporters = arrayOf("checkstyle", "plain")
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

val versionNameStr = project.properties["VERSION_NAME"] as String
val (major, minor, patch) = versionNameStr.split(".").map { it.toInt() }

android {
    namespace = "org.grakovne.lissen"
    compileSdk = 35

    lint {
        disable.add("MissingTranslation")
    }

    defaultConfig {
        applicationId = "org.grakovne.lissen"
        minSdk = 28
        targetSdk = 35
        versionName = versionNameStr
        versionCode = major * 10000 + minor * 100 + patch

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        val acraReportLogin = localProperties.getProperty("acra.report.login") ?: ""
        val acraReportPassword = localProperties.getProperty("acra.report.password") ?: ""

        buildConfigField("String", "ACRA_REPORT_LOGIN", "\"$acraReportLogin\"")
        buildConfigField("String", "ACRA_REPORT_PASSWORD", "\"$acraReportPassword\"")
    }

    buildTypes {
        release {}
        debug {
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

}

dependencies {

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

    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.lifecycle.service)
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