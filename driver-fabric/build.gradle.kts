import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    id("net.fabricmc.fabric-loom-remap")
}

fun fabricLaneProperty(
    name: String,
    defaultValue: String,
): String = providers.gradleProperty(name).orElse(defaultValue).get()

fun fabricLaneIntProperty(
    name: String,
    defaultValue: Int,
): Int = fabricLaneProperty(name, defaultValue.toString()).toInt()

val fabricCompiledMinecraftVersion = fabricLaneProperty("craftless.fabric.minecraftVersion", "1.21.6")
val fabricCompiledYarnMappings = fabricLaneProperty("craftless.fabric.yarnMappings", "1.21.6+build.1")
val fabricCompiledLoaderVersion = fabricLaneProperty("craftless.fabric.loaderVersion", "0.19.3")
val fabricCompiledApiVersion = fabricLaneProperty("craftless.fabric.apiVersion", "0.128.2+1.21.6")
val fabricCompiledJavaMajorVersion = fabricLaneIntProperty("craftless.fabric.javaMajorVersion", 21)
val fabricCompiledLaneId = fabricLaneProperty("craftless.fabric.laneId", "fabric-current-lane")
val fabricCompiledProviderId = fabricLaneProperty("craftless.fabric.providerId", "fabric-current-lane")
val fabricCompiledArtifactKey = fabricLaneProperty("craftless.fabric.artifactKey", "fabric-current-remap-jar")
val fabricCompiledDistributionPath =
    fabricLaneProperty("craftless.fabric.distributionPath", "mods/craftless-driver-fabric.jar")
val fabricCompiledMappingsFingerprint =
    fabricLaneProperty("craftless.fabric.mappingsFingerprint", "craftless-fabric-bindings")
val generatedFabricLaneMetadataDir =
    layout.buildDirectory.dir("generated/sources/fabricCompiledLaneMetadata/kotlin")
val generatedFabricDriverLaneCatalog = layout.buildDirectory.file("generated/driver-lanes/fabric-driver-lanes.json")

extensions.extraProperties["fabricCompiledMinecraftVersion"] = fabricCompiledMinecraftVersion
extensions.extraProperties["fabricCompiledLoaderVersion"] = fabricCompiledLoaderVersion

kotlin {
    sourceSets {
        named("main") {
            kotlin.srcDir(generatedFabricLaneMetadataDir)
        }
    }
}

dependencies {
    "minecraft"("com.mojang:minecraft:$fabricCompiledMinecraftVersion")
    "mappings"("net.fabricmc:yarn:$fabricCompiledYarnMappings:v2")
    "modImplementation"("net.fabricmc:fabric-loader:$fabricCompiledLoaderVersion")
    "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricCompiledApiVersion")

    implementation(project(":driver-api"))
    implementation(project(":driver-runtime"))
    implementation(project(":daemon"))
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    implementation("io.ktor:ktor-server-core-jvm:3.5.0")
    implementation("io.ktor:ktor-server-cio-jvm:3.5.0")
    include(project(":protocol"))
    include(project(":driver-api"))
    include(project(":driver-runtime"))
    include(project(":daemon"))
    include(project(":bridge-hmc"))
    include("io.ktor:ktor-client-core-jvm:3.5.0")
    include("io.ktor:ktor-client-cio-jvm:3.5.0")
    include("io.ktor:ktor-server-core-jvm:3.5.0")
    include("io.ktor:ktor-server-cio-jvm:3.5.0")
    include("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    include("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    include("org.jetbrains.kotlin:kotlin-reflect:2.4.0")
    include("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0")
    include("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0")
    include("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")
    include("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")
    include("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.0")
    include("org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.0")
    include("io.ktor:ktor-http-jvm:3.5.0")
    include("io.ktor:ktor-http-cio-jvm:3.5.0")
    include("io.ktor:ktor-utils-jvm:3.5.0")
    include("io.ktor:ktor-io-jvm:3.5.0")
    include("io.ktor:ktor-events-jvm:3.5.0")
    include("io.ktor:ktor-websocket-serialization-jvm:3.5.0")
    include("io.ktor:ktor-serialization-jvm:3.5.0")
    include("io.ktor:ktor-websockets-jvm:3.5.0")
    include("io.ktor:ktor-sse-jvm:3.5.0")
    include("io.ktor:ktor-network-jvm:3.5.0")
    include("io.ktor:ktor-network-tls-jvm:3.5.0")
    include("com.typesafe:config:1.4.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraftVersion", fabricCompiledMinecraftVersion)
    inputs.property("fabricApiVersion", fabricCompiledApiVersion)
    inputs.property("fabricLoaderVersion", fabricCompiledLoaderVersion)
    inputs.property("javaMajorVersion", fabricCompiledJavaMajorVersion)
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraftVersion" to fabricCompiledMinecraftVersion,
            "fabricApiVersion" to fabricCompiledApiVersion,
            "fabricLoaderVersion" to fabricCompiledLoaderVersion,
            "javaMajorVersion" to fabricCompiledJavaMajorVersion,
        )
    }
}

val pathfinderRuntimeVersion = "1.15.0"
val pathfinderRuntimeFileName = "baritone-api-fabric-$pathfinderRuntimeVersion.jar"
val pathfinderRuntimeUrl =
    "https://github.com/cabaletta/baritone/releases/download/v$pathfinderRuntimeVersion/$pathfinderRuntimeFileName"
val pathfinderRuntimeSha256 = "c58ef35a133b6ffce96a74682138ac2ee818cbc063b7c62671db9f9d7d783ebb"
val pathfinderRuntimeJar = layout.buildDirectory.file("pathfinder/$pathfinderRuntimeFileName")
val pathfinderNestedRuntimeFileName = "nether-pathfinder-1.4.1.jar"
val pathfinderNestedRuntimeZipPath = "META-INF/jars/$pathfinderNestedRuntimeFileName"
val pathfinderNestedRuntimeSha256 = "5bf06c2406b80c86a94fd58f18623fcc1877324e3beb7e01ac6503c5a7a260a6"
val pathfinderNestedRuntimeJar = layout.buildDirectory.file("pathfinder/$pathfinderNestedRuntimeFileName")
val pathfinderRuntimeEnabled =
    listOf("CRAFTLESS_ENABLE_PATHFINDER_BACKEND", "CRAFTLESS_FINAL_GAMEPLAY")
        .any { name ->
            val value = System.getenv(name)
            value == "1" || value.equals("true", ignoreCase = true)
        }

fun jsonString(value: String): String =
    buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

fun jsonArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }

val writeFabricDriverLaneCatalog =
    tasks.register("writeFabricDriverLaneCatalog") {
        group = "build"
        description = "Generates the internal Fabric driver lane catalog consumed by Craftless distributions."

        inputs.property("fabricCompiledProviderId", fabricCompiledProviderId)
        inputs.property("fabricCompiledArtifactKey", fabricCompiledArtifactKey)
        inputs.property("fabricCompiledDistributionPath", fabricCompiledDistributionPath)
        inputs.property("fabricCompiledMinecraftVersion", fabricCompiledMinecraftVersion)
        inputs.property("fabricCompiledLoaderVersion", fabricCompiledLoaderVersion)
        inputs.property("fabricCompiledApiVersion", fabricCompiledApiVersion)
        inputs.property("fabricCompiledJavaMajorVersion", fabricCompiledJavaMajorVersion)
        inputs.property("fabricCompiledMappingsFingerprint", fabricCompiledMappingsFingerprint)
        outputs.file(generatedFabricDriverLaneCatalog)

        doLast {
            val output = generatedFabricDriverLaneCatalog.get().asFile
            output.parentFile.mkdirs()
            output.writeText(
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": ${jsonString(fabricCompiledMinecraftVersion)},
                      "loaderVersion": ${jsonString(fabricCompiledLoaderVersion)},
                      "path": ${jsonString(fabricCompiledDistributionPath)},
                      "providerId": ${jsonString(fabricCompiledProviderId)},
                      "artifactKey": ${jsonString(fabricCompiledArtifactKey)},
                      "fabricApiVersion": ${jsonString(fabricCompiledApiVersion)},
                      "javaMajorVersion": $fabricCompiledJavaMajorVersion,
                      "mappingsFingerprint": ${jsonString(fabricCompiledMappingsFingerprint)},
                      "distributionPath": ${jsonString(fabricCompiledDistributionPath)}
                    }
                  ]
                }
                """.trimIndent() + "\n",
            )
        }
    }

val generateFabricCompiledLaneMetadata =
    tasks.register("generateFabricCompiledLaneMetadata") {
        group = "build"
        description = "Generates Kotlin metadata for the compiled Fabric lane from Gradle lane constants."

        val outputFile =
            generatedFabricLaneMetadataDir.map { directory ->
                directory.file("com/minekube/craftless/driver/fabric/runtime/FabricCompiledLaneMetadata.kt")
            }
        inputs.property("fabricCompiledLaneId", fabricCompiledLaneId)
        inputs.property("fabricCompiledProviderId", fabricCompiledProviderId)
        inputs.property("fabricCompiledMinecraftVersion", fabricCompiledMinecraftVersion)
        inputs.property("fabricCompiledLoaderVersion", fabricCompiledLoaderVersion)
        inputs.property("fabricCompiledApiVersion", fabricCompiledApiVersion)
        inputs.property("fabricCompiledJavaMajorVersion", fabricCompiledJavaMajorVersion)
        inputs.property("fabricCompiledMappingsFingerprint", fabricCompiledMappingsFingerprint)
        outputs.file(outputFile)

        doLast {
            val file = outputFile.get().asFile
            file.parentFile.mkdirs()
            file.writeText(
                """
                package com.minekube.craftless.driver.fabric.runtime

                internal object FabricCompiledLaneMetadata {
                    const val ID: String = ${jsonString(fabricCompiledLaneId)}
                    const val PROVIDER_ID: String = ${jsonString(fabricCompiledProviderId)}
                    const val MINECRAFT_VERSION: String = ${jsonString(fabricCompiledMinecraftVersion)}
                    const val LOADER_VERSION: String = ${jsonString(fabricCompiledLoaderVersion)}
                    const val FABRIC_API_VERSION: String = ${jsonString(fabricCompiledApiVersion)}
                    const val JAVA_MAJOR_VERSION: Int = $fabricCompiledJavaMajorVersion
                    const val MAPPINGS_FINGERPRINT: String = ${jsonString(fabricCompiledMappingsFingerprint)}
                }
                """.trimIndent() + "\n",
            )
        }
    }

tasks.named("compileKotlin") {
    dependsOn(generateFabricCompiledLaneMetadata)
}

tasks.named("compileTestKotlin") {
    dependsOn(generateFabricCompiledLaneMetadata)
}

tasks.matching { it.name == "runKtlintCheckOverMainSourceSet" }.configureEach {
    dependsOn(generateFabricCompiledLaneMetadata)
}

fun envLong(name: String): Long? = System.getenv(name)?.toLongOrNull()

fun finalGameplayFabricActionTimeout(): String =
    (
        envLong("CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS")
            ?: envLong("CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS")
            ?: 720_000L
    ).toString()

fun finalGameplayPublicAgentActionRequestTimeout(): String {
    val fabricActionMillis = finalGameplayFabricActionTimeout().toLong()
    return maxOf(10_000L, fabricActionMillis - 10_000L).toString()
}

fun finalGameplayPublicAgentCommandTimeout(): String =
    (
        envLong("CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS")
            ?: envLong("CRAFTLESS_PUBLIC_AGENT_COMMAND_TIMEOUT_MS")
            ?: envLong("CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS")
            ?: 2_700_000L
    ).toString()

fun finalGameplayOuterActionTimeout(): String {
    val holdMillis = envLong("CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS") ?: 600_000L
    val publicAgentCommandMillis = finalGameplayPublicAgentCommandTimeout().toLong()
    val requestedOuter = envLong("CRAFTLESS_LOCAL_SERVER_SMOKE_ACTION_TIMEOUT_MS")
    return maxOf(requestedOuter ?: 0L, publicAgentCommandMillis + holdMillis + 180_000L, 1_500_000L).toString()
}

fun laneSuffix(version: String): String =
    version
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }

fun fabricSmokeRuntimeLaneJson(minecraftVersion: String): String {
    val version = minecraftVersion.takeIf { it.isNotBlank() } ?: fabricCompiledMinecraftVersion
    val lane =
        when (version) {
            fabricCompiledMinecraftVersion ->
                mapOf(
                    "id" to fabricCompiledLaneId,
                    "status" to "SUPPORTED",
                    "minecraftVersion" to version,
                    "javaMajorVersion" to fabricCompiledJavaMajorVersion,
                    "providerId" to fabricCompiledProviderId,
                )
            else ->
                mapOf(
                    "id" to "fabric-unsupported-${laneSuffix(version)}",
                    "status" to "UNSUPPORTED",
                    "minecraftVersion" to version,
                    "javaMajorVersion" to if (version.startsWith("26.")) 25 else fabricCompiledJavaMajorVersion,
                    "providerId" to "fabric-unsupported",
                    "unsupportedReason" to "unsupported-version",
                )
        }
    return lane.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        val encodedValue =
            when (value) {
                is Number -> value.toString()
                else -> jsonString(value.toString())
            }
        "${jsonString(key)}:$encodedValue"
    }
}

fun sha256(file: File): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

fun preparePathfinderRuntimeFiles() {
    val output = pathfinderRuntimeJar.get().asFile
    val nestedOutput = pathfinderNestedRuntimeJar.get().asFile
    output.parentFile.mkdirs()
    if (!output.exists() || sha256(output) != pathfinderRuntimeSha256) {
        URI(pathfinderRuntimeUrl).toURL().openStream().use { input ->
            output.outputStream().use { outputStream ->
                input.copyTo(outputStream)
            }
        }
    }
    val actualSha256 = sha256(output)
    check(actualSha256 == pathfinderRuntimeSha256) {
        "Pathfinder runtime checksum mismatch: expected $pathfinderRuntimeSha256 but got $actualSha256"
    }
    if (!nestedOutput.exists() || sha256(nestedOutput) != pathfinderNestedRuntimeSha256) {
        ZipFile(output).use { zip ->
            val entry =
                zip.getEntry(pathfinderNestedRuntimeZipPath)
                    ?: error("Pathfinder runtime is missing nested mod jar: $pathfinderNestedRuntimeZipPath")
            zip.getInputStream(entry).use { input ->
                nestedOutput.outputStream().use { outputStream ->
                    input.copyTo(outputStream)
                }
            }
        }
    }
    val actualNestedSha256 = sha256(nestedOutput)
    check(actualNestedSha256 == pathfinderNestedRuntimeSha256) {
        "Nested pathfinder runtime checksum mismatch: expected $pathfinderNestedRuntimeSha256 but got $actualNestedSha256"
    }
}

val preparePathfinderRuntime =
    tasks.register("preparePathfinderRuntime") {
        group = "verification"
        description = "Downloads and verifies the pinned optional Fabric pathfinder runtime jar."
        outputs.files(pathfinderRuntimeJar, pathfinderNestedRuntimeJar)

        doLast {
            preparePathfinderRuntimeFiles()
        }
    }

if (pathfinderRuntimeEnabled) {
    preparePathfinderRuntimeFiles()
    val pathfinderRuntimeFiles = files(pathfinderRuntimeJar, pathfinderNestedRuntimeJar)
    pathfinderRuntimeFiles.builtBy(preparePathfinderRuntime)
    dependencies.add("modRuntimeOnly", pathfinderRuntimeFiles)
    tasks.named("generateRemapClasspath") {
        dependsOn(preparePathfinderRuntime)
    }
}

val fabricClientJavaExecutable =
    System
        .getenv("CRAFTLESS_SMOKE_JAVA_EXECUTABLE")
        ?.takeIf { it.isNotBlank() }

tasks.named<JavaExec>("runClient") {
    if (fabricClientJavaExecutable != null) {
        setExecutable(fabricClientJavaExecutable)
    }
    if (pathfinderRuntimeEnabled) {
        dependsOn(preparePathfinderRuntime)
    }
}

val testkitSourceSets =
    project(":testkit")
        .extensions
        .getByType<org.gradle.api.tasks.SourceSetContainer>()

tasks.register<JavaExec>("fabricClientSmoke") {
    group = "verification"
    description = "Opt-in Fabric real-client smoke. Set CRAFTLESS_FABRIC_CLIENT_SMOKE=1 " +
        "to keep a local server alive while a client command runs."
    dependsOn(":testkit:classes")
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.minekube.craftless.testkit.LocalMinecraftServerSmokeKt")

    val fabricSmokeEnabled =
        System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE") == "1" ||
            System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE").equals("true", ignoreCase = true)
    val fabricSmokeMinecraftVersion =
        System
            .getenv("CRAFTLESS_SMOKE_MINECRAFT_VERSION")
            ?.takeIf { it.isNotBlank() }
            ?: fabricCompiledMinecraftVersion
    environment("CRAFTLESS_FABRIC_CLIENT_SMOKE", System.getenv("CRAFTLESS_FABRIC_CLIENT_SMOKE").orEmpty())
    environment("CRAFTLESS_SMOKE_MINECRAFT_VERSION", fabricSmokeMinecraftVersion)

    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_ACTION_COMMAND_JSON").isNullOrBlank()) {
        val rootProjectPath =
            rootProject.projectDir.absolutePath
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        environment(
            "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON",
            """["mise","-C","$rootProjectPath","exec","--","gradle","-p","$rootProjectPath",":driver-fabric:runClient"]""",
        )
    }
    if (
        fabricSmokeEnabled &&
        System.getenv("CRAFTLESS_SMOKE_RUNTIME_LANE_JSON").isNullOrBlank() &&
        System.getenv("CRAFTLESS_SMOKE_RUNTIME_LANE_FILE").isNullOrBlank()
    ) {
        environment("CRAFTLESS_SMOKE_RUNTIME_LANE_JSON", fabricSmokeRuntimeLaneJson(fabricSmokeMinecraftVersion))
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
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_PROVISION_ITEM_ID").isNullOrBlank()) {
        environment("CRAFTLESS_SMOKE_PROVISION_ITEM_ID", "minecraft:iron_sword")
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_PROVISION_ITEM_NAME").isNullOrBlank()) {
        environment(
            "CRAFTLESS_SMOKE_PROVISION_ITEM_NAME",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_EQUIP_ITEM") ?: "Iron Sword",
        )
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_SMOKE_PROVISION_ITEM_COUNT").isNullOrBlank()) {
        environment("CRAFTLESS_SMOKE_PROVISION_ITEM_COUNT", "1")
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM").isNullOrBlank()) {
        environment("CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM", "1")
    }
    if (fabricSmokeEnabled && System.getenv("CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS").isNullOrBlank()) {
        environment("CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS", "3000")
    }

    doFirst {
        if (fabricSmokeEnabled) {
            println(
                "Fabric client smoke requested; server lifecycle is owned by testkit " +
                    "LocalMinecraftServerSmoke.runWithServer",
            )
        }
    }
}

tasks.register<JavaExec>("fabricFinalGameplay") {
    group = "verification"
    description = "Opt-in final Craftless gameplay run. Set CRAFTLESS_FINAL_GAMEPLAY=1 to launch the local server and Fabric client."
    dependsOn(":testkit:classes")
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("com.minekube.craftless.testkit.LocalMinecraftServerSmokeKt")

    val finalGameplayEnabled =
        System.getenv("CRAFTLESS_FINAL_GAMEPLAY") == "1" ||
            System.getenv("CRAFTLESS_FINAL_GAMEPLAY").equals("true", ignoreCase = true)
    environment("CRAFTLESS_FINAL_GAMEPLAY", System.getenv("CRAFTLESS_FINAL_GAMEPLAY").orEmpty())

    if (finalGameplayEnabled) {
        val rootProjectPath =
            rootProject.projectDir.absolutePath
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        environment("CRAFTLESS_FABRIC_CLIENT_SMOKE", "1")
        environment(
            "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT",
            layout.buildDirectory
                .dir("craftless-final-gameplay")
                .get()
                .asFile
                .absolutePath,
        )
        environment(
            "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON",
            System.getenv("CRAFTLESS_SMOKE_ACTION_COMMAND_JSON")
                ?: """["mise","-C","$rootProjectPath","exec","--","gradle","-p","$rootProjectPath",":driver-fabric:runClient"]""",
        )
        environment(
            "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON",
            System.getenv("CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON")
                ?: """["mise","-C","$rootProjectPath","exec","--","gradle","-p","$rootProjectPath",":testkit:publicAgentGameplay"]""",
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE")
                ?: "hello from Craftless final gameplay",
        )
        environment(
            "CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE",
            System.getenv("CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE")
                ?: "hello from Craftless final gameplay",
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM") ?: "0",
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS") ?: "3000",
        )
        environment(
            "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS",
            finalGameplayOuterActionTimeout(),
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS",
            finalGameplayFabricActionTimeout(),
        )
        environment(
            "CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS",
            System.getenv("CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS")
                ?: finalGameplayPublicAgentActionRequestTimeout(),
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS",
            finalGameplayPublicAgentCommandTimeout(),
        )
        environment(
            "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS") ?: "600000",
        )
        System.getenv("CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS")?.takeIf { it.isNotBlank() }?.let { phrase ->
            environment("CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS", phrase)
        }
        System.getenv("CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS")?.takeIf { it.isNotBlank() }?.let { reminderMs ->
            environment("CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS", reminderMs)
        }
        environment(
            "CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS",
            System.getenv("CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS") ?: "600000",
        )
        val readyCommand = System.getenv("CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON")
        if (!readyCommand.isNullOrBlank()) {
            environment(
                "CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON",
                readyCommand,
            )
        }
    }

    doFirst {
        if (finalGameplayEnabled) {
            println("Final gameplay requested; artifacts will be written under driver-fabric/build/craftless-final-gameplay/artifacts")
            println("The harness will announce readiness during the hold window when a ready notification command is configured.")
        }
    }
}
