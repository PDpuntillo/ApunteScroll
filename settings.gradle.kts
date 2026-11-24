pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.5.2"
        // AGP 9 trae Kotlin incorporado: no se aplica org.jetbrains.kotlin.android.
        // El plugin de Compose va siempre con la misma versión que Kotlin.
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        id("com.google.devtools.ksp") version "2.3.14"
        id("androidx.room") version "2.8.5"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ApunteScroll"
include(":app")
