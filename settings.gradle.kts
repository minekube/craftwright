pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://libraries.minecraft.net/")
        mavenCentral()
    }
}

rootProject.name = "craftless"

include(
    "protocol",
    "driver-api",
    "driver-runtime",
    "driver-fabric",
    "driver-fabric-official",
    "testkit",
    "daemon",
    "bridge-hmc",
    "cli",
)
