plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.gpa.calculator"
    compileSdk = 36

    defaultConfig {
        // Keep the same applicationId as the published app so this ships as an update.
        applicationId = "com.gpa.calculator"
        minSdk = 26
        targetSdk = 36
        // Must be higher than the versionCode currently live on Google Play.
        versionCode = 100
        versionName = "2.0.0"

        // --- Build-time configuration (override in local.properties / CI env) ---
        // Backend base URL where backend/ is deployed, e.g. https://example.com/gpa-api/
        buildConfigField("String", "API_BASE_URL", "\"${project.findProperty("API_BASE_URL") ?: "https://example.com/gpa-api/"}\"")
        // Shared secret that must match APP_API_KEY in backend/config.php
        buildConfigField("String", "APP_API_KEY", "\"${project.findProperty("APP_API_KEY") ?: "change-me-app-key"}\"")
        // AdMob application ID (NOT an ad unit). Google test app id by default.
        manifestPlaceholders["admobAppId"] = project.findProperty("ADMOB_APP_ID") ?: "ca-app-pub-3940256099942544~3347511713"
        // AppLovin MAX SDK key (leave empty to disable MAX entirely)
        manifestPlaceholders["applovinSdkKey"] = project.findProperty("APPLOVIN_SDK_KEY") ?: ""

        vectorDrawables.useSupportLibrary = true
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["room.incremental"] = "true"
            }
        }
    }

    buildTypes {
        release {
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

    // Play in-app review and in-app update
    implementation(libs.play.review)
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
}
