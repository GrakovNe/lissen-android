plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "org.grakovne.lissen.macrobenchmark"
  compileSdk = 37
  buildToolsVersion = "36.0.0"

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  defaultConfig {
    minSdk = 28
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    testInstrumentationRunnerArguments["class"] =
      "org.grakovne.lissen.macrobenchmark.BaselineProfileGenerator"
    listOf("serverUrl", "username", "password").forEach { key ->
      (project.findProperty(key) as String?)?.let { testInstrumentationRunnerArguments[key] = it }
    }
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks.add("release")
    }
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
  beforeVariants(selector().all()) {
    it.enable = it.buildType == "benchmark"
  }
}

tasks.register<Copy>("generateLissenBaselineProfile") {
  group = "baseline profile"
  description =
    "Runs BaselineProfileGenerator on a connected device and copies the profile to " +
      "app/src/main/baseline-prof.txt. Pass -PserverUrl=.. -Pusername=.. -Ppassword=.."

  dependsOn("connectedBenchmarkAndroidTest")

  from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output/benchmark/connected")) {
    include("**/BaselineProfileGenerator_generate-startup-prof.txt")
    eachFile { path = name }
    rename { "baseline-prof.txt" }
  }
  into(rootProject.file("app/src/main"))
  includeEmptyDirs = false
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
