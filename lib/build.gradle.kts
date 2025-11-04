import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
	
	id("com.google.devtools.ksp")
}

android {
	namespace = "org.grakovne.lissen.lib"
	compileSdk = 36
	
	defaultConfig {
		minSdk = 28
		
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}
	
	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
	kotlin {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.material)
	
	implementation(libs.converter.moshi)
	implementation(libs.moshi)
	implementation(libs.moshi.kotlin)
	
	ksp(libs.moshi.kotlin.codegen)
}