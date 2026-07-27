pluginManagement {
    repositories {
        maven { url = uri("/workspace/local-maven-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven { url = uri("/workspace/local-maven-repo") }
        google()
        mavenCentral()
    }
}

rootProject.name = "PoseAI"
include(":app")
