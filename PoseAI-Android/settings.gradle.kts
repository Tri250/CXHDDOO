pluginManagement {
    repositories {
        maven { url = uri("file:///workspace/local-maven-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("file:///workspace/local-maven-repo") }
    }
}

rootProject.name = "PoseAI"
include(":app")
