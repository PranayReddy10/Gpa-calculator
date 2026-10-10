import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Keys for this machine live in android/local.properties (never committed). A -P flag or a
// ~/.gradle/gradle.properties entry with the same name takes precedence.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// .properties files have no end-of-line comments, so "VERSION_CODE=14   # note" would read the note
// as part of the value. Drop anything after whitespace + '#'.
fun setting(name: String, default: String): String =
    (project.findProperty(name)?.toString() ?: localProps.getProperty(name) ?: default)
        .replace(Regex("""\s+#.*$"""), "").trim()

// The Google Mobile Ads SDK crashes at app start on a malformed app ID, so never ship one:
// anything that is not "ca-app-pub-<digits>~<digits>" (placeholder, empty, an ad UNIT id with "/")
// falls back to Google's official test app ID with a build warning.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobAppId: String = setting("ADMOB_APP_ID", "").let { id ->
    if (Regex("""^ca-app-pub-\d{16}~\d{10}$""").matches(id)) id
    else {
        if (id.isNotEmpty()) logger.warn("w: ADMOB_APP_ID '$id' is not a valid AdMob app ID (ca-app-pub-<16 digits>~<10 digits>). Using Google's test app ID instead.")
        admobTestAppId
    }
}

// ---------------------------------------------------------------------------------------------
// Release safety: a Play Store build must not ship with test or placeholder settings.
// Checked right before any release build (bundleRelease / assembleRelease); debug builds are
// never blocked. For a local test of the minified build only: -PALLOW_UNSAFE_RELEASE=true
// (never upload such a build).
// ---------------------------------------------------------------------------------------------
val releaseStoreFile: String = setting("RELEASE_STORE_FILE", "")
val releaseProblems: List<String> = buildList {
    val admob = setting("ADMOB_APP_ID", "")
    if (!Regex("""^ca-app-pub-\d{16}~\d{10}$""").matches(admob) || admob == admobTestAppId)
        add("ADMOB_APP_ID must be your real AdMob app ID (AdMob > Apps > App settings, contains '~'), not Google's test ID.")
    val key = setting("APP_API_KEY", "")
    if (key.isEmpty() || key.startsWith("change-me") || key.length < 16)
        add("APP_API_KEY must be set to the same long random value as APP_API_KEY in backend/config.php.")
    val api = setting("API_BASE_URL", "https://gpa.jntubooks.in/")
    if (!api.startsWith("https://") || api.contains("example.com"))
        add("API_BASE_URL must be your https backend URL, e.g. https://gpa.jntubooks.in/")
    if (releaseStoreFile.isEmpty()) {
        add("RELEASE_STORE_FILE is not set: point it at the keystore you use for Google Play (upload key).")
    } else {
        if (!rootProject.file(releaseStoreFile).exists()) add("RELEASE_STORE_FILE '$releaseStoreFile' does not exist.")
        if (setting("RELEASE_STORE_PASSWORD", "").isEmpty()) add("RELEASE_STORE_PASSWORD is empty.")
        if (setting("RELEASE_KEY_ALIAS", "").isEmpty()) add("RELEASE_KEY_ALIAS is empty.")
        if (setting("RELEASE_KEY_PASSWORD", "").isEmpty()) add("RELEASE_KEY_PASSWORD is empty.")
    }
}
val allowUnsafeRelease: Boolean = setting("ALLOW_UNSAFE_RELEASE", "false") == "true"

android {
    namespace = "com.gpa.calculator"
    compileSdk = 36

    defaultConfig {
        // Keep the same applicationId as the published app so this ships as an update.
        applicationId = "com.gpa.calculator"
        minSdk = 26
        targetSdk = 36
        // Must be higher than the versionCode currently live on Google Play (Play Console >
        // App bundle explorer). Override per release in local.properties: VERSION_CODE / VERSION_NAME.
        versionCode = setting("VERSION_CODE", "100").let {
            it.toIntOrNull() ?: throw GradleException("VERSION_CODE must be a whole number (e.g. VERSION_CODE=15), got '$it'.")
        }
        versionName = setting("VERSION_NAME", "2.0.0")

        // --- Build-time configuration: set these in android/local.properties (see local.properties.sample) ---
        // Backend base URL where backend/ is deployed (production: https://gpa.jntubooks.in/)
        buildConfigField("String", "API_BASE_URL", "\"${setting("API_BASE_URL", "https://gpa.jntubooks.in/")}\"")
        // Shared secret that must match APP_API_KEY in backend/config.php
        buildConfigField("String", "APP_API_KEY", "\"${setting("APP_API_KEY", "change-me-app-key")}\"")
        // AdMob application ID (NOT an ad unit). Google test app id by default.
        manifestPlaceholders["admobAppId"] = admobAppId
        // OneSignal app ID (public). Can also be set/overridden from the admin panel (App tab).
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${setting("ONESIGNAL_APP_ID", "")}\"")
        // AppLovin MAX SDK key (leave empty to disable MAX entirely)
        manifestPlaceholders["applovinSdkKey"] = setting("APPLOVIN_SDK_KEY", "")

        vectorDrawables.useSupportLibrary = true
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = setting("RELEASE_STORE_PASSWORD", "")
                keyAlias = setting("RELEASE_KEY_ALIAS", "")
                keyPassword = setting("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.preference)

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    // Ads: Google AdMob, AppLovin MAX, Meta Audience Network (selected at runtime by admin panel config)
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.applovin.sdk)
    implementation(libs.audience.network)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Push notifications + in-app messages
    implementation(libs.onesignal)

    // Play in-app review and in-app update
    implementation(libs.play.review)
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
}

// Fail fast before any release build if production settings are missing (see releaseProblems).
// The task action captures only plain local values so it works with the configuration cache.
tasks.configureEach {
    if (name == "preReleaseBuild") {
        val problems: List<String> = ArrayList(releaseProblems)
        val allowUnsafe: Boolean = allowUnsafeRelease
        doFirst {
            if (problems.isNotEmpty() && !allowUnsafe) {
                throw GradleException(
                    "Release build blocked — fix these in android/local.properties:\n" +
                        problems.joinToString("\n") { "  • $it" } +
                        "\n(See RELEASE.md. Debug builds are not affected.)"
                )
            }
        }
    }
}
