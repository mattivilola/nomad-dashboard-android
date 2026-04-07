import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.play.publisher)
}

val versionProperties = Properties().apply {
    rootProject.file("Config/version.properties").inputStream().use(::load)
}

fun env(name: String, default: String = ""): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "com.iloapps.nomaddashboard"
    compileSdk = 36

    defaultConfig {
        applicationId = env("NOMAD_APPLICATION_ID", "com.iloapps.nomaddashboard")
        minSdk = 30
        targetSdk = 36
        versionCode = versionProperties.getProperty("VERSION_CODE").toInt()
        versionName = versionProperties.getProperty("MARKETING_VERSION")
        testInstrumentationRunner = "com.iloapps.nomaddashboard.NomadDashboardTestRunner"
        vectorDrawables.useSupportLibrary = true

        manifestPlaceholders["nomadMapsApiKey"] = env("NOMAD_MAPS_API_KEY")
        manifestPlaceholders["nomadPlacesApiKey"] = env("NOMAD_PLACES_API_KEY")

        buildConfigField("String", "RELIEFWEB_APP_NAME", "\"${env("NOMAD_RELIEFWEB_APP_NAME")}\"")
        buildConfigField("String", "TELEMETRYDECK_APP_ID", "\"${env("NOMAD_TELEMETRYDECK_APP_ID")}\"")
    }

    signingConfigs {
        if (env("NOMAD_KEYSTORE_PATH").isNotBlank()) {
            create("release") {
                storeFile = file(env("NOMAD_KEYSTORE_PATH"))
                storePassword = env("NOMAD_KEYSTORE_PASSWORD")
                keyAlias = env("NOMAD_KEY_ALIAS")
                keyPassword = env("NOMAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = env("NOMAD_DEBUG_APPLICATION_ID_SUFFIX", ".dev")
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

play {
    serviceAccountCredentials.set(file(env("NOMAD_PLAY_SERVICE_ACCOUNT_JSON", "Config/nonexistent-play-service-account.json")))
    track.set(env("NOMAD_PLAY_TRACK", "internal"))
    releaseStatus.set(ReleaseStatus.COMPLETED)
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.feature.about)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.settings)
    implementation(projects.feature.timetracking)
    implementation(projects.feature.visited)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.material)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.window.size)

    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
