plugins {
    id("net.fabricmc.fabric-loom")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.3")
    implementation("net.fabricmc.fabric-api:fabric-api:0.153.0+26.2")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
