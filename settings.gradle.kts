pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nomad-dashboard-android"

include(
    ":app",
    ":benchmark",
    ":core:common",
    ":core:model",
    ":core:designsystem",
    ":core:network",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:testing",
    ":core:travel",
    ":feature:dashboard",
    ":feature:settings",
    ":feature:visited",
    ":feature:timetracking",
    ":feature:about",
    ":feature:companion",
    ":feature:widgets",
)
