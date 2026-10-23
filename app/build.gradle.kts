plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "io.hmg.appchallenge.foundation"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.hmg.appchallenge.foundation"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-phase0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AAOS Car API (GEAR_SELECTION, CarUxRestrictions, optional RANGE_REMAINING).
    // Compile-only optional library. Fake builds never load these classes.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "sdk"
    productFlavors {
        create("fake") {
            dimension = "sdk"
            applicationIdSuffix = ".fake"
            versionNameSuffix = "-fake"
            manifestPlaceholders["automotiveRequired"] = "false"
            buildConfigField("boolean", "USES_AAOS_CAR", "false")
            buildConfigField("String", "SDK_FLAVOR", "\"fake\"")
        }
        create("pleos") {
            dimension = "sdk"
            manifestPlaceholders["automotiveRequired"] = "true"
            buildConfigField("boolean", "USES_AAOS_CAR", "true")
            buildConfigField("String", "SDK_FLAVOR", "\"pleos\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Placeholder signing so a release APK can be produced before the team keystore exists.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    lint {
        abortOnError = true
        // The contest image is API 34. A newer Play target is out of scope for this phase.
        disable += "ExpiredTargetSdkVersion"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.ui.tooling)

    "pleosImplementation"(libs.pleos.vehicle)
    "pleosImplementation"(libs.pleos.navi)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(
        files(android.sdkDirectory.resolve("platforms/android-34/optional/android.car.jar")),
    )
}

tasks.register<Copy>("exportContestApk") {
    group = "distribution"
    description = "Copy the pleos release APK into apk/ with the contest file name."
    dependsOn("assemblePleosRelease")
    from(layout.buildDirectory.dir("outputs/apk/pleos/release"))
    include("*.apk")
    exclude("*unaligned*")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "placeholder-team_foundation_release.apk" }
}
