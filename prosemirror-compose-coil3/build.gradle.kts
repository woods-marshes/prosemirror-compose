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
        name.set("prosemirror-compose-coil3")
        description.set("Coil3 image loader integration for prosemirror-compose.")
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ProseMirrorComposeCoil3"
            isStatic = true
        }
    }

    jvm()

    android {
        namespace = "com.github.wood.prosemirror.compose.coil3"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":prosemirror-compose"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.coil.compose)
        }
    }
}
