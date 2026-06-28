buildscript {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
	dependencies {
		classpath("com.project.starter:easylauncher:6.4.1")
		classpath("com.github.usefulness:webp-imageio:0.11.0")
	}
}

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.hilt.android) apply false
	id("com.google.devtools.ksp") version "2.3.8" apply false
	alias(libs.plugins.compose.compiler) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.android.test) apply false
}