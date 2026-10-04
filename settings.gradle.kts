// 拾图 Shitu · 仓库与模块声明
// 说明：本机 dl.google.com / services.gradle.org 直连不通，故仓库顺序把国内镜像放最前，
// 官方源仅作兜底（正常构建不会走到）。
pluginManagement {
    // GitHub Actions 上直连 google/mavenCentral 更稳；本机 dl.google.com 不通，所以优先国内镜像
    val onCi = System.getenv("CI") != null
    repositories {
        if (onCi) {
            gradlePluginPortal()
            google()
            mavenCentral()
        }
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != null) {
            google()
            mavenCentral()
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "shitu"
include(":app")
