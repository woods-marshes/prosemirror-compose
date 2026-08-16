import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":composeApp"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

// Linux 桌面端的中文等复合输入依赖 AWT 输入法支持。JBR 对 XIM/IBus/fcitx
// 的兼容性最好（普通 OpenJDK/Zulu 在部分发行版上候选词/输入法切换有问题），
// 因此 Linux 上的 desktopApp run/打包优先使用 JBR_HOME，否则用 Gradle toolchain 解析 JBR 21。
val isLinux = System.getProperty("os.name").startsWith("Linux")
val jbrLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
    vendor.set(JvmVendorSpec.JETBRAINS)
}

val jbrHome: String? = if (isLinux) {
    System.getenv("JBR_HOME")
        ?.takeIf { File(it, "bin/java").isFile }
        ?: jbrLauncher.get().metadata.installationPath.asFile.absolutePath
} else {
    null
}

compose.desktop {
    application {
        mainClass = "com.github.wood.prosemirror.compose.MainKt"
        jbrHome?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.github.wood.prosemirror.compose"
            packageVersion = "1.0.0"
        }
    }
}