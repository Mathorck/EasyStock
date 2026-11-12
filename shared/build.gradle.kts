import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        // Room generates an `actual object` for AppDatabaseConstructor
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    android {
       namespace = "ch.mathorck.easystock.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.appcompat)
            // Activity Result APIs (camera, photo picker)
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // Backup transfer: QR scan (camera preview + frames) and QR encode/decode
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.zxing.core)
            // Server mode: HTTP engine
            implementation(libs.ktor.client.okhttp)
            // Server mode: the 15-minute background sync
            implementation(libs.androidx.work.runtime)
            // Server mode: optional UnifiedPush wake-up
            implementation(libs.unifiedpush.connector)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigationevent.compose)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            // Server mode (optional): API client and session store
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.androidx.datastore.preferencesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // ViewModel tests: Dispatchers.setMain and virtual time
            implementation(libs.kotlinx.coroutines.test)
            // Server mode: fake server for the API client tests
            implementation(libs.ktor.client.mock)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.room.testing)
            implementation(libs.androidx.work.testing)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    // Exported schema JSON, needed to write and test migrations
    schemaDirectory("$projectDir/schemas")
}

// Code coverage of the host tests (commonTest + androidHostTest), see .github/workflows/ci.yml.
// Device tests (androidDeviceTest) need an emulator and are not measured.
kover {
    reports {
        filters {
            excludes {
                // Generated code only: real logic stays in the report.
                classes(
                    // Room: DAO and database implementations, database constructor
                    "*_Impl*",
                    "*.AppDatabaseConstructor*",
                    // Compose resources accessors (Res, String0, Drawable0...)
                    "easystock.shared.generated.resources.*",
                    // Compose compiler lambda holders
                    "*.ComposableSingletons*",
                    "*.BuildConfig",
                )
            }
        }
    }
}

// The merge test cases shared with the server are read by MergeTestCasesTest: a changed case reruns the host tests
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/test-cases"))
        .withPropertyName("sharedTestCases")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// End-to-end tests (package ch.mathorck.easystock.e2e, run by server/e2e/run.sh of the server repository,
// see its docs/05-e2e-tests.md) run the real sync code with Room on the host JVM. The Android SQLite driver
// loads its native library from these properties: take the desktop build of the same driver version (not on
// the classpath, only its native files).
val sqliteDesktopNatives: Configuration by configurations.creating { isTransitive = false }
dependencies { sqliteDesktopNatives("androidx.sqlite:sqlite-bundled-jvm:${libs.versions.sqlite.get()}") }
val sqliteNativeDir = layout.buildDirectory.dir("sqlite-natives")
val extractSqliteNatives by tasks.registering(Sync::class) {
    // Resolved when the task is configured: a lambda here would hold the script (configuration cache)
    from(zipTree(sqliteDesktopNatives.singleFile)) { include("natives/**") }
    into(sqliteNativeDir)
}

tasks.withType<Test>().named { it == "testAndroidHostTest" }.configureEach {
    dependsOn(extractSqliteNatives)
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    val (folder, library) = when {
        os.contains("mac") -> "osx_arm64" to "libsqliteJni.dylib"
        os.contains("windows") -> "windows_x64" to "sqliteJni.dll"
        else -> (if (arm) "linux_arm64" else "linux_x64") to "libsqliteJni.so"
    }
    systemProperty("androidx.sqlite.driver.bundled.path", sqliteNativeDir.get().dir("natives/$folder").asFile.absolutePath)
    systemProperty("androidx.sqlite.driver.bundled.name", library)
    // The live server of the E2E tests: -Peasystock.e2e.url=http://127.0.0.1:port -Peasystock.e2e.setupToken=...
    // Without a URL they are skipped.
    listOf("easystock.e2e.url", "easystock.e2e.setupToken").forEach { key ->
        val value = providers.gradleProperty(key).orNull
        inputs.property(key, value ?: "")
        if (value != null) systemProperty(key, value)
    }
}
