plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "org.grakovne.lissen.minifiedtest"
  compileSdk = 37
  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true

  defaultConfig {
    minSdk = 28
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }

  buildTypes {
    create("minified") {
      isDebuggable = false
      signingConfig = signingConfigs.getByName("debug")
    }
  }
}

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(25)
  }
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.test.uiautomator.shell)
}
