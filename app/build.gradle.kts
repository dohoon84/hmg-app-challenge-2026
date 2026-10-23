import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

fun signingProp(name: String): String? =
    keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingProp("STORE_FILE")
val releaseStorePassword = signingProp("STORE_PASSWORD")
val releaseKeyAlias = signingProp("KEY_ALIAS")
val releaseKeyPassword = signingProp("KEY_PASSWORD")
val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile != null &&
    releaseStoreFile.isFile &&
    releaseStorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

if (releaseStorePath != null && !hasReleaseSigning) {
    logger.warn("Release signing is incomplete. The release APK will be unsigned.")
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

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val carJar = android.sdkDirectory.resolve("platforms/android-${android.compileSdk}/optional/android.car.jar")
    if (!carJar.isFile) {
        throw GradleException(
            "android.car.jar not found at $carJar. Install platforms;android-${android.compileSdk}.",
        )
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(files(carJar))
}

val contestSigningMessage =
    "exportContestApk requires a signed pleos release. " +
        "Set STORE_FILE, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD " +
        "in keystore.properties or the environment. Do not submit a fake or unsigned APK."

// Fails at the start of execution, before assemblePleosRelease does any work.
// A throw while configuring the build script would fail every Gradle command.
// onlyIf would skip the export instead of failing it. doLast on the copy runs
// only after assemblePleosRelease, which is the work we want to avoid.
val checkContestSigning = tasks.register("checkContestSigning") {
    group = "verification"
    description = "Reject a contest export before the release build when signing is not configured."
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(contestSigningMessage)
        }
    }
}

tasks.configureEach {
    if (name == "assemblePleosRelease") {
        mustRunAfter(checkContestSigning)
    }
}

tasks.register<Copy>("exportContestApk") {
    group = "distribution"
    description = "Copy a signed pleos release APK into apk/. Fails when release signing is not configured."
    dependsOn(checkContestSigning, "assemblePleosRelease")
    from(layout.buildDirectory.dir("outputs/apk/pleos/release"))
    include("*-release.apk")
    exclude("*unaligned*")
    exclude("*unsigned*")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { "placeholder-team_foundation_release.apk" }
}
