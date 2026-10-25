import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.Action
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipFile

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

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun loadSecretFile(): Properties {
    val path = System.getenv("PLEOS_SECRETS_FILE")?.takeIf { it.isNotBlank() } ?: "secrets/pleos-miri.env"
    val file = rootProject.file(path)
    val props = Properties()
    if (file.isFile) file.inputStream().use { props.load(it) }
    return props
}

fun secret(name: String): String {
    System.getenv(name)?.takeIf { it.isNotBlank() }?.let { return it }
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }?.let { return it }
    return loadSecretFile().getProperty(name)?.takeIf { it.isNotBlank() } ?: ""
}

fun javaLiteral(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

fun gearBackendLiteral(): String {
    val raw = System.getenv("MIRI_GEAR_BACKEND")?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty("MIRI_GEAR_BACKEND")?.takeIf { it.isNotBlank() }
        ?: "AAOS"
    return if (raw.equals("PLEOS", ignoreCase = true)) "PLEOS" else "AAOS"
}

// Default team segment. With no -Pteam, exportContestApk writes apk/krazylabs_miri_release.apk.
val teamName = (findProperty("team") as? String)?.takeIf { it.isNotBlank() } ?: "krazylabs"
val contestApkName = "${teamName}_miri_release.apk"

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

fun localProp(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val debugStorePath = localProp("DEBUG_STORE_FILE")
val debugStorePassword = localProp("DEBUG_STORE_PASSWORD")
val debugKeyAlias = localProp("DEBUG_KEY_ALIAS")
val debugKeyPassword = localProp("DEBUG_KEY_PASSWORD")
val debugStoreFile = debugStorePath?.let { rootProject.file(it) }
val hasDebugSigning = debugStoreFile != null &&
    debugStoreFile.isFile &&
    debugStorePassword != null &&
    debugKeyAlias != null &&
    debugKeyPassword != null

if (debugStorePath != null && !hasDebugSigning) {
    logger.warn("Debug signing is incomplete. The debug APK will use the Android debug key.")
}

android {
    namespace = "com.krazylabs.miri"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.krazylabs.miri"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.2.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // M6 can flip this to NAVIGATION_GUIDANCE. The debug panel overrides it at runtime.
        buildConfigField("String", "SPEECH_USAGE", "\"SONIFICATION\"")
        // F3 '반납 장소로 안내'. Debug overrides this to true. Release stays false.
        buildConfigField("boolean", "RETURN_GUIDANCE", "false")
        // Filming mode for a demo video. Debug overrides this to true. Release stays false.
        buildConfigField("boolean", "FILMING", "false")
    }

    // AAOS Car API (GEAR_SELECTION, CarUxRestrictions, INFO_* identity).
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
            buildConfigField("String", "GEAR_BACKEND", javaLiteral(gearBackendLiteral()))
        }
        create("pleos") {
            dimension = "sdk"
            manifestPlaceholders["automotiveRequired"] = "true"
            buildConfigField("boolean", "USES_AAOS_CAR", "true")
            buildConfigField("String", "SDK_FLAVOR", "\"pleos\"")
            buildConfigField("String", "GEAR_BACKEND", javaLiteral(gearBackendLiteral()))
            buildConfigField("String", "PLEOS_PROJECT_ID", javaLiteral(secret("PLEOS_PROJECT_ID")))
        }
    }

    signingConfigs {
        // Debug only. A box may set DEBUG_STORE_* in local.properties (gitignored)
        // so emulator installs share one signature. Missing values keep the
        // Android debug key. Release signing below is unchanged. No keystore
        // is committed.
        getByName("debug") {
            if (hasDebugSigning) {
                storeFile = debugStoreFile
                storePassword = debugStorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
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
        debug {
            buildConfigField("boolean", "RETURN_GUIDANCE", "true")
            buildConfigField("boolean", "FILMING", "true")
        }
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

// fakeRelease would ship the in-memory cabin. The contest APK is pleos release only.
// The merged manifest is the public variant artifact, not an AGP intermediates path.
val pleosReleaseMergedManifest = objects.fileProperty()
androidComponents {
    beforeVariants(selector().all()) { variant ->
        if (variant.name == "fakeRelease") {
            variant.enable = false
        }
    }
    onVariants(selector().withName("pleosRelease")) { variant ->
        pleosReleaseMergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
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
    testImplementation(libs.compose.ui.test.junit4)
    // Declares ComponentActivity for createComposeRule(). debugImplementation merges
    // it into the debug manifest only, which Robolectric reads from the debug
    // unit-test resource APK. Compose screenshot tests therefore live in src/testDebug.
    // A testImplementation copy is not merged into that APK, and the release APK
    // must not contain this activity. checkPleosReleaseContents rejects it.
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(files(carJar))
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("miri.designMatch.update", System.getProperty("miri.designMatch.update") ?: "false")
}

val contestSigningMessage =
    "exportContestApk requires a signed pleos release. " +
        "Set STORE_FILE, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD " +
        "in keystore.properties or the environment. Do not submit a fake or unsigned APK."

// org.gradle.parallel is on. assemblePleosRelease.mustRunAfter(a signing check) does not
// order that task's compile and package children, so they can start before the check.
// whenReady runs after the graph is assembled and before any task action.
// A build that did not request the export never enters the branch.
gradle.taskGraph.whenReady(Action<TaskExecutionGraph> {
    if (hasTask(":app:exportContestApk") && !hasReleaseSigning) {
        throw GradleException(contestSigningMessage)
    }
})

val forbiddenPleosReleaseNames = listOf(
    "FakeCabinWorld",
    "SamplePlaces",
    "FakeVehicleRepository",
    "FakeNavigationRepository",
    "FakeDrivingStateRepository",
    "DemoScenario",
    "DebugDemoActions",
    "PleosConnectorNames",
    "ConnectorProbe",
    "AutoPlayDirector",
    "ScriptedCabinFeed",
    "FilmingReceiver",
    "FilmingController",
    "FilmingBus",
)

// Names the app may ship. Library merges (Vehicle binding, AndroidX receiver) are included.
// The post-2026-10-27 approval list is not published yet, so this is the read-only set
// justified in the pleos manifest. Delete a line there and here together.
// pleos has no applicationIdSuffix. The fake suffix must not be part of this name.
val pleosApplicationId = android.defaultConfig.applicationId
    ?: throw GradleException("applicationId is required for the pleos permission allowlist")
val allowedPleosReleasePermissions = setOf(
    "pleos.car.permission.CAR_DOORS",
    "pleos.car.permission.CAR_WINDOWS",
    "pleos.car.permission.CAR_ENERGY",
    "pleos.car.permission.CAR_LIGHTS",
    "pleos.car.permission.CAR_DRIVE",
    "pleos.car.permission.CAR_INFO",
    "pleos.car.permission.CAR_SEATS",
    "pleos.car.permission.NAVI_ROUTE",
    "android.car.permission.CAR_POWERTRAIN",
    "android.car.permission.CAR_INFO",
    // Merged from the Vehicle SDK AAR. Confirm against the post-2026-10-27 approval list.
    "ai.pleos.playground.service.vehicle.VEHICLE_BINDING",
    "$pleosApplicationId.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

tasks.register("checkPleosReleaseContents") {
    group = "verification"
    description = "Fail the pleos release APK when it contains the fake flavor, an extra permission, or a test activity."
    dependsOn("assemblePleosRelease", "checkSecretsNotTracked")
    inputs.file(pleosReleaseMergedManifest)
    doLast {
        // Dex class-name scan is valid only while release isMinifyEnabled is false.
        // R8 would rename these classes and this check would pass silently.
        // A minified build needs a mapping-file check instead.
        // Resource strings (main strings.xml simulator copy) are not scanned.
        val mergedManifest = pleosReleaseMergedManifest.orNull?.asFile
            ?: throw GradleException(
                "pleos release merged manifest is not wired. " +
                    "checkPleosReleaseContents reads SingleArtifact.MERGED_MANIFEST.",
            )
        if (!mergedManifest.isFile) {
            throw GradleException("merged pleos release manifest not found at $mergedManifest")
        }
        val manifestXml = mergedManifest.readText()
        val permissions = Regex("""<uses-permission\b[^>]*android:name="([^"]+)"""")
            .findAll(manifestXml)
            .map { it.groupValues[1] }
            .toSet()
        val extra = permissions - allowedPleosReleasePermissions
        if (extra.isNotEmpty()) {
            throw GradleException("pleos release manifest permissions outside the allowlist: $extra")
        }
        val forbidden = listOf(
            "CONTROL_CAR",
            "PRIVATE_CAR_SAFETY",
            "PERF_VEHICLE_SPEED",
        )
        val forbiddenHit = permissions.firstOrNull { name ->
            forbidden.any { token -> name.contains(token) }
        }
        if (forbiddenHit != null) {
            throw GradleException("pleos release manifest contains $forbiddenHit")
        }
        val apkDir = layout.buildDirectory.dir("outputs/apk/pleos/release").get().asFile
        val apks = apkDir.listFiles().orEmpty().filter { file ->
            file.isFile && file.name.endsWith(".apk") && !file.name.contains("unaligned")
        }
        if (apks.isEmpty()) {
            throw GradleException("pleos release APK not found in $apkDir")
        }
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.endsWith(".dex")) continue
                    val text = zip.getInputStream(entry).use { input ->
                        String(input.readBytes(), Charsets.ISO_8859_1)
                    }
                    val hit = forbiddenPleosReleaseNames.firstOrNull { name ->
                        // Identifier boundary so ConnectorProbe does not match ConnectorProbeText.
                        Regex("(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])").containsMatchIn(text)
                    }
                    if (hit != null) {
                        throw GradleException("${apk.name} contains $hit")
                    }
                    val testClassHit = listOf(
                        "androidx/compose/ui/test",
                        "androidx.compose.ui.test",
                    ).firstOrNull { marker -> text.contains(marker) }
                    if (testClassHit != null) {
                        throw GradleException("${apk.name} contains a ui-test class: $testClassHit")
                    }
                }
            }
        }
        val logHit = project.bookmarkFieldLogInRelease()
        if (logHit != null) {
            throw GradleException("release sources log a bookmark field: $logHit")
        }
        val guidance = project.pleosReleaseBoolean("RETURN_GUIDANCE")
        if (guidance != "false") {
            throw GradleException("pleos release RETURN_GUIDANCE must be false, was $guidance")
        }
        val filming = project.pleosReleaseBoolean("FILMING")
        if (filming != "false") {
            throw GradleException("pleos release FILMING must be false, was $filming")
        }
        if (
            manifestXml.contains("FilmingReceiver") ||
            manifestXml.contains("com.krazylabs.miri.action.FILMING") ||
            manifestXml.contains("com.krazylabs.miri.action.AUTOPLAY")
        ) {
            throw GradleException("pleos release manifest contains the filming receiver")
        }
        val testManifestHit = listOf(
            "androidx.activity.ComponentActivity",
            "androidx.compose.ui.test",
        ).firstOrNull { marker -> manifestXml.contains(marker) }
        if (testManifestHit != null) {
            throw GradleException("pleos release manifest contains a test activity entry: $testManifestHit")
        }
    }
}

/** A boolean packaged into the pleos release APK. Debug sets the same field to true. */
fun Project.pleosReleaseBoolean(field: String): String {
    val buildConfig = layout.buildDirectory.file(
        "generated/source/buildConfig/pleos/release/com/krazylabs/miri/BuildConfig.java",
    ).get().asFile
    if (!buildConfig.isFile) {
        throw GradleException("pleos release BuildConfig not found at $buildConfig")
    }
    val match = Regex("""boolean\s+${Regex.escape(field)}\s*=\s*(true|false)""")
        .find(buildConfig.readText())
        ?: throw GradleException("pleos release BuildConfig has no $field")
    return match.groupValues[1]
}

/** A Log call in main, pleos, or release must not print bookmark coordinates or names. */
fun Project.bookmarkFieldLogInRelease(): String? {
    val roots = listOf("src/main", "src/pleos", "src/release")
    val logStart = Regex("""Log\.[iwd]\(""")
    val field = Regex("""latitude|address|name=""")
    roots.forEach { root ->
        val dir = file(root)
        if (!dir.isDirectory) return@forEach
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { source ->
            val lines = source.readLines()
            var index = 0
            while (index < lines.size) {
                if (!logStart.containsMatchIn(lines[index])) {
                    index += 1
                    continue
                }
                val call = StringBuilder()
                var depth = 0
                var started = false
                var end = index
                while (end < lines.size && end < index + 12) {
                    val part = lines[end]
                    call.append(part).append('\n')
                    part.forEach { ch ->
                        if (ch == '(') {
                            depth += 1
                            started = true
                        } else if (ch == ')') {
                            depth -= 1
                        }
                    }
                    if (started && depth <= 0) break
                    end += 1
                }
                if (field.containsMatchIn(call)) {
                    return "${source.relativeTo(projectDir).path}:${index + 1}"
                }
                index = end + 1
            }
        }
    }
    return null
}

tasks.register<Copy>("exportContestApk") {
    group = "distribution"
    description = "Copy a signed pleos release APK to apk/$contestApkName. Fails when release signing is not configured."
    dependsOn("checkPleosReleaseContents")
    from(layout.buildDirectory.dir("outputs/apk/pleos/release"))
    include("*-release.apk")
    exclude("*unaligned*")
    exclude("*unsigned*")
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { contestApkName }
}

tasks.register("checkSecretsNotTracked") {
    group = "verification"
    description = "Fail when local.properties, secrets/, or an env file is git-tracked."
    doLast {
        val root = rootProject.projectDir
        val process = ProcessBuilder("git", "ls-files")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val tracked = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) throw GradleException("git ls-files failed: $tracked")
        val bad = tracked.lineSequence().filter { line ->
            line == "local.properties" ||
                line == "secrets" ||
                line.startsWith("secrets/") ||
                line.endsWith(".env") ||
                line.contains("/.env")
        }.toList()
        if (bad.isNotEmpty()) throw GradleException("secret files are tracked: $bad")
    }
}
