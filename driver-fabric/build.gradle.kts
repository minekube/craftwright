plugins {
    id("net.fabricmc.fabric-loom-remap")
}

dependencies {
    "minecraft"("com.mojang:minecraft:1.21.6")
    "mappings"("net.fabricmc:yarn:1.21.6+build.1:v2")
    "modImplementation"("net.fabricmc:fabric-loader:0.19.3")

    implementation(project(":driver-api"))
    implementation(project(":driver-runtime"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val testkitSourceSets = project(":testkit")
    .extensions
    .getByType<org.gradle.api.tasks.SourceSetContainer>()

tasks.register<JavaExec>("fabricClientSmoke") {
    group = "verification"
    description = "Opt-in Fabric real-client smoke. Set CRAFTLESS_FABRIC_CLIENT_SMOKE=1 " +
        "to keep a local server alive while a client command runs."
    dependsOn(":testkit:classes")
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.minekube.craftless.testkit.LocalMinecraftServerSmokeKt")

    val fabricSmokeEnabled = System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE") == "1" ||
        System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE").equals("true", ignoreCase = true)
    environment("CRAFTLESS_FABRIC_CLIENT_SMOKE", System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE").orEmpty())

    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_ACTION_COMMAND_JSON").isNullOrBlank()) {
        environment(
            "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON",
            """["mise","exec","--","gradle",":driver-fabric:runClient"]""",
        )
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE").isNullOrBlank()) {
        environment(
            "CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE")
                ?: "hello from Craftless Fabric smoke",
        )
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_EXPECT_DISCONNECT").isNullOrBlank()) {
        environment("CRAFTLESS_SMOKE_EXPECT_DISCONNECT", "1")
    }

    doFirst {
        if (fabricSmokeEnabled) {
            println(
                "Fabric client smoke requested; server lifecycle is owned by testkit " +
                    "LocalMinecraftServerSmoke.runWithServer"
            )
        }
    }
}
