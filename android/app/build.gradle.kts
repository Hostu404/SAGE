plugins {
    alias(libs.plugins.android.application)
    // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hostu404.sage"
    // 37 because current Compose libraries require it; targetSdk stays at 35.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hostu404.sage"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.3"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            // Signed with the debug key so it can be installed straight from
            // the file. A Play Store build would use a real upload key.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.maxHeapSize = "3g"
            // Screenshot tests write their PNGs to app/build/outputs/roborazzi.
            test.systemProperty("roborazzi.test.record", "true")
            providers.gradleProperty("robolectricRepo").orNull?.let { test.systemProperty("robolectric.dependency.repo.url", it) }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
