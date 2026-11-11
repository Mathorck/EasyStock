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
