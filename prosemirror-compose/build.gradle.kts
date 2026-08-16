import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktechMavenPublish)
}

mavenPublishing {
    // GROUP / VERSION_NAME / POM_* 从 gradle.properties 读取；
    // 模块名默认就是 artifactId，这里只补模块专属的 name/description。
    pom {
        name.set("prosemirror-compose")
        description.set("Compose Multiplatform rich text editor with a ProseMirror document model.")
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ProseMirrorCompose"
            isStatic = true
        }
    }

    jvm()

//    js {
//        browser()
//    }
//
//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        browser()
//    }

    android {
        namespace = "com.github.wood.prosemirror.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
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
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.prosemirror.collab)
            implementation(libs.prosemirror.history)
            implementation(libs.prosemirror.model)
            implementation(libs.prosemirror.state)
            implementation(libs.prosemirror.test.builder)
            implementation(libs.prosemirror.transform)

            implementation(libs.jetbrains.markdown)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
//        jsMain.dependencies {
//            implementation(libs.wrappers.browser)
//        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}