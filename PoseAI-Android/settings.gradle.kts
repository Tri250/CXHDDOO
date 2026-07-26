// 本地 Maven 仓库（仅用于离线/开发环境）
// 通过环境变量 CI 区分：
//   - 本地开发（无 CI 环境变量）：使用 /workspace/local-maven-repo 离线仓库
//   - CI 环境（CI=true）：跳过本地仓库，使用 Google Maven / Maven Central
// 这样既能保证本地离线开发，又能让 CI 正确解析所有依赖

pluginManagement {
    val useLocalMavenRepo = System.getenv("CI") == null
    repositories {
        if (useLocalMavenRepo) {
            maven { url = uri("file:///workspace/local-maven-repo") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useLocalMavenRepo = System.getenv("CI") == null
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        if (useLocalMavenRepo) {
            maven { url = uri("file:///workspace/local-maven-repo") }
        }
    }
}

rootProject.name = "PoseAI"
include(":app")
