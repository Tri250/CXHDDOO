pluginManagement {
    repositories {
        // Primary Google Maven repository (AGP and Android artifacts)
        google()
        // Aliyun central mirror (mirrors Maven Central - hosts KSP and other non-Android plugins)
        maven { url = uri("https://maven.aliyun.com/repository/central/") }
        // Aliyun public mirror (fallback for all artifacts)
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
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
        // Aliyun central mirror (mirrors Maven Central - hosts Room, ML Kit, etc.)
        maven { url = uri("https://maven.aliyun.com/repository/central/") }
        // Aliyun public mirror (fallback for all artifacts)
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
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