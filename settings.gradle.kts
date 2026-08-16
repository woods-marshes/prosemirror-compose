rootProject.name = "prosemirror-compose"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // 阿里云镜像（大陆网络访问 Maven Central 不稳定，大文件下载易中断）
        // com.atlassian.prosemirror 未同步到镜像，排除后走 mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
            content {
                excludeGroup("com.atlassian.prosemirror")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // 阿里云镜像（大陆网络访问 Maven Central 不稳定，大文件下载易中断）
        // com.atlassian.prosemirror 未同步到镜像，排除后走 mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
            content {
                excludeGroup("com.atlassian.prosemirror")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":webApp")