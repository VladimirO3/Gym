import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	id("com.google.gms.google-services")
	id("com.google.firebase.crashlytics")
	id("com.google.devtools.ksp")
}

android {
	namespace = "com.business.gym_app"
	compileSdk = 37

	val versionPropsFile = file("version.properties")
	val versionProps = Properties()
	if (!versionPropsFile.exists()) {
		versionPropsFile.createNewFile()
		versionProps["versionCode"] = "1"
		versionProps["versionName"] = "1.0"
		versionProps.store(versionPropsFile.writer(), null)
	}
	versionProps.load(versionPropsFile.reader())
	val currentVersionCode = versionProps.getProperty("versionCode").toInt()
	val nextVersionCode = currentVersionCode + 1
	versionProps["versionCode"] = nextVersionCode.toString()
	versionProps["versionName"] = "1.$nextVersionCode"
	versionProps.store(versionPropsFile.writer(), null)

	// Подпись release-сборки.
	// Пароли и путь к keystore лежат в keystore.properties (файл НЕ коммитится, см. .gitignore).
	// Как создать:
	//   keytool -genkeypair -v -keystore gym-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias gym
	// и заполнить keystore.properties (образец — keystore.properties.example).
	val keystorePropsFile = rootProject.file("keystore.properties")
	val keystoreProps = Properties()
	if (keystorePropsFile.exists()) {
		keystorePropsFile.inputStream().use { keystoreProps.load(it) }
	}

	// Секреты API лежат в .env в корне проекта (файл НЕ коммитится, см. .gitignore;
	// шаблон — .env.example). Значения попадают в BuildConfig на этапе сборки,
	// поэтому в исходном коде и ресурсах ключей нет.
	// Переменная окружения с тем же именем имеет приоритет (для CI).
	val envFile = rootProject.file(".env")
	val envProps = Properties()
	if (envFile.exists()) {
		envFile.inputStream().use { envProps.load(it) }
	}
	val translateApiKey = (
		System.getenv("GOOGLE_TRANSLATE_API_KEY")?.takeIf { it.isNotBlank() }
			?: envProps.getProperty("GOOGLE_TRANSLATE_API_KEY")
		).orEmpty().trim()

	signingConfigs {
		if (keystorePropsFile.exists()) {
			create("release") {
				storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "gym-release.jks"))
				storePassword = keystoreProps.getProperty("storePassword")
				keyAlias = keystoreProps.getProperty("keyAlias", "gym")
				keyPassword = keystoreProps.getProperty("keyPassword")
			}
		}
	}

	defaultConfig {
		applicationId = "com.business.gym_app"
		minSdk = 24
		targetSdk = 36
		versionCode = currentVersionCode
		versionName = "1.$currentVersionCode"

		// Ключ Google Cloud Translation — из .env (см. .env.example), а не из кода/ресурсов.
		buildConfigField(
			"String",
			"GOOGLE_TRANSLATE_API_KEY",
			"\"" + translateApiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
		)

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
			ndk {
				debugSymbolLevel = "FULL"
			}
			// Подпись подключается только если keystore.properties существует;
			// иначе assembleRelease даст unsigned APK (как раньше), сборка не упадёт.
			// debug-ключом release НЕ подписываем — чтобы случайно не загрузить его в Play.
			signingConfig = if (keystorePropsFile.exists()) {
				signingConfigs.getByName("release")
			} else {
				null
			}
		}
	}
	compileOptions {
		isCoreLibraryDesugaringEnabled = true
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
		// Нужен для buildConfigField с секретами из .env (см. app/build.gradle.kts).
		buildConfig = true
	}
	testOptions {
		// android.util.Log и прочие заглушки android.jar в JVM-тестах возвращают значения по умолчанию
		unitTests.isReturnDefaultValues = true
	}
}

dependencies {
	implementation(libs.firebase.crashlytics.buildtools)
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
	implementation("androidx.activity:activity-ktx:1.13.0")
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation("androidx.profileinstaller:profileinstaller:1.4.1")
	implementation("androidx.biometric:biometric:1.1.0")
	implementation("androidx.work:work-runtime-ktx:2.10.1")

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
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)

	val ktor_version = "2.3.12"
	implementation("io.ktor:ktor-client-core:$ktor_version")
	implementation("io.ktor:ktor-client-okhttp:$ktor_version")
	implementation("io.ktor:ktor-client-websockets:$ktor_version")
	implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
	implementation("io.ktor:ktor-serialization-gson:$ktor_version")
	implementation("io.ktor:ktor-client-cio:$ktor_version")
	// Retrofit для HTTP запросов
	implementation("com.squareup.retrofit2:retrofit:2.9.0")
	implementation("com.squareup.retrofit2:converter-gson:2.9.0")

	// OkHttp для перехвата запросов (добавление токена)
	implementation("com.squareup.okhttp3:okhttp:4.11.0")

	// Coroutines для асинхронности
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

	// Glide или Coil для загрузки изображений из /uploads
	implementation("io.coil-kt:coil:2.4.0")
}