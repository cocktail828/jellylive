plugins {
	id("com.android.application")
	kotlin("android")
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.aboutlibraries)
}

android {
	buildToolsVersion = "36.0.0"
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		applicationId = namespace
		versionName = "v${project.getVersionName()}"
		versionCode = getVersionCode(project.getVersionName())
	}

    // Opt-in local build inputs, mounted read-only by scripts/build-container.sh.
    providers.gradleProperty("jellylive.bootstrapDir").orNull?.let { path ->
        val directory = file(path)
        val names = setOf("bestv-bootstrap.json", "jellyfin-bootstrap.json")
        val inputs = directory.listFiles()?.toList().orEmpty()
        require(directory.isDirectory && inputs.isNotEmpty() && inputs.all { it.isFile && it.name in names }) {
            "Bootstrap directory must contain only the supported local JSON files."
        }
        inputs.forEach { input ->
            val limit = if (input.name == "jellyfin-bootstrap.json") 64 * 1024L else 1024 * 1024L
            require(input.length() in 1..limit) { "Invalid local bootstrap file size: ${input.name}" }
            val value = try { groovy.json.JsonSlurper().parse(input) as? Map<*, *> }
                catch (_: Exception) { null }
            require(!value.isNullOrEmpty()) { "Invalid local bootstrap JSON: ${input.name}" }
            if (input.name == "jellyfin-bootstrap.json") {
                require(value["address"] is String && (value["address"] as String).isNotBlank() &&
                    value["username"] is String && (value["username"] as String).isNotBlank() && value["password"] is String) {
                    "Library bootstrap requires address, username and password strings."
                }
            }
        }
        sourceSets.getByName("main").assets.srcDir(directory)
    }

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

    providers.gradleProperty("jellylive.debugKeystore").orNull?.let { path ->
        signingConfigs.getByName("debug") {
            storeFile = file(path)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
	signingConfigs {
		val keystoreFile = getProperty("keystore.file")
		val keystorePassword = getProperty("keystore.password")
		val signingKeyAlias = getProperty("signing.key.alias")
		val signingKeyPassword = getProperty("signing.key.password")

		if (keystoreFile != null && keystorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
			create("release") {
				storeFile = file(keystoreFile)
				storePassword = keystorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
	}

	buildTypes {
		release {
			isMinifyEnabled = false

			// Set package names used in various XML files
			resValue("string", "app_id", namespace!!)
			resValue("string", "app_search_suggest_authority", "${namespace}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${namespace}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			buildConfigField("boolean", "DEVELOPMENT", "false")

			signingConfig = signingConfigs.findByName("release")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".bestvlive"

			// Set package names used in various XML files
			resValue("string", "app_id", namespace + applicationIdSuffix)
			resValue("string", "app_search_suggest_authority", "${namespace + applicationIdSuffix}.content")
			resValue("string", "app_search_suggest_intent_data", "content://${namespace + applicationIdSuffix}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
		}
	}

	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		sarifReport = true
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

base.archivesName.set("jellyfin-androidtv-v${project.getVersionName()}")

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.accompanist.permissions)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
