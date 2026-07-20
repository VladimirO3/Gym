plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	id("com.google.gms.google-services")
	id("com.google.firebase.crashlytics")
}

android {
	namespace = "com.business.gym"
	compileSdk {
		version = release(37) {
			minorApiLevel = 1
		}
	}

	defaultConfig {
		applicationId = "com.business.gym"
		minSdk = 23
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			optimization {
				enable = false
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.adaptive.navigation.suite)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation("androidx.constraintlayout:constraintlayout:2.2.1")
	implementation("androidx.media3:media3-exoplayer:1.1.1")
	implementation("androidx.media3:media3-ui:1.1.1")
	implementation("androidx.media3:media3-common:1.1.1")
	implementation("androidx.media3:media3-session:1.1.1")
	implementation("androidx.activity:activity-ktx:1.8.0")
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)

	// Firebase
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.database)
	implementation(libs.firebase.storage)
	implementation(libs.firebase.analytics)
	implementation(libs.firebase.crashlytics)
	implementation(libs.firebase.auth)
	implementation("com.google.android.gms:play-services-auth:21.3.0")
	implementation("io.coil-kt:coil-compose:2.5.0")

	testImplementation(libs.junit)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}