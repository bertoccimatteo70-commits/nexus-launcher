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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NexusLauncher"

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:data",
    ":core:designsystem",
    ":feature:home",
    ":feature:drawer",
    ":feature:search",
    ":feature:settings",
    ":feature:tools",
    ":feature:automations",
    ":feature:backup",
)
