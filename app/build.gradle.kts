plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	id("com.google.gms.google-services")
	id("com.google.firebase.crashlytics")
	id("com.google.devtools.ksp")
}

android {
	namespace = "com.business.gym"
	compileSdk = 37

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
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		isCoreLibraryDesugaringEnabled = true
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
}

dependencies {
	coreLibraryDesugaring(libs.desugarlibs)
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
	implementation("androidx.profileinstaller:profileinstaller:1.4.1")

	// Room
	implementation(libs.room.runtime)
	implementation(libs.room.ktx)
	ksp(libs.room.compiler)

	// Billing
	implementation(libs.billing)
	implementation(libs.billing.ktx)

	// Firebase
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.database)
	implementation(libs.firebase.firestore)
	implementation(libs.firebase.storage)
	implementation(libs.firebase.analytics)
	implementation(libs.firebase.crashlytics)
	implementation(libs.firebase.auth)
	implementation(libs.play.services.auth)
	implementation(libs.coil.compose)

	// Retrofit API
	implementation(libs.retrofit)
	implementation(libs.retrofit.gson)

	testImplementation(libs.junit)
	testImplementation(libs.mockito.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}