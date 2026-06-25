pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "craftwright"

include(
    "protocol",
    "driver-api",
    "driver-runtime",
    "testkit",
    "daemon",
    "bridge-hmc",
    "cli",
)
