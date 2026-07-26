// 本地 Maven 仓库（仅用于离线/开发环境），路径相对 settings.gradle.kts 所在目录
// CI 环境若无此目录，Gradle 会自动跳过，不影响依赖解析

pluginManagement {
    val localMavenRepo = settingsDir.parentFile?.resolve("local-maven-repo")
    repositories {
        if (localMavenRepo != null && localMavenRepo.exists()) {
            maven { url = uri(localMavenRepo) }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val localMavenRepo = settingsDir.parentFile?.resolve("local-maven-repo")
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        if (localMavenRepo != null && localMavenRepo.exists()) {
            maven { url = uri(localMavenRepo) }
        }
    }
}

rootProject.name = "PoseAI"
include(":app")
