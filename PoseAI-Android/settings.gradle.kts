pluginManagement {
    repositories {
        // Primary Google Maven repository (AGP and Android artifacts)
        google()
        // Aliyun mirror (fallback for network-restricted environments like GitHub Actions)
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        // Alternative Google Maven URL (fallback)
        maven { url = uri("https://maven.google.com/dl/android/maven2/") }
        // Maven Central (fallback for non-Android artifacts)
        mavenCentral()
        // Gradle Plugin Portal (for community plugins)
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Primary Google Maven repository
        google()
        // Aliyun mirror (fallback for network-restricted environments)
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        // Alternative Google Maven URL (fallback)
        maven { url = uri("https://maven.google.com/dl/android/maven2/") }
        // Maven Central
        mavenCentral()
        // Google's Maven repository (alternative domain)
        maven { url = uri("https://maven.google.com/") }
    }
}

rootProject.name = "PoseAI-Android"
include(":app")