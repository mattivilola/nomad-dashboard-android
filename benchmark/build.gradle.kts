plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
    kotlin("android")
}

android {
    namespace = "com.iloapps.nomaddashboard.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // com.android.test packages instrumentation sources from main.
    sourceSets.getByName("main").java.srcDir("src/androidTest/java")
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
