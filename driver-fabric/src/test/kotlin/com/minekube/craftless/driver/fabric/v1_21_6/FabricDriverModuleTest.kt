package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverOperationInvocation
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataSnapshot
import com.minekube.craftless.driver.fabric.discovery.SnapshotFabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeFingerprint
import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.protocol.RuntimeAvailabilityState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FabricDriverModuleTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { path -> path.parent }
            .first { path -> Files.exists(path.resolve("settings.gradle.kts")) }

    private fun readArtifact(
        artifactsDir: Path,
        name: String,
    ): String {
        val path = artifactsDir.resolve(name)
        val deadline = System.nanoTime() + 30_000_000_000
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(path)) {
                return Files.readString(path)
            }
            Thread.sleep(10)
        }
        error(
            "timed out waiting for artifact $name in $artifactsDir; " +
                "observed=${Files.list(artifactsDir).use { stream -> stream.map { it.fileName.toString() }.sorted().toList() }}",
        )
    }

    private fun blockQueryRuntimeMetadataProvider(): FabricRuntimeMetadataProvider =
        SnapshotFabricRuntimeMetadataProvider(
            FabricRuntimeMetadataSnapshot(
                loaderVersion = "0.19.3",
                driver = "craftless-driver-fabric",
                driverVersion = "0.1.0-SNAPSHOT",
                mappings = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
                installedModsFingerprint =
                    fabricRuntimeFingerprint("mods", listOf("minecraft@1.21.6", "fabricloader@0.19.3")),
                registryFingerprint = fabricRuntimeFingerprint("registries", listOf("block:craftless-test")),
                serverFeatureFingerprint = fabricRuntimeFingerprint("server-features", listOf("environment:test")),
            ),
        )

    @Test
    fun `fabric metadata declares client entrypoint and mixin config`() {
        val metadata = resourceJson("fabric.mod.json")

        assertEquals("craftless-driver-fabric", metadata["id"]?.jsonPrimitive?.content)
        assertTrue(metadata["version"]?.jsonPrimitive?.content?.matches(Regex("""\d+\.\d+\.\d+(-SNAPSHOT)?""")) == true)
        assertEquals(
            "Craftless Driver Fabric ${FabricCompiledLaneMetadata.MINECRAFT_VERSION} compiled lane",
            metadata["name"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "Craftless in-client driver compiled for Minecraft ${FabricCompiledLaneMetadata.MINECRAFT_VERSION}.",
            metadata["description"]?.jsonPrimitive?.content,
        )
        assertEquals(
            "com.minekube.craftless.driver.fabric.CraftlessFabricClientEntrypoint",
            metadata["entrypoints"]
                ?.jsonObject
                ?.get("client")
                ?.jsonArray
                ?.single()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "craftless-driver-fabric.mixins.json",
            metadata["mixins"]
                ?.jsonArray
                ?.single()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            ">=${FabricCompiledLaneMetadata.FABRIC_API_VERSION}",
            metadata["depends"]
                ?.jsonObject
                ?.get("fabric-api")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            ">=${FabricCompiledLaneMetadata.LOADER_VERSION}",
            metadata["depends"]
                ?.jsonObject
                ?.get("fabricloader")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            FabricCompiledLaneMetadata.MINECRAFT_VERSION,
            metadata["depends"]
                ?.jsonObject
                ?.get("minecraft")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            ">=${FabricCompiledLaneMetadata.JAVA_MAJOR_VERSION}",
            metadata["depends"]
                ?.jsonObject
                ?.get("java")
                ?.jsonPrimitive
                ?.content,
        )

        val mixins = resourceJson("craftless-driver-fabric.mixins.json")
        assertEquals("com.minekube.craftless.driver.fabric.v1_21_6.mixin", mixins["package"]?.jsonPrimitive?.content)
        assertEquals("client", mixins["environment"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("MinecraftClientMixin"),
            mixins["mixins"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `fabric mod source metadata is expanded from compiled lane placeholders`() {
        val source = Files.readString(repositoryRoot().resolve("driver-fabric/src/main/resources/fabric.mod.json"))

        assertTrue(source.contains("\${minecraftVersion}"))
        assertTrue(source.contains("\${fabricApiVersion}"))
        assertTrue(source.contains("\${fabricLoaderVersion}"))
        assertTrue(source.contains("\${javaMajorVersion}"))
        assertFalse(source.contains("Craftless Driver Fabric 1.21.6\""))
        assertFalse(source.contains("Minecraft 1.21.6."))
        assertFalse(source.contains("driver.fabric.v1_21_6.CraftlessFabricClientEntrypoint"))
    }

    @Test
    fun `stable fabric entrypoint delegates through bootstrap selector only`() {
        val source =
            Files.readString(
                repositoryRoot().resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt",
                ),
            )

        assertTrue(source.contains("FabricBootstrapSelector"))
        assertFalse(source.contains("driver.fabric.v1_21_6"))
        assertFalse(source.contains("FabricCurrentLaneBootstrap"))
    }

    @Test
    fun `current lane bootstrap starts self attach from backend session`() {
        val source =
            Files.readString(
                repositoryRoot().resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneBootstrap.kt",
                ),
            )

        assertTrue(source.contains("BackendDriverSession("))
        assertTrue(source.contains("FabricDriverSelfAttach.startFromEnvironment"))
        assertTrue(
            source.indexOf("FabricDriverBackend.install(backend)") <
                source.indexOf("FabricDriverSelfAttach.startFromEnvironment"),
        )
    }

    @Test
    fun `stable fabric production package imports versioned implementations only through bootstrap selector`() {
        val stablePackage =
            repositoryRoot().resolve(
                "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric",
            )
        val violations =
            Files.list(stablePackage).use { paths ->
                paths
                    .toList()
                    .filter { path -> path.fileName.toString().endsWith(".kt") }
                    .filter { path -> path.fileName.toString() != "FabricBootstrapSelector.kt" }
                    .filter { path ->
                        Files.readString(path).contains("com.minekube.craftless.driver.fabric.v")
                    }.map { path -> stablePackage.relativize(path).toString() }
                    .sorted()
                    .toList()
            }

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `compiled lane literals do not drift in product runtime code`() {
        val root = repositoryRoot()
        val runtimeFiles =
            listOf(
                "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt",
                "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokePlan.kt",
            )

        runtimeFiles.forEach { relativePath ->
            val source = Files.readString(root.resolve(relativePath))
            assertFalse(source.contains("\"${FabricCompiledLaneMetadata.MINECRAFT_VERSION}\""), relativePath)
            assertFalse(source.contains("\"${FabricCompiledLaneMetadata.FABRIC_API_VERSION}\""), relativePath)
            assertFalse(source.contains("\"${FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT}\""), relativePath)
        }
    }

    @Test
    fun `compiled lane metadata is generated by gradle not handwritten source`() {
        val handwrittenMetadata =
            repositoryRoot()
                .resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompiledLaneMetadata.kt")

        assertFalse(
            Files.exists(handwrittenMetadata),
            "FabricCompiledLaneMetadata must be generated from driver-fabric Gradle lane constants.",
        )
    }

    @Test
    fun `fabric build generates driver lane catalog for distribution packaging`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("writeFabricDriverLaneCatalog"))
        assertTrue(buildScript.contains("fabric-driver-lanes.json"))
        assertTrue(buildScript.contains("artifactKey"))
        assertTrue(buildScript.contains("fabric-current-remap-jar"))
        assertTrue(buildScript.contains("distributionPath"))
        assertTrue(buildScript.contains("mappingsFingerprint"))
    }

    @Test
    fun `fabric compiled lane build is parameterized for compatibility probes`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("craftless.fabric.minecraftVersion"))
        assertTrue(buildScript.contains("craftless.fabric.mappingMode"))
        assertTrue(buildScript.contains("craftless.fabric.yarnMappings"))
        assertTrue(buildScript.contains("craftless.fabric.loaderVersion"))
        assertTrue(buildScript.contains("craftless.fabric.apiVersion"))
        assertTrue(buildScript.contains("craftless.fabric.javaMajorVersion"))
        assertTrue(buildScript.contains("craftless.fabric.laneId"))
        assertTrue(buildScript.contains("craftless.fabric.providerId"))
        assertTrue(buildScript.contains("craftless.fabric.artifactKey"))
        assertTrue(buildScript.contains("craftless.fabric.mappingsFingerprint"))
        assertTrue(buildScript.contains("\"official\" -> Unit"))
        assertTrue(buildScript.contains("\"official\""))
    }

    @Test
    fun `mise latest lane probe uses official mapping boundary not yarn remap lane`() {
        val mise = Files.readString(repositoryRoot().resolve(".mise.toml"))
        val latestTask =
            mise
                .substringAfter("[tasks.fabric-lane-check-latest-official]", missingDelimiterValue = "")
                .substringBefore("\n[tasks.", missingDelimiterValue = "")

        assertTrue(latestTask.isNotBlank(), "latest official Fabric lane probe task is missing")
        assertTrue(latestTask.contains("fabric-lane-check-latest-official.log"))
        assertTrue(latestTask.contains("fabric-lane-check-latest-official.status"))
        assertTrue(latestTask.contains("mise exec java@temurin-25.0.3+9.0.LTS gradle@9.6.0 -- gradle"))
        assertTrue(latestTask.contains(":driver-fabric-official:compileKotlin"))
        assertTrue(latestTask.contains(":driver-fabric-official:processResources"))
        assertTrue(latestTask.contains(":driver-fabric-official:jar"))
        assertFalse(latestTask.contains("-Pcraftless.fabric.mappingMode"))
        assertFalse(latestTask.contains("craftless.fabric.yarnMappings"))
    }

    @Test
    fun `latest official lane probe uses separate non remap module boundary`() {
        val root = repositoryRoot()
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        val rootBuild = Files.readString(root.resolve("build.gradle.kts"))
        val mise = Files.readString(root.resolve(".mise.toml"))
        val latestTask =
            mise
                .substringAfter("[tasks.fabric-lane-check-latest-official]", missingDelimiterValue = "")
                .substringBefore("\n[tasks.", missingDelimiterValue = "")
        val officialBuild = root.resolve("driver-fabric-official/build.gradle.kts")

        assertTrue(settings.contains("\"driver-fabric-official\""))
        assertTrue(rootBuild.contains("id(\"net.fabricmc.fabric-loom\") version \"1.17.12\" apply false"))
        assertTrue(latestTask.contains(":driver-fabric-official:compileKotlin"))
        assertTrue(latestTask.contains(":driver-fabric-official:processResources"))
        assertTrue(latestTask.contains(":driver-fabric-official:jar"))
        assertFalse(latestTask.contains(":driver-fabric:compileKotlin"))
        assertTrue(Files.exists(officialBuild), "official Fabric lane build file is missing")
        val officialBuildSource = Files.readString(officialBuild)
        assertTrue(officialBuildSource.contains("id(\"net.fabricmc.fabric-loom\")"))
        assertTrue(officialBuildSource.contains("com.mojang:minecraft:26.2"))
        assertTrue(officialBuildSource.contains("net.fabricmc:fabric-loader:0.19.3"))
        assertTrue(officialBuildSource.contains("net.fabricmc.fabric-api:fabric-api:0.153.0+26.2"))
        assertTrue(officialBuildSource.contains("JavaLanguageVersion.of(25)"))
        assertFalse(officialBuildSource.contains("fabric-loom-remap"))
        assertFalse(officialBuildSource.contains("craftless.fabric.yarnMappings"))
    }

    @Test
    fun `official lane uses shared fabric attach boundary without depending on yarn remap lane`() {
        val root = repositoryRoot()
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        val fabricBuild = Files.readString(root.resolve("driver-fabric/build.gradle.kts"))
        val officialBuild = Files.readString(root.resolve("driver-fabric-official/build.gradle.kts"))
        val officialEntrypoint =
            Files.readString(
                root.resolve(
                    "driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/CraftlessFabricOfficialEntrypoint.kt",
                ),
            )
        val officialSources =
            Files.walk(root.resolve("driver-fabric-official/src/main")).use { paths ->
                paths
                    .filter { path -> Files.isRegularFile(path) }
                    .map { path -> root.relativize(path) to Files.readString(path) }
                    .toList()
            }
        val forbiddenGameplayCatalogTokens =
            listOf(
                "DriverActionDescriptor(",
                "RuntimeOperationNode(",
                "player.chat",
                "inventory.query",
                "world.block.break",
                "entity.attack",
                "scenario",
            )

        assertTrue(settings.contains("\"driver-fabric-attach\""))
        assertTrue(fabricBuild.contains("project(\":driver-fabric-attach\")"))
        assertTrue(officialBuild.contains("project(\":driver-fabric-attach\")"))
        assertFalse(officialBuild.contains("project(\":driver-fabric\")"))
        assertTrue(officialEntrypoint.contains("FabricDriverSelfAttach.startFromEnvironment"))
        assertEquals(
            emptyList(),
            officialSources
                .flatMap { (path, source) ->
                    forbiddenGameplayCatalogTokens
                        .filter { token -> source.contains(token) }
                        .map { token -> "$path contains $token" }
                },
        )
    }

    @Test
    fun `official lane packages shared attach runtime dependencies without yarn remap gameplay lane`() {
        val officialBuild = Files.readString(repositoryRoot().resolve("driver-fabric-official/build.gradle.kts"))
        val requiredIncludes =
            listOf(
                "include(project(\":protocol\"))",
                "include(project(\":driver-api\"))",
                "include(project(\":driver-runtime\"))",
                "include(project(\":driver-fabric-attach\"))",
                "include(\"io.ktor:ktor-client-core-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-client-cio-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-server-core-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-server-cio-jvm:3.5.0\")",
                "include(\"org.jetbrains.kotlin:kotlin-stdlib:2.4.0\")",
                "include(\"org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0\")",
            )
        val forbiddenIncludes =
            listOf(
                "include(project(\":driver-fabric\"))",
                "include(project(\":daemon\"))",
                "include(project(\":bridge-hmc\"))",
            )

        assertEquals(
            emptyList(),
            requiredIncludes.filterNot { include -> officialBuild.contains(include) },
        )
        assertEquals(
            emptyList(),
            forbiddenIncludes.filter { include -> officialBuild.contains(include) },
        )
    }

    @Test
    fun `official lane has opt in launch attach probe task without packaging support claim`() {
        val root = repositoryRoot()
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        val fabricBuild = Files.readString(root.resolve("driver-fabric/build.gradle.kts"))
        val officialBuild = Files.readString(root.resolve("driver-fabric-official/build.gradle.kts"))
        val operatingContract = Files.readString(root.resolve("docs/agent-operating-contract.md"))
        val moduleContracts = Files.readString(root.resolve("docs/agent-module-contracts.md"))
        val officialAgents = Files.readString(root.resolve("driver-fabric-official/AGENTS.md"))
        val attachAgents = Files.readString(root.resolve("driver-fabric-attach/AGENTS.md"))
        val fabricBackend =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val fabricCapabilityProbe =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt",
                ),
            )
        val officialBackend =
            Files.readString(
                root.resolve(
                    "driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt",
                ),
            )
        val fabricRuntimeGraph =
            Files.readString(
                root.resolve(
                    "driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeGraphFragment.kt",
                ),
            )
        val fabricRegistryGraph =
            Files.readString(
                root.resolve(
                    "driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRegistryGraph.kt",
                ),
            )
        val fabricEventGraph =
            Files.readString(
                root.resolve(
                    "driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricEventGraph.kt",
                ),
            )
        val fabricClientStateGraph =
            Files.readString(
                root.resolve(
                    "driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricClientStateGraphSnapshot.kt",
                ),
            )
        val officialSources =
            Files.walk(root.resolve("driver-fabric-official/src/main/kotlin")).use { paths ->
                paths
                    .filter { path -> Files.isRegularFile(path) }
                    .filter { path -> path.toString().endsWith(".kt") }
                    .map { path -> Files.readString(path) }
                    .toList()
                    .joinToString("\n")
            }
        val probeRunner =
            Files.readString(
                root.resolve(
                    "driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt",
                ),
            )
        val manifestFiles =
            listOf(
                root.resolve("cli/src/main/resources/driver-mods.json"),
                root.resolve("build/docker/craftless/driver-mods.json"),
            ).filter(Files::isRegularFile)

        assertTrue(officialBuild.contains("officialFabricAttachProbe"))
        assertTrue(officialBuild.contains("sourceSets.test.get().runtimeClasspath"))
        assertTrue(
            officialBuild.contains(
                "com.minekube.craftless.driver.fabric.official.probe.OfficialFabricAttachProbeKt",
            ),
        )
        assertTrue(officialBuild.contains("CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE"))
        assertTrue(officialBuild.contains("java@temurin-25.0.3+9.0.LTS"))
        assertTrue(officialBuild.contains("gradle@9.6.0"))
        assertTrue(officialBuild.contains(":driver-fabric-official:runClient"))
        assertTrue(settings.contains("\"driver-fabric-discovery\""))
        assertTrue(fabricBuild.contains("project(\":driver-fabric-discovery\")"))
        assertTrue(officialBuild.contains("project(\":driver-fabric-discovery\")"))
        assertTrue(operatingContract.contains("Version breadth is a system property"))
        assertTrue(operatingContract.contains("Add per-version code only after"))
        assertTrue(operatingContract.contains("proving an actual Minecraft, Fabric API, mapping"))
        assertTrue(officialAgents.contains("docs/agent-module-contracts.md#driver-fabric-official"))
        assertTrue(attachAgents.contains("docs/agent-module-contracts.md#driver-fabric-attach"))
        assertTrue(moduleContracts.contains("Treat this module as a compatibility lane and probe boundary"))
        assertTrue(moduleContracts.contains("only for proven"))
        assertTrue(moduleContracts.contains("loopback transport shared by all Fabric driver lanes"))
        assertTrue(moduleContracts.contains("keep that behavior in the"))
        assertTrue(moduleContracts.contains("lane adapter"))
        assertFalse(fabricBackend.contains("internal data class FabricRuntimeMetadataSnapshot"))
        assertFalse(fabricBackend.contains("internal class SnapshotFabricRuntimeMetadataProvider"))
        assertFalse(fabricCapabilityProbe.contains("internal data class FabricCapabilityGraphFragment"))
        assertTrue(fabricCapabilityProbe.contains("typealias FabricCapabilityGraphFragment = FabricRuntimeGraphFragment"))
        assertFalse(fabricBackend.contains("private fun FabricLoader.installedMods()"))
        assertFalse(officialSources.contains("OfficialFabricRuntimeMetadataProvider"))
        assertFalse(officialSources.contains("OfficialFabricRuntimeMetadataSnapshot"))
        assertTrue(officialSources.contains("SnapshotFabricRuntimeMetadataProvider"))
        assertTrue(officialSources.contains("FabricLoaderRuntimeMetadataReader"))
        assertTrue(officialBackend.contains("FabricRuntimeMetadataProvider"))
        assertTrue(officialBackend.contains("fabricRuntimeMetadataGraph"))
        assertTrue(fabricRuntimeGraph.contains("fabricRuntimeResourceNode"))
        assertTrue(fabricRuntimeGraph.contains("FabricRuntimeGraphFragment"))
        assertTrue(fabricRegistryGraph.contains("fabricRegistryGraphFragment"))
        assertTrue(fabricRegistryGraph.contains("registry.entity"))
        assertTrue(officialBackend.contains("fabricRegistryGraphFragment"))
        assertFalse(fabricCapabilityProbe.contains("RuntimeResourceNode(\n                        id = \"registry\""))
        assertTrue(fabricEventGraph.contains("fabricEventGraphFragment"))
        assertTrue(fabricEventGraph.contains("event.lifecycle"))
        assertTrue(officialBackend.contains("fabricEventGraphFragment"))
        assertFalse(fabricCapabilityProbe.contains("RuntimeResourceNode(\n                        id = \"event\""))
        assertFalse(fabricCapabilityProbe.contains("RuntimeEventNode(\n                        id = \"event.\$id\""))
        assertTrue(fabricClientStateGraph.contains("fabricClientStateGraphFragment"))
        assertTrue(fabricClientStateGraph.contains("inventory.slot"))
        assertTrue(officialSources.contains("OfficialFabricClientStateProvider"))
        assertTrue(officialSources.contains("net.minecraft.client.Minecraft"))
        assertTrue(officialSources.contains("MinecraftOfficialFabricClientConnector"))
        assertTrue(officialSources.contains("ConnectScreen.startConnecting"))
        assertTrue(officialSources.contains("ServerData.Type.OTHER"))
        assertFalse(officialSources.contains("FabricClientGateway"))
        assertFalse(officialSources.contains("FabricOperationAdapters"))
        assertTrue(officialBackend.contains("fabricClientStateGraphFragment"))
        assertFalse(officialBackend.contains("FabricClientStateGraphSnapshot.disconnected()"))
        assertFalse(officialBackend.contains("metadata-only backend"))
        assertFalse(fabricCapabilityProbe.contains("RuntimeResourceNode(\"client\""))
        assertFalse(fabricCapabilityProbe.contains("RuntimeHandleNode(\n                        id = \"inventory.slot\""))
        assertFalse(officialBackend.contains("import com.minekube.craftless.protocol.RuntimeResourceNode"))
        assertFalse(officialBackend.contains("import com.minekube.craftless.protocol.RuntimeCapabilityGraph"))
        assertFalse(officialBackend.contains("mods:official-lane-probe"))
        assertFalse(officialBackend.contains("registries:unavailable"))
        assertFalse(officialBackend.contains("server-features:unavailable"))
        assertTrue(probeRunner.contains("builder.environment()[\"CRAFTLESS_CLIENT_ID\"]"))
        assertTrue(probeRunner.contains("builder.environment()[\"CRAFTLESS_DAEMON_URL\"]"))
        assertTrue(probeRunner.contains("CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT"))
        assertTrue(probeRunner.contains("/clients/\${config.clientId}:connect"))
        assertTrue(probeRunner.contains("client-openapi-connected.json"))
        assertTrue(probeRunner.contains("runCatching"))
        assertTrue(probeRunner.contains("error !is IOException"))
        assertTrue(
            probeRunner.indexOf("val openApiText") <
                probeRunner.indexOf("command.stopAndWriteLog"),
            "official attach probe must fetch per-client OpenAPI before stopping the attached client",
        )
        assertTrue(probeRunner.contains("exitProcess(1)"))
        manifestFiles.forEach { manifest ->
            assertFalse(
                Files.readString(manifest).contains("driver-fabric-official"),
                "${root.relativize(manifest)} must not package the official probe lane as supported",
            )
        }
    }

    @Test
    fun `cli driver mod manifest projection carries runtime identity not build fields`() {
        val buildScript = Files.readString(repositoryRoot().resolve("cli/build.gradle.kts"))

        assertTrue(buildScript.contains("\"fabricApiVersion\" to requiredCatalogString(entry, \"fabricApiVersion\")"))
        assertTrue(buildScript.contains("\"javaMajorVersion\" to requiredCatalogInt(entry, \"javaMajorVersion\")"))
        assertTrue(buildScript.contains("\"mappingsFingerprint\" to requiredCatalogString(entry, \"mappingsFingerprint\")"))
        assertFalse(buildScript.contains("\"artifactKey\" to"))
        assertFalse(buildScript.contains("\"distributionPath\" to"))
    }

    @Test
    fun `fabric gateway does not expose raw command dispatch`() {
        assertTrue(FabricClientGateway::class.java.methods.none { it.name == "dispatchCommand" })
    }

    @Test
    fun `fabric gateway exposes generic runtime boundaries instead of action-specific methods`() {
        val methodNames =
            FabricClientGateway::class.java.methods
                .map { it.name }
                .toSet()

        assertTrue("executeOnClient" in methodNames)
        assertTrue("dispatchChatMessage" !in methodNames)
        assertTrue("move" !in methodNames)
    }

    @Test
    fun `fabric event hooks record internal mixin events with generic evidence`() {
        val before = FabricEventHooks.snapshot().clientTicks

        FabricEventHooks.recordClientTick()

        val sourceEvidence = FabricEventHooks.sourceEvidence().single()
        assertEquals(before + 1, FabricEventHooks.snapshot().clientTicks)
        assertEquals("mixin", sourceEvidence.kind)
        assertEquals("craftless-client-tick", sourceEvidence.fingerprint)
        assertFalse(sourceEvidence.fingerprint.contains("minecraft"))
    }

    @Test
    fun `fabric api callback event sources use generic craftless evidence`() {
        val sourceEvidence = FabricEventCallbacks.sourceEvidence()

        assertEquals(setOf("callback"), sourceEvidence.map { it.kind }.toSet())
        assertTrue(
            sourceEvidence.map { it.fingerprint }.containsAll(
                setOf(
                    "craftless-callback-client-tick-start",
                    "craftless-callback-client-tick-end",
                    "craftless-callback-client-world-change",
                    "craftless-callback-client-entity-load",
                    "craftless-callback-client-entity-unload",
                    "craftless-callback-play-join",
                    "craftless-callback-play-disconnect",
                ),
            ),
        )
        assertTrue(sourceEvidence.none { it.fingerprint.contains("minecraft") })
        assertTrue(sourceEvidence.none { it.fingerprint.contains("fabric") })
    }

    @Test
    fun `fabric event callbacks do not compile against optional world change event`() {
        val source =
            Files.readString(
                repositoryRoot()
                    .resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricEventCallbacks.kt"),
            )

        assertFalse(source.contains("ClientWorldEvents"))
    }

    @Test
    fun `fabric movement bindings do not compile against player input record`() {
        val source =
            Files.readString(
                repositoryRoot()
                    .resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt"),
            )

        assertFalse(source.contains("PlayerInput"))
    }

    @Test
    fun `fabric recipe bridge does not compile against version-specific recipe display types`() {
        val root = repositoryRoot()
        val backend =
            Files.readString(
                root.resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt"),
            )
        val projection =
            Files.readString(
                root.resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricRecipeProjection.kt"),
            )
        val mixins =
            Files.readString(root.resolve("driver-fabric/src/main/resources/craftless-driver-fabric.mixins.json"))
        val forbidden =
            listOf(
                "NetworkRecipeId",
                "RecipeDisplayEntry",
                "RecipeFinder",
                "AbstractCraftingScreenHandler",
                "net.minecraft.recipe.display",
                "ClientRecipeBookAccessor",
            )

        forbidden.forEach { token ->
            assertFalse(backend.contains(token), "backend contains $token")
            assertFalse(projection.contains(token), "projection contains $token")
            assertFalse(mixins.contains(token), "mixin config contains $token")
        }
    }

    @Test
    fun `fabric backend exposes driver runtime actions without changing daemon contract`() {
        val backend = FabricDriverBackend.metadataOnly()

        assertEquals("craftless-driver-fabric", backend.runtimeMetadata("alice").driver)
        assertEquals("0.1.0-SNAPSHOT", backend.runtimeMetadata("alice").driverVersion)
        assertEquals("craftless-fabric-bindings", backend.runtimeMetadata("alice").mappings)
        assertEquals(
            DriverBackendAction.CONNECT,
            backend.connect("alice", ConnectionTarget("127.0.0.1", 25565)).action,
        )
        val stop = backend.stop("alice")
        assertEquals(DriverBackendAction.STOP, stop.action)
        assertTrue(stop.message?.contains("metadata-only") == true)
        assertTrue(backend.events().any { it.contains("connect alice 127.0.0.1:25565") })
    }

    @Test
    fun `fabric backend runtime graph includes sanitized compatibility lane evidence`() {
        val backend = FabricDriverBackend.metadataOnly()

        val runtime = backend.runtimeGraph("alice").resources.single { it.id == "runtime" }
        val evidence = runtime.sourceEvidence.associate { it.kind to it.fingerprint }

        assertEquals("current-lane", evidence["runtime-lane"])
        assertEquals("current-lane", evidence["runtime-provider"])
        assertEquals("supported", evidence["runtime-status"])
        assertEquals("java:21", evidence["runtime-java"])
        assertTrue(evidence.values.none { value -> "fabric" in value || "minecraft" in value || "yarn" in value })
    }

    @Test
    fun `fabric real backend runtime metadata comes from runtime provider`() {
        val backend =
            FabricDriverBackend.real(
                gateway = RecordingFabricClientGateway(),
                runtimeMetadataProvider =
                    FabricRuntimeMetadataProvider {
                        DriverRuntimeMetadata(
                            loaderVersion = "0.19.3",
                            driver = "craftless-driver-fabric",
                            driverVersion = "0.1.0-SNAPSHOT",
                            mappings = "craftless-fabric-bindings",
                            installedModsFingerprint = "mods:test-runtime",
                            registryFingerprint = "registries:client-boundary",
                            serverFeatureFingerprint = "server-features:disconnected",
                            permissionsFingerprint = "permissions:local-client",
                        )
                    },
            )

        val metadata = backend.runtimeMetadata("alice")

        assertEquals("0.19.3", metadata.loaderVersion)
        assertEquals("mods:test-runtime", metadata.installedModsFingerprint)
        assertEquals("registries:client-boundary", metadata.registryFingerprint)
        assertEquals("server-features:disconnected", metadata.serverFeatureFingerprint)
        assertEquals("permissions:local-client", metadata.permissionsFingerprint)
    }

    @Test
    fun `fabric backend exposes runtime capability graph from probes`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.queryResults.add(
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
            ),
        )
        gateway.queryResults.add(true)
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider =
                    FabricRuntimeMetadataProvider {
                        DriverRuntimeMetadata(
                            loaderVersion = "0.19.3",
                            driver = "craftless-driver-fabric",
                            driverVersion = "0.1.0-SNAPSHOT",
                            mappings = "craftless-fabric-bindings",
                            installedModsFingerprint = "mods:test-runtime",
                            registryFingerprint = "registries:test-runtime",
                            serverFeatureFingerprint = "server-features:test-runtime",
                            permissionsFingerprint = "permissions:local-client",
                        )
                    },
            )

        val graph = backend.runtimeGraph("alice")

        assertEquals("alice", graph.clientId)
        assertTrue(graph.resources.any { it.id == "runtime" })
        assertTrue(graph.resources.any { it.id == "player" })
        assertTrue(graph.operations.any { it.id == "player.query" })
        assertTrue(graph.operations.any { it.id == "world.block.interact" })
        assertTrue(graph.fingerprint().startsWith("graph:"))
    }

    @Test
    fun `transitional fabric binding operation ids are represented as runtime graph operations`() {
        val executionAdapterIds = defaultFabricExecutionAdapters().map { adapter -> adapter.operationId }.sorted()
        val definitionIds = fabricBootstrapOperationDefinitions().map { definition -> definition.id }.sorted()
        val graphOperationIds =
            FabricDriverBackend
                .metadataOnly()
                .runtimeGraph("alice")
                .operations
                .map { it.id }
                .sorted()

        assertEquals(
            definitionIds.filter { id -> id in executionAdapterIds },
            executionAdapterIds,
            "Private Fabric execution adapters must expose operation ids only through bootstrap definitions.",
        )
        assertEquals(executionAdapterIds, graphOperationIds.filter { it in executionAdapterIds })
    }

    @Test
    fun `fabric execution adapters do not own operation id literals`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt",
                ),
            )
        val operationIdLiterals =
            Regex("""operationId(?:\s*:\s*String)?\s*=\s*"([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*)"""")
                .findAll(source)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toList()

        assertEquals(
            emptyList(),
            operationIdLiterals,
            "Private Fabric execution adapters must reference bootstrap operation ids instead of owning operation id literals.",
        )
    }

    @Test
    fun `fabric execution adapters do not use stale action binding names`() {
        val root = repositoryRoot()
        val sourceRoot = root.resolve("driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6")
        val sources =
            listOf("FabricExecutionAdapters.kt", "FabricDriverBackend.kt")
                .joinToString(separator = "\n") { file -> Files.readString(sourceRoot.resolve(file)) }
        val forbidden =
            listOf(
                "FabricActionBinding",
                "defaultFabricActionBindings",
                "actionBindings",
                "actionBindingsById",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> sources.contains(token) },
            "Private Fabric execution adapters must not use stale execution adapter names.",
        )
    }

    @Test
    fun `bootstrap operation definitions do not hand maintain public resource ownership`() {
        val source =
            Files.readString(
                repositoryRoot().resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt",
                ),
            )
        val forbidden =
            listOf(
                "val resource: String",
                "resource = \"player\"",
                "resource = \"inventory\"",
                "resource = \"recipe\"",
                "resource = \"entity\"",
                "resource = \"world\"",
                "resource = \"screen\"",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Transitional bootstrap operation definitions must not duplicate public resource ownership. " +
                "Resource ids should be derived from operation graph metadata instead of hand-maintained catalog fields.",
        )
    }

    @Test
    fun `bootstrap operation definitions do not own private adapter key pairs`() {
        val source =
            Files.readString(
                repositoryRoot().resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt",
                ),
            )
        val forbidden =
            listOf(
                "val adapter: String",
                "adapter = FabricBootstrapOperationAdapters",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Bootstrap operation definitions should describe transitional graph shape only. " +
                "Private execution adapter keys must live in a separate adapter-key mapping.",
        )
    }

    @Test
    fun `fabric client state probe does not own bootstrap operation definitions`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt",
                ),
            )
        val forbidden =
            listOf(
                "RuntimeOperationNode(",
                "\"player.chat\"",
                "\"inventory.query\"",
                "\"recipe.query\"",
                "\"entity.attack\"",
                "\"world.block.query\"",
                "\"world.block.break\"",
                "\"screen.close\"",
                "\"fabric.player-chat\"",
                "\"fabric.inventory-query\"",
                "\"fabric.recipe-query\"",
                "\"fabric.entity-attack\"",
                "\"fabric.world-block-query\"",
                "\"fabric.world-block-break\"",
                "\"fabric.screen-close\"",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "FabricClientStateCapabilityProbe must not own bootstrap operation definitions; " +
                "they belong in the transitional graph bootstrap definition layer.",
        )
    }

    @Test
    fun `bootstrap operation definitions still project into runtime graph`() {
        val definitionIds = fabricBootstrapOperationDefinitions().map { definition -> definition.id }.sorted()
        val graphOperationIds =
            FabricDriverBackend
                .metadataOnly()
                .runtimeGraph("alice")
                .operations
                .map { operation -> operation.id }
                .toSet()

        assertTrue(definitionIds.isNotEmpty(), "Bootstrap operation definition ids must be discoverable.")
        assertEquals(
            definitionIds,
            definitionIds.filter { id -> id in graphOperationIds },
            "Every transitional bootstrap definition must project into the runtime graph.",
        )
    }

    @Test
    fun `fabric runtime metadata fingerprints runtime registry entries`() {
        val provider =
            SnapshotFabricRuntimeMetadataProvider(
                FabricRuntimeMetadataSnapshot(
                    loaderVersion = "0.19.3",
                    driver = "craftless-driver-fabric",
                    driverVersion = "0.1.0-SNAPSHOT",
                    mappings = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
                    installedModsFingerprint =
                        fabricRuntimeFingerprint("mods", listOf("minecraft@1.21.6", "fabricloader@0.19.3")),
                    registryFingerprint =
                        fabricRuntimeFingerprint("registries", listOf("item:minecraft:iron_sword", "block:minecraft:stone")),
                    serverFeatureFingerprint = fabricRuntimeFingerprint("server-features", listOf("environment:dev")),
                ),
            )

        val metadata = provider.runtimeMetadata("alice")

        assertEquals("0.19.3", metadata.loaderVersion)
        assertTrue(metadata.installedModsFingerprint.startsWith("mods:"))
        assertTrue(metadata.registryFingerprint.startsWith("registries:"))
        assertTrue(metadata.serverFeatureFingerprint.startsWith("server-features:"))
        assertFalse(metadata.registryFingerprint.contains("client-boundary"))
        assertFalse(metadata.registryFingerprint.contains("metadata-only"))
    }

    @Test
    fun `fabric server feature metadata is queried from client gateway`() {
        val gateway = RecordingFabricClientGateway()
        gateway.queryResults.add(listOf("connection:connected", "server:remote", "feature-set:abc123"))

        val features = GatewayFabricServerFeatureProvider(gateway).serverFeatures()

        assertEquals(listOf("connection:connected", "server:remote", "feature-set:abc123"), features)
        assertEquals(listOf("client-query"), gateway.actions)
    }

    @Test
    fun `fabric client smoke plan is opt in and bridge independent`() {
        val plan = FabricClientSmokePlan.default()

        assertEquals("CRAFTLESS_FABRIC_CLIENT_SMOKE", plan.environmentGate)
        assertEquals(FabricCompiledLaneMetadata.MINECRAFT_VERSION, plan.minecraftVersion)
        assertEquals(listOf(":driver-fabric:fabricClientSmoke"), plan.gradleTasks)
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.START_LOCAL_SERVER })
        assertTrue(plan.steps.any { it.description.contains("kept running", ignoreCase = true) })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.LAUNCH_FABRIC_CLIENT })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.INVOKE_GENERATED_CHAT_ACTION })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.INVOKE_GENERATED_GAMEPLAY_ACTIONS })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.ASSERT_SERVER_EVIDENCE })
        assertTrue(plan.artifacts.contains("server-evidence.jsonl"))
        assertTrue(plan.artifacts.contains("client-openapi.json"))
        assertTrue(plan.artifacts.contains("client-resources.json"))
        assertTrue(plan.artifacts.contains("client-openapi-connected.json"))
        assertTrue(plan.artifacts.contains("client-actions-connected.json"))
        assertTrue(plan.artifacts.contains("client-resources-connected.json"))
        assertTrue(plan.artifacts.contains("gameplay-results.jsonl"))
        assertTrue(plan.steps.none { it.description.contains("hmc", ignoreCase = true) })
        assertTrue(plan.steps.none { it.description.contains("headlessmc", ignoreCase = true) })
    }

    @Test
    fun `fabric final gameplay plan gates completion on graph streams and Codex evidence`() {
        val plan = FabricFinalGameplayPlan.default()

        assertEquals("CRAFTLESS_FINAL_GAMEPLAY", plan.environmentGate)
        assertEquals(FabricCompiledLaneMetadata.MINECRAFT_VERSION, plan.minecraftVersion)
        assertEquals(listOf(":driver-fabric:fabricFinalGameplay"), plan.gradleTasks)
        assertEquals("driver-fabric/build/craftless-final-gameplay/artifacts", plan.artifactsDirectory)
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.START_LOCAL_SERVER })
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.LAUNCH_VISIBLE_FABRIC_CLIENT })
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.FETCH_GRAPH_OPENAPI })
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.SUBSCRIBE_SSE })
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.INVOKE_DISCOVERED_GAMEPLAY })
        assertTrue(plan.steps.any { it.description.contains("ready evidence", ignoreCase = true) })
        assertTrue(plan.steps.any { it.description.contains("optional", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("CRAFTLESS_ENABLE_PATHFINDER_BACKEND", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("driver-fabric/build/pathfinder", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("SHA-256", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("api-fabric", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("Loom", ignoreCase = true) && it.contains("remapped", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.any { it.contains("nested", ignoreCase = true) && it.contains("remapped", ignoreCase = true) })
        assertTrue(plan.runtimePreparations.none { it.contains("server-side item provisioning", ignoreCase = true) })
        assertTrue(plan.artifacts.contains("client-openapi-connected.json"))
        assertTrue(plan.artifacts.contains("client-actions-connected.json"))
        assertTrue(plan.artifacts.contains("client-resources-connected.json"))
        assertTrue(plan.artifacts.contains("client-events.jsonl"))
        assertTrue(plan.artifacts.contains("gameplay-results.jsonl"))
        assertTrue(plan.artifacts.contains("public-agent-gameplay-results.jsonl"))
        assertTrue(plan.artifacts.contains("public-agent-state.jsonl"))
        assertTrue(plan.artifacts.contains("final-gameplay-ready.json"))
        assertFalse(plan.artifacts.contains("survival-task-results.jsonl"))
        assertTrue(plan.artifacts.contains("server-evidence.jsonl"))
        assertTrue(plan.completionGates.none { it.contains("Robin", ignoreCase = true) })
        assertTrue(plan.completionGates.none { it.contains("Minecraft chat", ignoreCase = true) })
        assertTrue(plan.completionGates.any { it.contains("Codex", ignoreCase = true) && it.contains("evidence", ignoreCase = true) })
        assertTrue(plan.completionGates.any { it.contains("SSE", ignoreCase = true) })
        assertTrue(plan.completionGates.any { it.contains("no server-side item provisioning", ignoreCase = true) })
        assertFalse(plan.completionGates.any { it.contains("provisioned", ignoreCase = true) && !it.contains("no", ignoreCase = true) })
        assertTrue(plan.completionGates.none { it.contains("static fallback", ignoreCase = true) && !it.contains("no", ignoreCase = true) })
        assertFalse(plan.artifacts.contains("provisioned-iron-sword"))
    }

    @Test
    fun `fabric final gameplay defaults to Codex evidence gate without chat confirmation phrase`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("\"CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS\""))
        assertFalse(buildScript.contains("?: \"goal may be completed\""))
        assertFalse(buildScript.contains("confirm in Minecraft chat"))
        assertFalse(buildScript.contains("say \\\"Robin, Craftless final gameplay is ready"))
    }

    @Test
    fun `fabric final gameplay outer timeout covers public agent runtime and optional hold window`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("\"CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS\""))
        assertTrue(buildScript.contains("\"CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS\""))
        assertTrue(buildScript.contains("\"CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS\""))
        assertTrue(buildScript.contains("\"CRAFTLESS_LOCAL_SERVER_SMOKE_ACTION_TIMEOUT_MS\""))
        assertTrue(buildScript.contains("finalGameplayPublicAgentCommandTimeout"))
        assertTrue(buildScript.contains("publicAgentCommandMillis + holdMillis + 180_000L"))
        assertFalse(buildScript.contains("fabricActionMillis + holdMillis + 180_000L"))
        assertTrue(buildScript.contains("1_500_000L"))
        assertTrue(buildScript.contains("720_000L"))
        assertTrue(buildScript.contains("2_700_000L"))
        assertTrue(buildScript.contains("?: \"600000\""))
    }

    @Test
    fun `final gameplay config exports public agent action request timeout below fabric action timeout`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("finalGameplayPublicAgentActionRequestTimeout"))
        assertTrue(buildScript.contains("\"CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS\""))
        assertTrue(buildScript.contains("fabricActionMillis - 10_000L"))
    }

    @Test
    fun `fabric smoke controller prefers fabric action timeout over outer server timeout`() {
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "1500000",
                    "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "720000",
                ),
            )

        assertEquals(720_000.milliseconds, controller.actionTimeout)
    }

    @Test
    fun `fabric smoke controller gives public agent process the outer smoke timeout`() {
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "1500000",
                    "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "120000",
                ),
            )

        assertEquals(120_000.milliseconds, controller.actionTimeout)
        assertEquals(1_500_000.milliseconds, controller.publicAgentCommandTimeout)
    }

    @Test
    fun `fabric smoke controller parses public agent process timeout separately from outer smoke timeout`() {
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "2100000",
                    "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "120000",
                    "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS" to "2400000",
                ),
            )

        assertEquals(120_000.milliseconds, controller.actionTimeout)
        assertEquals(2_400_000.milliseconds, controller.publicAgentCommandTimeout)
    }

    @Test
    fun `fabric client smoke passes runtime lane evidence before launching client`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("fabricSmokeRuntimeLaneJson"))
        assertTrue(buildScript.contains("\"CRAFTLESS_SMOKE_RUNTIME_LANE_JSON\""))
        assertTrue(buildScript.contains("\"unsupported-version\""))
        assertFalse(buildScript.contains("latest-release-26-2"))
        assertFalse(buildScript.contains("older-release-1-20-6"))
        assertFalse(buildScript.contains("\"26.2\" ->"))
    }

    @Test
    fun `fabric client smoke runClient command preserves parameterized lane properties`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("fabricSmokeLaneGradleProperties"))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.minecraftVersion=\$fabricCompiledMinecraftVersion\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.yarnMappings=\$fabricCompiledYarnMappings\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.loaderVersion=\$fabricCompiledLoaderVersion\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.apiVersion=\$fabricCompiledApiVersion\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.javaMajorVersion=\$fabricCompiledJavaMajorVersion\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.laneId=\$fabricCompiledLaneId\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.providerId=\$fabricCompiledProviderId\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.artifactKey=\$fabricCompiledArtifactKey\""))
        assertTrue(buildScript.contains("\"-Pcraftless.fabric.mappingsFingerprint=\$fabricCompiledMappingsFingerprint\""))
        assertTrue(buildScript.contains("fabricSmokeLaneGradleProperties() + listOf(\":driver-fabric:runClient\")"))
    }

    @Test
    fun `active smoke fixtures do not keep static latest unsupported lane ids`() {
        val smokeTest =
            Files.readString(
                repositoryRoot().resolve(
                    "testkit/src/test/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmokeTest.kt",
                ),
            )

        assertFalse(smokeTest.contains("latest-release-26-2"))
        assertFalse(smokeTest.contains("older-release-1-20-6"))
        assertTrue(smokeTest.contains("fabric-unsupported-26-2"))
    }

    @Test
    fun `fabric run client consumes resolved smoke Java executable`() {
        val buildScript = Files.readString(repositoryRoot().resolve("driver-fabric/build.gradle.kts"))

        assertTrue(buildScript.contains("fabricClientJavaExecutable"))
        assertTrue(buildScript.contains("\"CRAFTLESS_SMOKE_JAVA_EXECUTABLE\""))
        assertTrue(buildScript.contains("setExecutable(fabricClientJavaExecutable)"))
    }

    @Test
    fun `fabric backend schedules generated actions through generic client execution`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)

        backend.connect("alice", ConnectionTarget("127.0.0.1", 25565))
        backend.invoke(
            "alice",
            DriverActionInvocation("player.chat", mapOf("message" to JsonPrimitive("hello client"))),
        )
        backend.stop("alice")

        assertEquals(3, gateway.scheduled)
        assertEquals(
            listOf(
                "connect 127.0.0.1:25565",
                "client-action",
                "stop",
            ),
            gateway.actions,
        )
    }

    @Test
    fun `fabric backend reports connect as unobserved until gateway is connected`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connectMarksConnected = false
        val backend = FabricDriverBackend.real(gateway)

        val result = backend.connect("alice", ConnectionTarget("127.0.0.1", 25565))

        assertEquals(DriverBackendAction.CONNECT, result.action)
        assertFalse(result.observed)
        assertEquals(listOf("connect 127.0.0.1:25565"), gateway.actions)
    }

    @Test
    fun `fabric backend exposes bootstrap bindings as graph operation adapters`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val operation = backend.runtimeGraph("alice").operations.single { it.id == "player.chat" }

        val result =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = operation,
                        arguments = mapOf("message" to JsonPrimitive("hello adapter")),
                    ),
                )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals("hello adapter", result.message)
        assertEquals(listOf("client-action"), gateway.actions)
    }

    @Test
    fun `fabric backend maps player move action to movement intent`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)
        gateway.queryResult =
            buildJsonObject {
                put("ticks", 20)
                put(
                    "input",
                    buildJsonObject {
                        put("forward", true)
                        put("jump", true)
                    },
                )
                put(
                    "position-before",
                    buildJsonObject {
                        put("x", 10.0)
                        put("y", 64.0)
                        put("z", -3.5)
                    },
                )
            }

        val move = backend.actions("alice").single { it.id == "player.move" }

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.move",
                    arguments =
                        mapOf(
                            "forward" to JsonPrimitive(true),
                            "jump" to JsonPrimitive(true),
                            "ticks" to JsonPrimitive(20),
                        ),
                ),
            )

        assertEquals("object", move.result.properties["data"]?.type)
        assertEquals("player.move", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(20, result.data["ticks"]?.jsonPrimitive?.int)
        val input = requireNotNull(result.data["input"]?.jsonObject)
        assertEquals(true, input["forward"]?.jsonPrimitive?.boolean)
        assertEquals(true, input["jump"]?.jsonPrimitive?.boolean)
        val positionBefore = requireNotNull(result.data["position-before"]?.jsonObject)
        assertEquals(-3.5, positionBefore["z"]?.jsonPrimitive?.content?.toDoubleOrNull())
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns machine readable movement failure before scheduling gateway`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.move",
                    arguments =
                        mapOf(
                            "forward" to JsonPrimitive(true),
                            "ticks" to JsonPrimitive(0),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-ticks", result.message)
        assertEquals(false, result.data["moved"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-ticks", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend maps player chat action to chat execution`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("hello action")),
                ),
            )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("client-action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend invokes entity query through runtime graph adapter`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("origin", buildJsonObject { put("x", 0.0) })
                put("radius", 16.0)
                put("count", 1)
                put(
                    "entities",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("handle", "entity.handle-42")
                                put("label", "Cow")
                                put("category", "passive")
                            },
                        )
                    },
                )
            }
        val backend = smokeBackend(gateway)
        val operation = backend.runtimeGraph("alice").operations.single { it.id == "entity.query" }

        val result =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = operation,
                        arguments =
                            mapOf(
                                "radius" to JsonPrimitive(16.0),
                                "limit" to JsonPrimitive(10),
                            ),
                    ),
                )

        assertEquals("entity.query", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(1, result.data["count"]?.jsonPrimitive?.int)
        val entity =
            requireNotNull(
                result.data["entities"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject,
            )
        assertEquals("entity.handle-42", entity["handle"]?.jsonPrimitive?.content)
        assertEquals("Cow", entity["label"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
    }

    @Test
    fun `fabric backend returns machine readable entity query failures for invalid bounds`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = smokeBackend(gateway)
        val entityQuery = backend.runtimeGraph("alice").operations.single { it.id == "entity.query" }

        assertEquals("string", entityQuery.result.properties["reason"]?.type)

        val invalidRadius =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = entityQuery,
                        arguments = mapOf("radius" to JsonPrimitive(0.0)),
                    ),
                )
        val invalidLimit =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = entityQuery,
                        arguments = mapOf("limit" to JsonPrimitive(0)),
                    ),
                )

        assertEquals(DriverActionStatus.FAILED, invalidRadius.status)
        assertEquals("invalid-radius", invalidRadius.message)
        assertEquals("invalid-radius", invalidRadius.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, invalidRadius.data["count"]?.jsonPrimitive?.int)
        assertEquals(0, invalidRadius.data["entities"]?.jsonArray?.size)
        assertEquals(DriverActionStatus.FAILED, invalidLimit.status)
        assertEquals("invalid-limit", invalidLimit.message)
        assertEquals("invalid-limit", invalidLimit.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, invalidLimit.data["count"]?.jsonPrimitive?.int)
        assertEquals(0, invalidLimit.data["entities"]?.jsonArray?.size)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend invokes entity attack through runtime graph adapter`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = smokeBackend(gateway)

        val unavailableAttack = backend.runtimeGraph("alice").operations.single { it.id == "entity.attack" }
        val unavailableResult =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = unavailableAttack,
                        arguments =
                            mapOf(
                                "target" to
                                    buildJsonObject {
                                        put("handle", "entity.handle-42")
                                    },
                            ),
                    ),
                )

        assertEquals("fabric.entity-attack", unavailableAttack.adapter)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, unavailableAttack.availability.state)
        assertEquals("client-not-connected", unavailableAttack.availability.reason)
        assertEquals("object", unavailableAttack.arguments["target"]?.type)
        assertEquals(true, unavailableAttack.arguments["target"]?.required)
        assertEquals("number", unavailableAttack.arguments["max-distance"]?.type)
        assertEquals("object", unavailableAttack.result.type)
        assertEquals("boolean", unavailableAttack.result.properties["hit"]?.type)
        assertEquals("string", unavailableAttack.result.properties["reason"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("handle", "entity.handle-42")
                put("label", "Cow")
                put("category", "passive")
                put("distance", 2.5)
                put("hit", true)
            }

        val attack = backend.runtimeGraph("alice").operations.single { it.id == "entity.attack" }
        gateway.actions.clear()
        gateway.scheduled = 0
        val result =
            backend
                .operationAdapters("alice")
                .invoke(
                    DriverOperationInvocation(
                        clientId = "alice",
                        operation = attack,
                        arguments =
                            mapOf(
                                "target" to
                                    buildJsonObject {
                                        put("handle", "entity.handle-42")
                                    },
                                "max-distance" to JsonPrimitive(4.5),
                            ),
                    ),
                )

        assertEquals("entity.attack", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("entity.handle-42", result.data["handle"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns machine readable entity attack failures for invalid arguments`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = smokeBackend(gateway)
        val attack = backend.runtimeGraph("alice").operations.single { it.id == "entity.attack" }
        val adapters = backend.operationAdapters("alice")

        fun invoke(arguments: Map<String, kotlinx.serialization.json.JsonElement>) =
            adapters.invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = attack,
                    arguments = arguments,
                ),
            )

        val invalidDistance =
            invoke(
                mapOf(
                    "target" to
                        buildJsonObject {
                            put("handle", "entity.handle-42")
                        },
                    "max-distance" to JsonPrimitive(-1.0),
                ),
            )
        val missingTarget = invoke(emptyMap())
        val invalidTarget =
            invoke(
                mapOf(
                    "target" to
                        buildJsonObject {
                            put("handle", "not-an-entity-handle")
                        },
                ),
            )

        assertEquals(DriverActionStatus.FAILED, invalidDistance.status)
        assertEquals("invalid-max-distance", invalidDistance.message)
        assertEquals(false, invalidDistance.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-max-distance", invalidDistance.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, missingTarget.status)
        assertEquals("missing-target", missingTarget.message)
        assertEquals(false, missingTarget.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("missing-target", missingTarget.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, invalidTarget.status)
        assertEquals("invalid-entity-handle", invalidTarget.message)
        assertEquals(false, invalidTarget.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-entity-handle", invalidTarget.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend does not advertise static placeholder gameplay actions`() {
        val backend = FabricDriverBackend.metadataOnly()

        val actionIds = backend.actions("alice").map { it.id }.toSet()

        assertTrue("player.chat" in actionIds)
        assertTrue("world.block.query" in actionIds)
        assertTrue("entity.attack" in actionIds)
        assertFalse("find.tree" in actionIds)
        assertFalse("mine.log" in actionIds)
        assertFalse("craft.sword" in actionIds)
        assertFalse("kill.cow" in actionIds)
    }

    @Test
    fun `fabric public actions are projected from runtime graph instead of binding descriptors`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val actions = backend.actions("alice")

        assertTrue(actions.any { it.id == "player.query" })
        assertTrue(actions.any { it.id == "inventory.equip" })
        assertTrue(actions.any { it.id == "world.block.break" })
        assertTrue(actions.all { it.source == DriverActionSource.RUNTIME_PROBE })
        assertEquals(DriverActionAvailability.AVAILABLE, actions.single { it.id == "player.query" }.availability)
        assertEquals(null, actions.single { it.id == "player.query" }.availabilityReason)
        assertEquals("integer", actions.single { it.id == "inventory.equip" }.arguments["slot"]?.type)
        assertEquals(true, actions.single { it.id == "inventory.equip" }.arguments["slot"]?.required)
        assertEquals(
            "object",
            actions
                .single { it.id == "player.query" }
                .result
                .properties["data"]
                ?.type,
        )
    }

    @Test
    fun `fabric execution adapters do not own public descriptors or schemas`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt",
                ),
            )
        val forbidden =
            listOf(
                "DriverActionDescriptor",
                "DriverActionArgument",
                "DriverActionResultDescriptor",
                "DriverActionResultProperty",
                "fabricPlayerQueryDescriptor",
                "fabricObjectDataResultDescriptor",
                "descriptor.id",
                "override val descriptor",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Private Fabric execution adapters must not own public descriptors or schemas; graph discovery owns those.",
        )
    }

    @Test
    fun `fabric operation adapter registration does not use binding descriptors`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )

        assertTrue(
            "binding.descriptor.id" !in source,
            "Fabric operation adapter registration must use private operation ids, not binding descriptors.",
        )
    }

    @Test
    fun `fabric backend does not own bootstrap adapter key literals`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val forbidden =
            listOf(
                "\"fabric.entity-query\"",
                "\"fabric.entity-attack\"",
                "\"fabric.world-block-query\"",
                "\"fabric.recipe-query\"",
                "\"fabric.recipe-craft\"",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Fabric backend adapter registration must use bootstrap adapter constants, not duplicate adapter-key literals.",
        )
    }

    @Test
    fun `fabric backend does not own bootstrap operation id guard literals`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val forbidden =
            listOf(
                "\"entity.query\"",
                "\"entity.attack\"",
                "\"world.block.query\"",
                "\"recipe.query\"",
                "\"recipe.craft\"",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Private Fabric backend adapter guards must reference bootstrap operation id constants.",
        )
    }

    @Test
    fun `fabric backend does not derive binding adapter keys from operation ids`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val forbidden =
            listOf(
                "fabricOperationAdapterKey",
                """replace(".", "-")""",
            )

        assertEquals(
            emptyList(),
            forbidden.filter { token -> source.contains(token) },
            "Private Fabric backend adapter registration must use bootstrap adapter definitions, not derive keys from operation ids.",
        )
    }

    @Test
    fun `fabric backend and smoke do not own navigation operation id literals`() {
        val root = repositoryRoot()
        val backendSource =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val smokeSource =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt",
                ),
            )

        assertEquals(
            emptyList(),
            listOf(
                "\"navigation.plan\"",
                "\"navigation.follow\"",
                "\"navigation.stop\"",
                "\"task.run\"",
                "\"task.status\"",
            ).filter { token -> backendSource.contains(token) },
            "Fabric backend dispatch must reference navigation operation constants instead of owning operation ids.",
        )
        assertEquals(
            emptyList(),
            listOf(
                "\"navigation.plan\"",
                "\"navigation.follow\"",
            ).filter { token -> smokeSource.contains(token) },
            "Fabric smoke required-action checks must reference navigation operation constants instead of owning operation ids.",
        )
    }

    @Test
    fun `fabric smoke controller does not own bootstrap action id literals`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt",
                ),
            )

        assertEquals(
            emptyList(),
            listOf(
                "\"player.chat\"",
                "\"player.move\"",
                "\"screen.query\"",
                "\"world.time.query\"",
                "\"player.query\"",
                "\"entity.query\"",
                "\"inventory.query\"",
                "\"inventory.equip\"",
                "\"player.look\"",
                "\"player.raycast\"",
                "\"world.block.break\"",
                "\"world.block.interact\"",
            ).filter { token -> source.contains(token) },
            "Fabric smoke controller must reference bootstrap operation constants instead of owning action ids.",
        )
    }

    @Test
    fun `completion gate does not accept unsupported version lanes as support`() {
        val checklist = Files.readString(repositoryRoot().resolve("docs/project-completion-checklist.md"))
        val finalGate =
            checklist
                .substringAfter("## Final Completion Gate")
                .replace(Regex("\\s+"), " ")

        assertFalse(
            finalGate.contains("accepted support boundary", ignoreCase = true),
            "Final completion must require runnable version support evidence, not an accepted unsupported boundary.",
        )
        assertFalse(
            Regex("unsupported lane.*completion", RegexOption.IGNORE_CASE).containsMatchIn(finalGate),
            "Unsupported compatibility lanes are diagnostics and must not satisfy completion.",
        )
        assertTrue(finalGate.contains("runnable support evidence", ignoreCase = true))
        assertTrue(finalGate.contains("latest", ignoreCase = true))
        assertTrue(finalGate.contains("representative older", ignoreCase = true))
    }

    @Test
    fun `fabric backend can expose unavailable actions only from runtime discovery probes`() {
        val backend = FabricDriverBackend.metadataOnly()

        val action = backend.actions("alice").single { it.id == "player.raycast" }
        val result = backend.invoke("alice", DriverActionInvocation("player.raycast"))

        assertEquals("player.raycast", action.id)
        assertEquals(DriverActionSource.RUNTIME_PROBE, action.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, action.availability)
        assertEquals("client-not-connected", action.availabilityReason)
        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("client-not-connected", result.message)
    }

    @Test
    fun `fabric backend dispatch does not depend on fabric action discovery`() {
        val source =
            Files.readString(
                repositoryRoot().resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )

        assertFalse(source.contains("FabricActionDiscovery"))
        assertFalse(source.contains("discoveredActions"))
    }

    @Test
    fun `fabric standalone action discovery layer is removed`() {
        val root = repositoryRoot()
        assertFalse(
            Files.exists(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionDiscovery.kt",
                ),
            ),
        )
        val sourceFiles =
            root
                .resolve("driver-fabric/src")
                .toFile()
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .toList()
        val staleNames =
            listOf(
                "FabricActionDiscovery",
                "FabricActionProbe",
                "FabricActionDiscoveryContext",
                "FabricDiscoveredAction",
                "defaultFabricActionDiscovery",
            )
        val offenders =
            sourceFiles
                .flatMap { file ->
                    val text = file.readText()
                    staleNames
                        .filter { staleName -> text.contains(staleName) }
                        .map { staleName -> root.relativize(file.toPath()).toString() to staleName }
                }.filterNot { (path, _) -> path.endsWith("FabricDriverModuleTest.kt") }

        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `fabric compatibility invoke dispatches unavailable operations from runtime graph`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val result = backend.invoke("alice", DriverActionInvocation("player.raycast"))

        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("client-not-connected", result.message)
        assertEquals(0, gateway.graphCapabilityProbeQueries)
        assertEquals(0, gateway.capabilityProbeQueries)
        assertEquals(emptyList(), gateway.actions)
    }

    @Test
    fun `fabric compatibility invoke adapters come from private binding map`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("hello graph invoke")),
                ),
            )

        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals("hello graph invoke", result.message)
        assertEquals(2, gateway.graphCapabilityProbeQueries)
        assertEquals(0, gateway.capabilityProbeQueries)
        assertEquals(listOf("client-action"), gateway.actions)
    }

    @Test
    fun `fabric runtime discovery probes client state before advertising unavailable raycast`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val raycast = backend.actions("alice").single { it.id == "player.raycast" }
        val result = backend.invoke("alice", DriverActionInvocation("player.raycast"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, raycast.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, raycast.availability)
        assertEquals("client-not-connected", raycast.availabilityReason)
        assertEquals("number", raycast.arguments["max-distance"]?.type)
        assertEquals("boolean", raycast.arguments["include-fluids"]?.type)
        assertEquals("object", raycast.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("client-not-connected", result.message)

        gateway.connected = true
        val connectedRaycast = backend.actions("alice").single { it.id == "player.raycast" }
        assertEquals(DriverActionSource.RUNTIME_PROBE, connectedRaycast.source)
        assertEquals(DriverActionAvailability.AVAILABLE, connectedRaycast.availability)
        assertEquals(null, connectedRaycast.availabilityReason)
        assertEquals("object", connectedRaycast.result.properties["data"]?.type)

        val connectedResult = backend.invoke("alice", DriverActionInvocation("player.raycast"))
        assertEquals(DriverActionStatus.ACCEPTED, connectedResult.status)
        assertEquals(true, connectedResult.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("block", connectedResult.data["target-kind"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fabric backend reports invalid raycast max distance as action failure`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.raycast",
                    arguments = mapOf("max-distance" to JsonPrimitive(0.0)),
                ),
            )

        assertEquals("player.raycast", result.action)
        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-max-distance", result.message)
        assertEquals(false, result.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-max-distance", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes player query only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailablePlayer = backend.actions("alice").single { it.id == "player.query" }
        val unavailableResult = backend.invoke("alice", DriverActionInvocation("player.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailablePlayer.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailablePlayer.availability)
        assertEquals("client-not-connected", unavailablePlayer.availabilityReason)
        assertEquals("object", unavailablePlayer.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put(
                    "position",
                    buildJsonObject {
                        put("x", 1.0)
                        put("y", 64.0)
                        put("z", -2.5)
                    },
                )
                put(
                    "rotation",
                    buildJsonObject {
                        put("yaw", 90.0)
                        put("pitch", 12.5)
                    },
                )
                put("selected-slot", 3)
            }

        val player = backend.actions("alice").single { it.id == "player.query" }
        val result = backend.invoke("alice", DriverActionInvocation("player.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, player.source)
        assertEquals(DriverActionAvailability.AVAILABLE, player.availability)
        assertEquals(null, player.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(3, result.data["selected-slot"]?.jsonPrimitive?.int)
        val position = requireNotNull(result.data["position"]?.jsonObject)
        assertEquals(1.0, position["x"]?.jsonPrimitive?.content?.toDoubleOrNull())
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes player look only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableLook = backend.actions("alice").single { it.id == "player.look" }
        val unavailableResult =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.look",
                    arguments =
                        mapOf(
                            "yaw" to JsonPrimitive(90.0),
                            "pitch" to JsonPrimitive(12.5),
                        ),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableLook.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableLook.availability)
        assertEquals("client-not-connected", unavailableLook.availabilityReason)
        assertEquals("number", unavailableLook.arguments["yaw"]?.type)
        assertEquals(true, unavailableLook.arguments["yaw"]?.required)
        assertEquals("number", unavailableLook.arguments["pitch"]?.type)
        assertEquals(true, unavailableLook.arguments["pitch"]?.required)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)

        gateway.connected = true
        val look = backend.actions("alice").single { it.id == "player.look" }
        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.look",
                    arguments =
                        mapOf(
                            "yaw" to JsonPrimitive(90.0),
                            "pitch" to JsonPrimitive(12.5),
                        ),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, look.source)
        assertEquals(DriverActionAvailability.AVAILABLE, look.availability)
        assertEquals(null, look.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("client-action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric player look returns machine readable failures before scheduling gateway`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val missingYaw =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.look",
                    arguments = mapOf("pitch" to JsonPrimitive(12.5)),
                ),
            )
        val invalidPitch =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.look",
                    arguments =
                        mapOf(
                            "yaw" to JsonPrimitive(90.0),
                            "pitch" to JsonPrimitive(120.0),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, missingYaw.status)
        assertEquals("missing-yaw", missingYaw.message)
        assertEquals(false, missingYaw.data["applied"]?.jsonPrimitive?.boolean)
        assertEquals("missing-yaw", missingYaw.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, invalidPitch.status)
        assertEquals("invalid-pitch", invalidPitch.message)
        assertEquals(false, invalidPitch.data["applied"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-pitch", invalidPitch.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes inventory query only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableInventory = backend.actions("alice").single { it.id == "inventory.query" }
        val unavailableResult = backend.invoke("alice", DriverActionInvocation("inventory.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableInventory.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableInventory.availability)
        assertEquals("client-not-connected", unavailableInventory.availabilityReason)
        assertEquals("object", unavailableInventory.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("selected-slot", 1)
                put("slot-count", 46)
                put(
                    "slots",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("slot", 1)
                                put("empty", false)
                                put("count", 1)
                                put("item-name", "Iron Sword")
                            },
                        )
                    },
                )
            }

        val inventory = backend.actions("alice").single { it.id == "inventory.query" }
        val result = backend.invoke("alice", DriverActionInvocation("inventory.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, inventory.source)
        assertEquals(DriverActionAvailability.AVAILABLE, inventory.availability)
        assertEquals(null, inventory.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(1, result.data["selected-slot"]?.jsonPrimitive?.int)
        assertEquals(46, result.data["slot-count"]?.jsonPrimitive?.int)
        val slot =
            requireNotNull(
                result.data["slots"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject,
            )
        assertEquals("Iron Sword", slot["item-name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fabric runtime discovery exposes inventory equip only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableEquip = backend.actions("alice").single { it.id == "inventory.equip" }
        val unavailableResult =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "inventory.equip",
                    arguments = mapOf("slot" to JsonPrimitive(2)),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableEquip.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableEquip.availability)
        assertEquals("client-not-connected", unavailableEquip.availabilityReason)
        assertEquals("integer", unavailableEquip.arguments["slot"]?.type)
        assertEquals(true, unavailableEquip.arguments["slot"]?.required)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)

        gateway.connected = true
        val equip = backend.actions("alice").single { it.id == "inventory.equip" }
        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "inventory.equip",
                    arguments = mapOf("slot" to JsonPrimitive(2)),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, equip.source)
        assertEquals(DriverActionAvailability.AVAILABLE, equip.availability)
        assertEquals(null, equip.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("client-action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric inventory equip returns machine readable failures before scheduling gateway`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val missingSlot =
            backend.invoke(
                "alice",
                DriverActionInvocation(action = "inventory.equip"),
            )
        val invalidSlot =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "inventory.equip",
                    arguments = mapOf("slot" to JsonPrimitive(9)),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, missingSlot.status)
        assertEquals("missing-slot", missingSlot.message)
        assertEquals(false, missingSlot.data["equipped"]?.jsonPrimitive?.boolean)
        assertEquals("missing-slot", missingSlot.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, invalidSlot.status)
        assertEquals("invalid-slot", invalidSlot.message)
        assertEquals(false, invalidSlot.data["equipped"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-slot", invalidSlot.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes block break only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableBreak = backend.actions("alice").single { it.id == "world.block.break" }
        val unavailableResult =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments = mapOf("max-distance" to JsonPrimitive(4.0)),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableBreak.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableBreak.availability)
        assertEquals("client-not-connected", unavailableBreak.availabilityReason)
        assertEquals("number", unavailableBreak.arguments["max-distance"]?.type)
        assertEquals("boolean", unavailableBreak.arguments["include-fluids"]?.type)
        assertEquals("object", unavailableBreak.arguments["target"]?.type)
        assertEquals("integer", unavailableBreak.arguments["ticks"]?.type)
        assertEquals("object", unavailableBreak.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
                put("started", true)
                put("block", "1 64 1")
            }

        val blockBreak = backend.actions("alice").single { it.id == "world.block.break" }
        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments = mapOf("max-distance" to JsonPrimitive(4.0)),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, blockBreak.source)
        assertEquals(DriverActionAvailability.AVAILABLE, blockBreak.availability)
        assertEquals(null, blockBreak.availabilityReason)
        assertEquals("object", blockBreak.arguments["target"]?.type)
        assertEquals("integer", blockBreak.arguments["ticks"]?.type)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("1 64 1", result.data["block"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric block break rejects malformed target handle`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put("handle", "minecraft:oak_log")
                                },
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-target-handle", result.message)
        assertEquals(false, result.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-target-handle", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric block break rejects incomplete target position`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put(
                                        "position",
                                        buildJsonObject {
                                            put("x", 1)
                                            put("y", 64)
                                        },
                                    )
                                },
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-target-position", result.message)
        assertEquals(false, result.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-target-position", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric block break rejects invalid scalar arguments with machine readable failures`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val invalidDistance =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments = mapOf("max-distance" to JsonPrimitive(0.0)),
                ),
            )
        val invalidTicks =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.break",
                    arguments = mapOf("ticks" to JsonPrimitive(0)),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, invalidDistance.status)
        assertEquals("invalid-max-distance", invalidDistance.message)
        assertEquals(false, invalidDistance.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-max-distance", invalidDistance.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, invalidTicks.status)
        assertEquals("invalid-ticks", invalidTicks.message)
        assertEquals(false, invalidTicks.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-ticks", invalidTicks.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime graph exposes block query from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val unavailableQuery = backend.runtimeGraph("alice").operations.single { it.id == "world.block.query" }
        val unavailableResult =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = unavailableQuery,
                    arguments = mapOf("radius" to JsonPrimitive(8.0)),
                ),
            )

        assertEquals("fabric.world-block-query", unavailableQuery.adapter)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, unavailableQuery.availability.state)
        assertEquals("client-not-connected", unavailableQuery.availability.reason)
        assertEquals("number", unavailableQuery.arguments["radius"]?.type)
        assertEquals("integer", unavailableQuery.arguments["limit"]?.type)
        assertEquals("string", unavailableQuery.arguments["category"]?.type)
        assertEquals("object", unavailableQuery.arguments["target"]?.type)
        assertEquals("object", unavailableQuery.result.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
            )
        gateway.screenOpen = false
        gateway.queryResult =
            buildJsonObject {
                put("origin", buildJsonObject { put("x", 1.0) })
                put("radius", 8.0)
                put("count", 1)
                put(
                    "blocks",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("handle", "block.handle-1")
                                put("category", "log")
                                put(
                                    "position",
                                    buildJsonObject {
                                        put("x", 2)
                                        put("y", 64)
                                        put("z", 3)
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val blockQuery = backend.runtimeGraph("alice").operations.single { it.id == "world.block.query" }
        gateway.actions.clear()
        gateway.scheduled = 0
        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = blockQuery,
                    arguments =
                        mapOf(
                            "radius" to JsonPrimitive(8.0),
                            "limit" to JsonPrimitive(5),
                            "category" to JsonPrimitive("log"),
                        ),
                ),
            )

        assertEquals(RuntimeAvailabilityState.AVAILABLE, blockQuery.availability.state)
        assertEquals(null, blockQuery.availability.reason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(1, result.data["count"]?.jsonPrimitive?.int)
        val block =
            requireNotNull(
                result.data["blocks"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject,
            )
        assertEquals(
            "block.handle-1",
            block["handle"]?.jsonPrimitive?.content,
        )
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns machine readable block query failures for invalid bounds`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
            )
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val blockQuery = backend.runtimeGraph("alice").operations.single { it.id == "world.block.query" }

        assertEquals("string", blockQuery.result.properties["reason"]?.type)

        val invalidRadius =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = blockQuery,
                    arguments = mapOf("radius" to JsonPrimitive(0.0)),
                ),
            )
        val invalidLimit =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = blockQuery,
                    arguments = mapOf("limit" to JsonPrimitive(0)),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, invalidRadius.status)
        assertEquals("invalid-radius", invalidRadius.message)
        assertEquals("invalid-radius", invalidRadius.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, invalidRadius.data["count"]?.jsonPrimitive?.int)
        assertEquals(0, invalidRadius.data["blocks"]?.jsonArray?.size)
        assertEquals(DriverActionStatus.FAILED, invalidLimit.status)
        assertEquals("invalid-limit", invalidLimit.message)
        assertEquals("invalid-limit", invalidLimit.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, invalidLimit.data["count"]?.jsonPrimitive?.int)
        assertEquals(0, invalidLimit.data["blocks"]?.jsonArray?.size)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric recipe projection emits opaque Craftless handles and public item labels`() {
        val recipe =
            craftlessRecipeRecord(
                CraftlessRecipeProjection(
                    handleIndex = 42,
                    kind = "shapeless-crafting",
                    outputs =
                        listOf(
                            craftlessRecipeItem(
                                rawName = "item.minecraft.wooden_sword",
                                translationKey = "item.minecraft.wooden_sword",
                            ),
                        ),
                    ingredients =
                        listOf(
                            craftlessRecipeItem("item.minecraft.oak_planks", "item.minecraft.oak_planks"),
                            craftlessRecipeItem("item.minecraft.stick", "item.minecraft.stick"),
                        ),
                    station = craftlessRecipeItem("item.minecraft.crafting_table", "item.minecraft.crafting_table"),
                ),
                craftable = true,
            )

        assertEquals("recipe.handle:42", recipe["handle"]?.jsonPrimitive?.content)
        assertEquals(true, recipe["craftable"]?.jsonPrimitive?.boolean)
        val output = recipe["outputs"]?.jsonArray?.single()?.jsonObject ?: error("missing recipe output")
        assertEquals("Wooden Sword", output["label"]?.jsonPrimitive?.content)
        assertEquals("weapon", output["category"]?.jsonPrimitive?.content)
        val ingredients = recipe["ingredients"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        assertEquals(listOf("material", "material"), ingredients.map { it["category"]?.jsonPrimitive?.content })
        val produces = recipe["produces"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val requires = recipe["requires"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        assertEquals(listOf("Wooden Sword"), produces.map { it["label"]?.jsonPrimitive?.content })
        assertEquals(listOf("material", "material"), requires.map { it["category"]?.jsonPrimitive?.content })
        val publicPayload = recipe.toString().lowercase()
        assertFalse(publicPayload.contains("minecraft"))
        assertFalse(publicPayload.contains("fabric"))
        assertFalse(publicPayload.contains("yarn"))
        assertFalse(publicPayload.contains("wooden_sword"))
    }

    @Test
    fun `fabric recipe projection explains non craftable recipes with machine readable reason`() {
        val recipe =
            craftlessRecipeRecord(
                CraftlessRecipeProjection(
                    handleIndex = 43,
                    kind = "shaped-crafting",
                    outputs =
                        listOf(
                            craftlessRecipeItem(
                                rawName = "item.minecraft.iron_pickaxe",
                                translationKey = "item.minecraft.iron_pickaxe",
                            ),
                        ),
                    ingredients =
                        listOf(
                            craftlessRecipeItem("item.minecraft.iron_ingot", "item.minecraft.iron_ingot"),
                            craftlessRecipeItem("item.minecraft.stick", "item.minecraft.stick"),
                        ),
                ),
                craftable = false,
            )

        assertEquals(false, recipe["craftable"]?.jsonPrimitive?.boolean)
        assertEquals("recipe-not-craftable", recipe["reason"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fabric runtime discovery keeps recipe operations unavailable until live recipe probes exist`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )

        val disconnectedGraph = backend.runtimeGraph("alice")
        val unavailableQuery = disconnectedGraph.operations.single { it.id == "recipe.query" }
        val unavailableCraft = disconnectedGraph.operations.single { it.id == "recipe.craft" }

        assertEquals("recipe", disconnectedGraph.resources.single { it.id == "recipe" }.id)
        assertEquals("recipe.handle", disconnectedGraph.handles.single { it.id == "recipe.handle" }.id)
        assertEquals("fabric.recipe-query", unavailableQuery.adapter)
        assertEquals("fabric.recipe-craft", unavailableCraft.adapter)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, unavailableQuery.availability.state)
        assertEquals("client-not-connected", unavailableQuery.availability.reason)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, unavailableCraft.availability.state)
        assertEquals("client-not-connected", unavailableCraft.availability.reason)

        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
            )

        val connectedGraph = backend.runtimeGraph("alice")
        val query = connectedGraph.operations.single { it.id == "recipe.query" }
        val craft = connectedGraph.operations.single { it.id == "recipe.craft" }

        val recipeResource = connectedGraph.resources.single { it.id == "recipe" }
        val recipeHandle = connectedGraph.handles.single { it.id == "recipe.handle" }

        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, recipeResource.availability.state)
        assertEquals(
            "recipe-discovery-unavailable",
            recipeResource.availability.reason,
        )
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, recipeHandle.availability.state)
        assertEquals("recipe-discovery-unavailable", recipeHandle.availability.reason)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, query.availability.state)
        assertEquals("recipe-discovery-unavailable", query.availability.reason)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, craft.availability.state)
        assertEquals("recipe-discovery-unavailable", craft.availability.reason)
        assertEquals("string", query.arguments["category"]?.type)
        assertEquals("string", query.arguments["output"]?.type)
        assertEquals("boolean", query.arguments["craftable"]?.type)
        assertEquals("integer", query.arguments["limit"]?.type)
        assertEquals("object", query.result.type)
        assertEquals("integer", query.result.properties["count"]?.type)
        assertEquals("string", query.result.properties["reason"]?.type)
        val recipesSchema = query.result.properties["recipes"]
        assertEquals("array", recipesSchema?.type)
        val recipeSchema = recipesSchema?.items
        assertEquals("object", recipeSchema?.type)
        assertEquals("string", recipeSchema?.properties?.get("handle")?.type)
        assertEquals("string", recipeSchema?.properties?.get("kind")?.type)
        assertEquals("boolean", recipeSchema?.properties?.get("craftable")?.type)
        assertEquals("array", recipeSchema?.properties?.get("outputs")?.type)
        assertEquals("array", recipeSchema?.properties?.get("ingredients")?.type)
        val requiresSchema = recipeSchema?.properties?.get("requires")
        val producesSchema = recipeSchema?.properties?.get("produces")
        assertEquals("array", requiresSchema?.type)
        assertEquals("object", requiresSchema?.items?.type)
        assertEquals("array", producesSchema?.type)
        assertEquals("object", producesSchema?.items?.type)
        assertEquals("object", recipeSchema?.properties?.get("station")?.type)
        assertEquals("string", recipeSchema?.properties?.get("reason")?.type)
        val craftTargetSchema = craft.arguments["target"]
        assertEquals("object", craftTargetSchema?.type)
        assertEquals(true, craftTargetSchema?.required)
        assertEquals("string", craftTargetSchema?.properties?.get("handle")?.type)
        assertEquals("integer", craft.arguments["count"]?.type)
        assertEquals("object", craft.result.type)
        assertEquals("string", craft.result.properties["handle"]?.type)
        assertEquals("boolean", craft.result.properties["accepted"]?.type)
        assertEquals("boolean", craft.result.properties["changed"]?.type)
        assertEquals("integer", craft.result.properties["requested-count"]?.type)
        assertEquals("integer", craft.result.properties["crafted-count"]?.type)
        assertEquals("string", craft.result.properties["inventory-before"]?.type)
        assertEquals("string", craft.result.properties["inventory-after"]?.type)
        assertEquals("integer", craft.result.properties["sync-id"]?.type)
        assertEquals("integer", craft.result.properties["output-slot"]?.type)
        assertEquals("integer", craft.result.properties["attempt"]?.type)
        assertEquals("integer", craft.result.properties["confirmation-attempt"]?.type)
        assertEquals("string", craft.result.properties["phase"]?.type)
        assertEquals("string", craft.result.properties["reason"]?.type)
        val expectedOutputSchema = craft.result.properties["expected-output"]
        val actualOutputSchema = craft.result.properties["actual-output"]
        assertEquals("object", expectedOutputSchema?.type)
        assertEquals("string", expectedOutputSchema?.properties?.get("label")?.type)
        assertEquals("object", actualOutputSchema?.type)
        assertEquals("integer", actualOutputSchema?.properties?.get("count")?.type)

        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
            )

        val recipeReadyGraph = backend.runtimeGraph("alice")
        val availableQuery = recipeReadyGraph.operations.single { it.id == "recipe.query" }
        val executionUnavailableCraft = recipeReadyGraph.operations.single { it.id == "recipe.craft" }
        val availableRecipeResource = recipeReadyGraph.resources.single { it.id == "recipe" }
        val availableRecipeHandle = recipeReadyGraph.handles.single { it.id == "recipe.handle" }

        assertEquals(RuntimeAvailabilityState.AVAILABLE, availableRecipeResource.availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, availableRecipeHandle.availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, availableQuery.availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, executionUnavailableCraft.availability.state)
        assertEquals("recipe-context-unavailable", executionUnavailableCraft.availability.reason)

        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
                recipeCrafting = true,
            )

        val craftingReadyGraph = backend.runtimeGraph("alice")
        val availableCraft = craftingReadyGraph.operations.single { it.id == "recipe.craft" }

        assertEquals(RuntimeAvailabilityState.AVAILABLE, availableCraft.availability.state)
    }

    @Test
    fun `fabric backend refuses recipe query until live recipe probe exists`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
            )
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeQuery = backend.runtimeGraph("alice").operations.single { it.id == "recipe.query" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeQuery,
                    arguments =
                        mapOf(
                            "category" to JsonPrimitive("tool"),
                            "craftable" to JsonPrimitive(true),
                            "limit" to JsonPrimitive(8),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("recipe-discovery-unavailable", result.message)
        assertEquals(emptyList<String>(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend queries live recipe projection when recipe probe exists`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
            )
        gateway.queryResult =
            buildJsonObject {
                put("count", 1)
                put(
                    "recipes",
                    buildJsonArray {
                        add(
                            craftlessRecipeRecord(
                                CraftlessRecipeProjection(
                                    handleIndex = 42,
                                    kind = "shapeless-crafting",
                                    outputs =
                                        listOf(
                                            craftlessRecipeItem(
                                                rawName = "item.minecraft.wooden_sword",
                                                translationKey = "item.minecraft.wooden_sword",
                                            ),
                                        ),
                                ),
                                craftable = true,
                            ),
                        )
                    },
                )
            }
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeQuery = backend.runtimeGraph("alice").operations.single { it.id == "recipe.query" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeQuery,
                    arguments =
                        mapOf(
                            "category" to JsonPrimitive("weapon"),
                            "craftable" to JsonPrimitive(true),
                            "limit" to JsonPrimitive(8),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        val queriedHandle =
            result.data
                .jsonObject
                .get("recipes")
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("handle")
                ?.jsonPrimitive
                ?.content
        assertEquals("recipe.handle:42", queriedHandle)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns machine readable recipe query failure for invalid limit`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
            )
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeQuery = backend.runtimeGraph("alice").operations.single { it.id == "recipe.query" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeQuery,
                    arguments = mapOf("limit" to JsonPrimitive(0)),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-limit", result.message)
        assertEquals("invalid-limit", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, result.data["count"]?.jsonPrimitive?.int)
        assertEquals(0, result.data["recipes"]?.jsonArray?.size)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend crafts a discovered recipe handle through runtime graph adapter`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
                recipeCrafting = true,
            )
        gateway.queryResult =
            buildJsonObject {
                put("handle", "recipe.handle:42")
                put("accepted", true)
                put("changed", false)
                put("crafted-count", 1)
                put("inventory-before", "inventory.fingerprint:before")
                put("inventory-after", "inventory.fingerprint:after")
                put("sync-id", 7)
            }
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeCraft = backend.runtimeGraph("alice").operations.single { it.id == "recipe.craft" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeCraft,
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put("handle", "recipe.handle:42")
                                },
                            "count" to JsonPrimitive(1),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        val craftData = result.data.jsonObject
        assertEquals("recipe.handle:42", craftData["handle"]?.jsonPrimitive?.content)
        assertEquals(true, craftData["accepted"]?.jsonPrimitive?.boolean)
        assertEquals(
            "inventory.fingerprint:before",
            craftData["inventory-before"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "inventory.fingerprint:after",
            craftData["inventory-after"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(listOf("client-query", "client-query"), gateway.actions)
        assertEquals(2, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns machine readable recipe craft failure for invalid count`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
                recipeCrafting = true,
            )
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeCraft = backend.runtimeGraph("alice").operations.single { it.id == "recipe.craft" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeCraft,
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put("handle", "recipe.handle:42")
                                },
                            "count" to JsonPrimitive(0),
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-count", result.message)
        val data = result.data.jsonObject
        assertEquals("recipe.handle:42", data["handle"]?.jsonPrimitive?.content)
        assertEquals(false, data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals(false, data["changed"]?.jsonPrimitive?.boolean)
        assertEquals(0, data["requested-count"]?.jsonPrimitive?.int)
        assertEquals(0, data["crafted-count"]?.jsonPrimitive?.int)
        assertEquals("target-validation-failed", data["phase"]?.jsonPrimitive?.content)
        assertEquals("invalid-count", data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend returns schema shaped recipe craft failure when target is missing`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
                recipeCrafting = true,
            )
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeCraft = backend.runtimeGraph("alice").operations.single { it.id == "recipe.craft" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeCraft,
                    arguments = mapOf("count" to JsonPrimitive(3)),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("missing-target", result.message)
        val data = result.data.jsonObject
        assertEquals(false, data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals(false, data["changed"]?.jsonPrimitive?.boolean)
        assertEquals(3, data["requested-count"]?.jsonPrimitive?.int)
        assertEquals(0, data["crafted-count"]?.jsonPrimitive?.int)
        assertEquals("target-validation-failed", data["phase"]?.jsonPrimitive?.content)
        assertEquals("missing-target", data["reason"]?.jsonPrimitive?.content)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend confirms recipe craft inventory after output take`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = true,
                inventory = true,
                camera = true,
                interactionManager = true,
                world = true,
                recipes = true,
                recipeCrafting = true,
            )
        gateway.queryResults +=
            buildJsonObject {
                put("handle", "recipe.handle:42")
                put("accepted", true)
                put("changed", false)
                put("requested-count", 1)
                put("crafted-count", 0)
                put("inventory-before", "inventory.fingerprint:before")
                put("inventory-after", "inventory.fingerprint:before")
                put("sync-id", 7)
                put("phase", "recipe-fill-requested")
            }
        gateway.queryResults +=
            buildJsonObject {
                put("handle", "recipe.handle:42")
                put("accepted", true)
                put("changed", false)
                put("requested-count", 1)
                put("crafted-count", 1)
                put("inventory-before", "inventory.fingerprint:before")
                put("inventory-after", "inventory.fingerprint:before")
                put("sync-id", 7)
                put("phase", "crafting-output-taken")
            }
        gateway.queryResults +=
            buildJsonObject {
                put("handle", "recipe.handle:42")
                put("accepted", true)
                put("changed", true)
                put("requested-count", 1)
                put("crafted-count", 1)
                put("inventory-before", "inventory.fingerprint:before")
                put("inventory-after", "inventory.fingerprint:after")
                put("sync-id", 7)
                put("phase", "crafting-inventory-confirmed")
                put("confirmation-attempt", 1)
            }
        val backend =
            FabricDriverBackend.real(
                gateway = gateway,
                runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
            )
        val recipeCraft = backend.runtimeGraph("alice").operations.single { it.id == "recipe.craft" }

        val result =
            backend.operationAdapters("alice").invoke(
                DriverOperationInvocation(
                    clientId = "alice",
                    operation = recipeCraft,
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put("handle", "recipe.handle:42")
                                },
                        ),
                ),
            )

        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        val craftData = result.data.jsonObject
        assertEquals("crafting-inventory-confirmed", craftData["phase"]?.jsonPrimitive?.content)
        assertEquals(true, craftData["changed"]?.jsonPrimitive?.boolean)
        assertEquals(
            "inventory.fingerprint:after",
            craftData["inventory-after"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(listOf("client-query", "client-query", "client-query"), gateway.actions)
        assertEquals(3, gateway.scheduled)
    }

    @Test
    fun `fabric recipe craft failures include requested count and phase`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val recipeCraftFailureSource =
            source
                .substringAfter("private fun recipeCraftFailure(")
                .substringBefore("private fun JsonObject.matchesCraftlessRecipeOutput(")

        assertTrue(recipeCraftFailureSource.contains("requestedCount: Int"))
        assertTrue(recipeCraftFailureSource.contains("phase: String"))
        assertTrue(recipeCraftFailureSource.contains("put(\"requested-count\", requestedCount)"))
        assertTrue(recipeCraftFailureSource.contains("put(\"phase\", phase)"))
    }

    @Test
    fun `fabric recipe craft execution takes generic crafting output after recipe fill`() {
        val root = repositoryRoot()
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
                ),
            )
        val craftRecipeSource =
            source
                .substringAfter("private fun craftRecipe(")
                .substringBefore("private fun queryEntities(")

        assertTrue(craftRecipeSource.contains("clickCraftlessRecipe("))
        assertTrue(craftRecipeSource.contains("craftlessOutputSlot()"))
        assertTrue(craftRecipeSource.contains("expectedOutput"))
        assertTrue(craftRecipeSource.contains("crafting-output-mismatch"))
        assertTrue(craftRecipeSource.contains("clickSlot("))
        assertTrue(craftRecipeSource.contains("SlotActionType.QUICK_MOVE"))
        assertTrue(craftRecipeSource.contains("val outputStackCount = outputSlot.stack.count"))
        assertTrue(craftRecipeSource.contains("put(\"crafted-count\", if (changed) outputStackCount else 0)"))
        val recipeCraftPendingSource =
            source
                .substringAfter("private fun recipeCraftPending(")
                .substringBefore("private fun JsonObject.withCraftingConfirmation(")
        assertTrue(
            recipeCraftPendingSource.contains("put(\"phase\""),
        )
        assertTrue(
            recipeCraftPendingSource.contains("requestedCount: Int"),
        )
        assertTrue(
            recipeCraftPendingSource.contains("put(\"requested-count\", requestedCount)"),
        )
        assertTrue(craftRecipeSource.indexOf("clickRecipe(") < craftRecipeSource.indexOf("clickSlot("))
    }

    @Test
    fun `fabric runtime discovery exposes block interact only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableInteract = backend.actions("alice").single { it.id == "world.block.interact" }
        val unavailableResult =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments = mapOf("max-distance" to JsonPrimitive(4.0)),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableInteract.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableInteract.availability)
        assertEquals("client-not-connected", unavailableInteract.availabilityReason)
        assertEquals("number", unavailableInteract.arguments["max-distance"]?.type)
        assertEquals("boolean", unavailableInteract.arguments["include-fluids"]?.type)
        assertEquals("object", unavailableInteract.arguments["target"]?.type)
        assertEquals("string", unavailableInteract.arguments["side"]?.type)
        assertEquals("object", unavailableInteract.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
                put("accepted", true)
                put("changed", true)
                put("block", "1 64 1")
                put("handle", "world.block:1:64:1")
                put("adjacent-handle", "world.block:1:65:1")
            }

        val blockInteract = backend.actions("alice").single { it.id == "world.block.interact" }
        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments =
                        mapOf(
                            "max-distance" to JsonPrimitive(4.0),
                            "side" to JsonPrimitive("up"),
                            "target" to
                                buildJsonObject {
                                    put("handle", "world.block:1:64:1")
                                },
                        ),
                ),
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, blockInteract.source)
        assertEquals(DriverActionAvailability.AVAILABLE, blockInteract.availability)
        assertEquals(null, blockInteract.availabilityReason)
        assertEquals("object", blockInteract.arguments["target"]?.type)
        assertEquals("string", blockInteract.arguments["side"]?.type)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals(true, result.data["changed"]?.jsonPrimitive?.boolean)
        assertEquals("1 64 1", result.data["block"]?.jsonPrimitive?.content)
        assertEquals("world.block:1:65:1", result.data["adjacent-handle"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric block interact rejects malformed target handle with machine readable failure`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments =
                        mapOf(
                            "target" to
                                buildJsonObject {
                                    put("handle", "minecraft:oak_log")
                                },
                        ),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("invalid-target-handle", result.message)
        assertEquals(false, result.data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-target-handle", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric block interact rejects invalid scalar arguments with machine readable failures`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val invalidDistance =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments = mapOf("max-distance" to JsonPrimitive(0.0)),
                ),
            )
        val invalidSide =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments = mapOf("side" to JsonPrimitive("diagonal")),
                ),
            )

        assertEquals(DriverActionStatus.FAILED, invalidDistance.status)
        assertEquals("invalid-max-distance", invalidDistance.message)
        assertEquals(false, invalidDistance.data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-max-distance", invalidDistance.data["reason"]?.jsonPrimitive?.content)
        assertEquals(DriverActionStatus.FAILED, invalidSide.status)
        assertEquals("invalid-side", invalidSide.message)
        assertEquals(false, invalidSide.data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals("invalid-side", invalidSide.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `targeted block interact hit position is centered on requested face`() {
        val position = BlockPos(10, 64, -4)

        val up = craftlessBlockFaceHitPosition(position, Direction.UP)
        val north = craftlessBlockFaceHitPosition(position, Direction.NORTH)

        assertEquals(10.5, up.x)
        assertEquals(65.0, up.y)
        assertEquals(-3.5, up.z)
        assertEquals(10.5, north.x)
        assertEquals(64.5, north.y)
        assertEquals(-4.0, north.z)
    }

    @Test
    fun `block query face metadata projects generic adjacent placement affordance`() {
        val face =
            CraftlessBlockQueryFace(
                side = Direction.NORTH,
                adjacentPosition = BlockPos(10, 64, -5),
                adjacentCategory = "air",
                replaceable = true,
                occupiedByPlayer = false,
            ).toCraftlessJson()

        assertEquals("north", face["side"]?.jsonPrimitive?.content)
        assertEquals("world.block:10:64:-5", face["adjacent-handle"]?.jsonPrimitive?.content)
        assertEquals("air", face["adjacent-category"]?.jsonPrimitive?.content)
        assertEquals(true, face["replaceable"]?.jsonPrimitive?.boolean)
        assertEquals(false, face["occupied-by-player"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `block interact acceptance includes item use fallback result`() {
        assertTrue(craftlessBlockInteractAccepted(ActionResult.PASS, ActionResult.SUCCESS))
        assertTrue(craftlessBlockInteractAccepted(ActionResult.SUCCESS, ActionResult.PASS))
        assertFalse(craftlessBlockInteractAccepted(ActionResult.PASS, ActionResult.PASS))
    }

    @Test
    fun `fabric runtime discovery exposes world time query only from client state`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = false
        val backend = FabricDriverBackend.real(gateway)

        val unavailableTime = backend.actions("alice").single { it.id == "world.time.query" }
        val unavailableResult = backend.invoke("alice", DriverActionInvocation("world.time.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, unavailableTime.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, unavailableTime.availability)
        assertEquals("client-not-connected", unavailableTime.availabilityReason)
        assertEquals("object", unavailableTime.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("time", 1234)
                put("time-of-day", 5678)
            }

        val worldTime = backend.actions("alice").single { it.id == "world.time.query" }
        val result = backend.invoke("alice", DriverActionInvocation("world.time.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, worldTime.source)
        assertEquals(DriverActionAvailability.AVAILABLE, worldTime.availability)
        assertEquals(null, worldTime.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(1234, result.data["time"]?.jsonPrimitive?.int)
        assertEquals(5678, result.data["time-of-day"]?.jsonPrimitive?.int)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery downgrades connected actions when concrete capabilities are missing`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.capabilities =
            FabricClientCapabilitySnapshot(
                connected = true,
                player = false,
                inventory = false,
                camera = true,
                interactionManager = false,
                world = true,
            )
        val backend = FabricDriverBackend.real(gateway)

        val actions = backend.actions("alice").associateBy { it.id }

        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("player.query").availability)
        assertEquals("player-unavailable", actions.getValue("player.query").availabilityReason)
        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("player.look").availability)
        assertEquals("player-unavailable", actions.getValue("player.look").availabilityReason)
        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("inventory.query").availability)
        assertEquals("inventory-unavailable", actions.getValue("inventory.query").availabilityReason)
        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("inventory.equip").availability)
        assertEquals("inventory-unavailable", actions.getValue("inventory.equip").availabilityReason)
        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("world.block.break").availability)
        assertEquals("interaction-unavailable", actions.getValue("world.block.break").availabilityReason)
        assertEquals(DriverActionAvailability.UNAVAILABLE, actions.getValue("world.block.interact").availability)
        assertEquals("player-unavailable", actions.getValue("world.block.interact").availabilityReason)
        assertEquals(DriverActionAvailability.AVAILABLE, actions.getValue("player.raycast").availability)
        assertEquals(DriverActionAvailability.AVAILABLE, actions.getValue("world.time.query").availability)

        val result = backend.invoke("alice", DriverActionInvocation("world.block.break"))

        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("interaction-unavailable", result.message)
        assertEquals(0, gateway.capabilityProbeQueries)
        assertEquals(4, gateway.graphCapabilityProbeQueries)
    }

    @Test
    fun `fabric runtime discovery exposes screen query only from live client state`() {
        val metadataOnly = FabricDriverBackend.metadataOnly()
        assertEquals(DriverActionAvailability.AVAILABLE, metadataOnly.actions("alice").single { it.id == "screen.query" }.availability)

        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)
        gateway.queryResult =
            buildJsonObject {
                put("open", true)
                put("title", "Inventory")
            }

        val screen = backend.actions("alice").single { it.id == "screen.query" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.query"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, screen.source)
        assertEquals(DriverActionAvailability.AVAILABLE, screen.availability)
        assertEquals(null, screen.availabilityReason)
        assertEquals("object", screen.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["open"]?.jsonPrimitive?.boolean)
        assertEquals("Inventory", result.data["title"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes unavailable screen close when no screen is open`() {
        val metadataOnly = FabricDriverBackend.metadataOnly()
        val metadataOnlyClose = metadataOnly.actions("alice").single { it.id == "screen.close" }
        assertEquals(DriverActionAvailability.UNAVAILABLE, metadataOnlyClose.availability)
        assertEquals("screen-not-open", metadataOnlyClose.availabilityReason)

        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.screenOpen = false
        val backend = FabricDriverBackend.real(gateway)

        val close = backend.actions("alice").single { it.id == "screen.close" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.close"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, close.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, close.availability)
        assertEquals("screen-not-open", close.availabilityReason)
        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("screen-not-open", result.message)
        assertEquals(0, gateway.screenProbeQueries)
        assertEquals(4, gateway.graphCapabilityProbeQueries)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes screen close only when a screen is open`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        gateway.screenOpen = true
        val backend = FabricDriverBackend.real(gateway)

        val close = backend.actions("alice").single { it.id == "screen.close" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.close"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, close.source)
        assertEquals(DriverActionAvailability.AVAILABLE, close.availability)
        assertEquals(null, close.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(0, gateway.screenProbeQueries)
        assertEquals(4, gateway.graphCapabilityProbeQueries)
        assertEquals(listOf("client-action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric backend reports missing player chat message as action failure`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = emptyMap(),
                ),
            )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("missing-message", result.message)
        assertEquals(false, result.data["sent"]?.jsonPrimitive?.boolean)
        assertEquals("missing-message", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend reports blank player chat message as action failure`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("  ")),
                ),
            )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("blank-message", result.message)
        assertEquals(false, result.data["sent"]?.jsonPrimitive?.boolean)
        assertEquals("blank-message", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend rejects raw minecraft command strings as chat action input`() {
        val gateway = RecordingFabricClientGateway()
        gateway.connected = true
        val backend = FabricDriverBackend.real(gateway)

        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("/server lobby")),
                ),
            )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.FAILED, result.status)
        assertEquals("minecraft-command-rejected", result.message)
        assertEquals(false, result.data["sent"]?.jsonPrimitive?.boolean)
        assertEquals("minecraft-command-rejected", result.data["reason"]?.jsonPrimitive?.content)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric smoke controller is inert without opt in`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)
        val controller = FabricClientSmokeController.fromEnvironment(emptyMap())

        assertFalse(controller.enabled)
        assertFalse(controller.start(backend, gateway))
        assertEquals(emptyList(), gateway.actions)
    }

    @Test
    fun `fabric smoke controller can hold the final gameplay session open`() {
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_FINAL_GAMEPLAY" to "1",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "720000",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "60000",
                ),
            )

        assertEquals(60_000.milliseconds, controller.holdAfterActions)
        assertEquals(720_000.milliseconds, controller.actionTimeout)
        assertEquals(null, controller.confirmationChatContains)
        assertEquals(0.milliseconds, controller.readyNotificationReminder)
        assertFalse(controller.toString().contains("runSurvivalTask"))
    }

    @Test
    fun `fabric smoke controller parses process external public agent command`() {
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["mise","exec","--","gradle",":testkit:publicAgentGameplay"]""",
                ),
            )

        assertEquals(
            listOf("mise", "exec", "--", "gradle", ":testkit:publicAgentGameplay"),
            controller.publicAgentCommand,
        )
    }

    @Test
    fun `fabric smoke child commands do not inherit server owner environment`() {
        val env =
            mutableMapOf(
                "PATH" to "/usr/bin",
                "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                "CRAFTLESS_FINAL_GAMEPLAY" to "1",
                "CRAFTLESS_LOCAL_SERVER_SMOKE" to "1",
                "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT" to "/tmp/craftless-server",
                "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON" to """["gradle",":driver-fabric:runClient"]""",
                "CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE" to "hello from Craftless final gameplay",
                "CRAFTLESS_SMOKE_PROVISION_ITEM_ID" to "minecraft:iron_sword",
            )

        env.removeInheritedSmokeOwnerEnvironment()

        assertEquals("/usr/bin", env["PATH"])
        assertFalse("CRAFTLESS_FABRIC_CLIENT_SMOKE" in env)
        assertFalse("CRAFTLESS_FINAL_GAMEPLAY" in env)
        assertFalse("CRAFTLESS_LOCAL_SERVER_SMOKE" in env)
        assertFalse("CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT" in env)
        assertFalse("CRAFTLESS_SMOKE_ACTION_COMMAND_JSON" in env)
        assertFalse("CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE" in env)
        assertFalse("CRAFTLESS_SMOKE_PROVISION_ITEM_ID" in env)
    }

    @Test
    fun `fabric smoke controller runs process external public agent command with live daemon url`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-public-agent-command")
        val envOutput = artifactsDir.resolve("public-agent-env.txt")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "120000",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to
                        """["/bin/sh","-c","printf '%s\n%s\n%s\n%s\n' \"${'$'}CRAFTLESS_PUBLIC_AGENT_BASE_URL\" \"${'$'}CRAFTLESS_PUBLIC_AGENT_CLIENT_ID\" \"${'$'}CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR\" \"${'$'}CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS\" > '$envOutput'"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        val envLines = Files.readAllLines(envOutput)
        assertTrue(envLines[0].startsWith("http://127.0.0.1:"))
        assertEquals("fabric-smoke", envLines[1])
        assertEquals(artifactsDir.toString(), envLines[2])
        assertEquals("120000", envLines[3])
        assertTrue(Files.exists(artifactsDir.resolve("public-agent-command.log")))
    }

    @Test
    fun `fabric smoke controller does not enter ready hold when public agent reports blocked`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-public-agent-blocked")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "1",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to
                        """["/bin/sh","-c","printf '%s\n' '{\"event\":\"public-agent-blocked\",\"clientId\":\"fabric-smoke\",\"blocker\":\"insufficient-public-evidence:navigation.follow.succeeded\"}' >> \"${'$'}CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR/public-agent-gameplay-results.jsonl\""]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        val blockedArtifact = readArtifact(artifactsDir, "public-agent-blocked.json")
        assertTrue(blockedArtifact.contains("\"event\":\"public-agent-blocked\""))
        assertTrue(blockedArtifact.contains("insufficient-public-evidence:navigation.follow.succeeded"))
        Thread.sleep(50)
        assertFalse(Files.exists(artifactsDir.resolve("final-gameplay-ready.json")))
        assertFalse(Files.exists(artifactsDir.resolve("final-gameplay-confirmation-timeout.json")))
    }

    @Test
    fun `fabric smoke controller runs ready notification command with live session context`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-ready-notification")
        val readyOutput = artifactsDir.resolve("ready-env.txt")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "1",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["/bin/sh","-c","printf public-agent-ready > /dev/null"]""",
                    "CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON" to
                        """["/bin/sh","-c","printf '%s\n%s\n%s\n%s\n%s\n%s\n' \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_BASE_URL\" \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_CLIENT_ID\" \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_SERVER_HOST\" \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_SERVER_PORT\" \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_ARTIFACTS_DIR\" \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_HOLD_MS\" > '$readyOutput'"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        val readyLines = Files.readAllLines(readyOutput)
        assertTrue(readyLines[0].startsWith("http://127.0.0.1:"))
        assertEquals("fabric-smoke", readyLines[1])
        assertEquals("localhost", readyLines[2])
        assertEquals("25567", readyLines[3])
        assertEquals(artifactsDir.toString(), readyLines[4])
        assertEquals("1", readyLines[5])
        val readyArtifact = readArtifact(artifactsDir, "final-gameplay-ready.json")
        assertTrue(readyArtifact.contains("\"event\":\"final-gameplay-ready\""))
        assertTrue(readyArtifact.contains("\"server\":\"localhost:25567\""))
        assertTrue(readyArtifact.contains("\"confirmation-contains\":\"goal may be completed\""))
        val joinInstructions = readArtifact(artifactsDir, "final-gameplay-join-instructions.txt")
        assertTrue(joinInstructions.contains("Server: localhost:25567"))
        assertTrue(joinInstructions.contains("Confirmation phrase: goal may be completed"))
        assertTrue(joinInstructions.contains("Client id: fabric-smoke"))
        assertTrue(Files.exists(artifactsDir.resolve("final-gameplay-ready-command.log")))
    }

    @Test
    fun `fabric smoke controller stops final session after configured chat confirmation evidence`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-ready-confirmation")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "5000",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["/bin/sh","-c","printf public-agent-ready > /dev/null"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        readArtifact(artifactsDir, "final-gameplay-ready.json")
        Files.writeString(
            artifactsDir.resolve("server-evidence.jsonl"),
            """{"type":"CHAT","player":"Robin","message":"goal may be completed"}""" + "\n",
        )
        gateway.awaitAction("stop")

        assertTrue("stop" in gateway.actionSnapshot())
        val confirmationArtifact = readArtifact(artifactsDir, "final-gameplay-confirmation.json")
        assertTrue(confirmationArtifact.contains("\"event\":\"final-gameplay-confirmed\""))
        assertTrue(confirmationArtifact.contains("\"player\":\"Robin\""))
        assertTrue(confirmationArtifact.contains("\"message\":\"goal may be completed\""))
    }

    @Test
    fun `fabric smoke controller repeats ready notification during confirmation hold`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-ready-reminder")
        val readyOutput = artifactsDir.resolve("ready-reminders.txt")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "120",
                    "CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS" to "20",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["/bin/sh","-c","printf public-agent-ready > /dev/null"]""",
                    "CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON" to
                        """["/bin/sh","-c","printf '%s\n' \"${'$'}CRAFTLESS_FABRIC_SMOKE_READY_SERVER_PORT\" >> '$readyOutput'"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")

        val reminderLines = Files.readAllLines(readyOutput)
        assertTrue(reminderLines.size >= 2, "expected repeated ready notifications, got $reminderLines")
        assertTrue(reminderLines.all { it == "25567" })
        assertFalse(Files.exists(artifactsDir.resolve("final-gameplay-confirmation.json")))
    }

    @Test
    fun `fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-confirmation-timeout")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "25",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["/bin/sh","-c","printf public-agent-ready > /dev/null"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        assertFalse(Files.exists(artifactsDir.resolve("final-gameplay-confirmation.json")))
        val timeoutArtifact = readArtifact(artifactsDir, "final-gameplay-confirmation-timeout.json")
        assertTrue(timeoutArtifact.contains("\"event\":\"final-gameplay-confirmation-timeout\""))
        assertTrue(timeoutArtifact.contains("\"server\":\"localhost:25567\""))
        assertTrue(timeoutArtifact.contains("\"confirmation-contains\":\"goal may be completed\""))
        assertTrue(timeoutArtifact.contains("\"hold-ms\":\"25\""))
    }

    @Test
    fun `fabric smoke controller extends final gameplay hold on chat activity`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-activity-hold")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "40",
                    "CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS" to "150",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                    "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
                    "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON" to """["/bin/sh","-c","printf public-agent-ready > /dev/null"]""",
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        readArtifact(artifactsDir, "final-gameplay-ready.json")
        Files.writeString(
            artifactsDir.resolve("server-evidence.jsonl"),
            """{"type":"CHAT","player":"Robin","message":"let us keep playing first"}""" + "\n",
        )
        Thread.sleep(80)

        assertFalse("stop" in gateway.actionSnapshot(), "controller stopped at original hold deadline")
        gateway.awaitAction("stop")
        val activityArtifact = readArtifact(artifactsDir, "final-gameplay-activity.jsonl")
        assertTrue(activityArtifact.contains("\"event\":\"final-gameplay-activity-extended\""))
        assertTrue(activityArtifact.contains("\"player\":\"Robin\""))
        val timeoutArtifact = readArtifact(artifactsDir, "final-gameplay-confirmation-timeout.json")
        assertTrue(timeoutArtifact.contains("\"activity-extends-hold-ms\":\"150\""))
        assertFalse(Files.exists(artifactsDir.resolve("final-gameplay-confirmation.json")))
    }

    @Test
    fun `fabric smoke controller invokes generated chat and movement through daemon api and writes artifacts`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-smoke-artifacts")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_HOST" to "localhost",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE" to "hello from fabric smoke",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                ),
            )
        enqueueBasicSmokeQueryResults(gateway)

        assertEquals(0.milliseconds, controller.startupSettleDelay)
        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        val clientOpenApi = readArtifact(artifactsDir, "client-openapi.json")
        assertTrue(clientOpenApi.contains("/clients/fabric-smoke:run"))
        assertTrue(clientOpenApi.contains("craftless-driver-fabric"))
        val clientActions = readArtifact(artifactsDir, "client-actions.json")
        assertTrue(clientActions.contains("player.chat"))
        assertTrue(clientActions.contains("player.move"))
        assertTrue(readArtifact(artifactsDir, "client-resources.json").contains("\"id\":\"player\""))
        val connectedOpenApi = readArtifact(artifactsDir, "client-openapi-connected.json")
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/player:query"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/player:look"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/entity:query"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/screen:query"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/block:break"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/block:interact"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/time:query"))
        val connectedActions = readArtifact(artifactsDir, "client-actions-connected.json")
        assertTrue(connectedActions.contains("player.query"))
        assertTrue(connectedActions.contains("player.look"))
        assertTrue(connectedActions.contains("entity.query"))
        assertTrue(connectedActions.contains("inventory.query"))
        assertTrue(connectedActions.contains("inventory.equip"))
        assertTrue(connectedActions.contains("screen.query"))
        assertTrue(connectedActions.contains("world.block.break"))
        assertTrue(connectedActions.contains("world.block.interact"))
        assertTrue(connectedActions.contains("world.time.query"))
        assertTrue(connectedActions.contains("\"availability\":\"available\""))
        val connectedResources = readArtifact(artifactsDir, "client-resources-connected.json")
        assertTrue(connectedResources.contains("\"id\":\"player\""))
        assertTrue(connectedResources.contains("\"id\":\"entity\""))
        assertTrue(connectedResources.contains("\"id\":\"inventory\""))
        assertTrue(connectedResources.contains("\"id\":\"screen\""))
        assertTrue(connectedResources.contains("\"id\":\"world.block\""))
        assertTrue(connectedResources.contains("\"id\":\"world.time\""))
        assertTrue(connectedResources.contains("\"availability\":\"available\""))
        val gameplayResults = readArtifact(artifactsDir, "gameplay-results.jsonl")
        assertTrue(gameplayResults.contains("player.query"))
        assertTrue(gameplayResults.contains("position-before"))
        assertTrue(gameplayResults.contains("screen.query"))
        assertTrue(gameplayResults.contains("world.time.query"))
        assertTrue(gameplayResults.contains("player.look"))
        assertTrue(gameplayResults.contains("entity.query"))
        assertTrue(gameplayResults.contains("entity.handle-42"))
        assertTrue(gameplayResults.contains("inventory.query"))
        assertTrue(gameplayResults.contains("inventory.equip"))
        assertTrue(gameplayResults.contains("slot 1"))
        assertTrue(gameplayResults.contains("Iron Sword"))
        assertTrue(gameplayResults.contains("world.block.break"))
        assertTrue(gameplayResults.contains("world.block.interact"))
        val publicAgentGameplay = readArtifact(artifactsDir, "public-agent-gameplay-results.jsonl")
        assertTrue(publicAgentGameplay.contains("entity.query"))
        assertTrue(publicAgentGameplay.contains("world.block.break"))
        assertFalse(publicAgentGameplay.contains("task.survival"))
        val publicAgentState = readArtifact(artifactsDir, "public-agent-state.jsonl")
        assertTrue(publicAgentState.contains("public-agent-discovery"))
        assertTrue(publicAgentState.contains("missing-generic-primitive"))
        assertTrue(readArtifact(artifactsDir, "runtime-metadata.json").contains("craftless-driver-fabric"))
        val clientEvents = readArtifact(artifactsDir, "client-events.jsonl")
        assertTrue(clientEvents.contains("hello from fabric smoke"))
        assertTrue(clientEvents.contains("player.move"))
        val eventStream = readArtifact(artifactsDir, "client-events-stream.sse")
        assertTrue(eventStream.contains("event: player.chat"))
        assertTrue(eventStream.contains("event: player.move"))
        assertTrue(eventStream.contains("data:"))
    }

    @Test
    fun `fabric smoke controller waits for target inventory item before equip`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-smoke-target-item")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM" to "1",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                ),
            )
        gateway.queryResults +=
            buildJsonObject {
                put("ticks", 4)
                put(
                    "position-before",
                    buildJsonObject {
                        put("x", 0.0)
                        put("y", 64.0)
                        put("z", 0.0)
                    },
                )
            }
        gateway.queryResults +=
            buildJsonObject {
                put("open", false)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("time", 1234)
                put("time-of-day", 5678)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("selected-slot", 0)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("origin", buildJsonObject { put("x", 0.0) })
                put("radius", 16.0)
                put("count", 0)
                put("entities", buildJsonArray {})
            }
        gateway.queryResults +=
            buildJsonObject {
                put("selected-slot", 0)
                put("slot-count", 46)
                put("slots", buildJsonArray {})
            }
        gateway.queryResults +=
            buildJsonObject {
                put("selected-slot", 0)
                put("slot-count", 46)
                put(
                    "slots",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("slot", 2)
                                put("empty", false)
                                put("count", 1)
                                put("item-name", "Iron Sword")
                            },
                        )
                    },
                )
            }
        gateway.queryResults +=
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
            }
        gateway.queryResults +=
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
                put("accepted", true)
            }

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        val gameplay = readArtifact(artifactsDir, "gameplay-results.jsonl")
        assertTrue(gameplay.contains("craftless-smoke-target-item-observed"))
        assertTrue(gameplay.contains("slot 2"))
        assertTrue(gameplay.contains("Iron Sword"))
    }

    @Test
    fun `fabric smoke controller does not claim missing target item was selected`() {
        val gateway = RecordingFabricClientGateway()
        val backend = smokeBackend(gateway)
        val artifactsDir = Files.createTempDirectory("craftless-fabric-smoke-missing-target-item")
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                    "CRAFTLESS_SMOKE_ARTIFACTS_DIR" to artifactsDir.toString(),
                ),
            )
        gateway.queryResults +=
            buildJsonObject {
                put("ticks", 4)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("open", false)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("time", 1234)
                put("time-of-day", 5678)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("selected-slot", 0)
            }
        gateway.queryResults +=
            buildJsonObject {
                put("origin", buildJsonObject { put("x", 0.0) })
                put("radius", 16.0)
                put("count", 0)
                put("entities", buildJsonArray {})
            }
        gateway.queryResults +=
            buildJsonObject {
                put("selected-slot", 0)
                put("slot-count", 46)
                put("slots", buildJsonArray {})
            }
        gateway.queryResults +=
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
            }
        gateway.queryResults +=
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
                put("accepted", true)
            }

        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitAction("stop")
        val gameplay = readArtifact(artifactsDir, "gameplay-results.jsonl")
        assertFalse(gameplay.contains("selected slot 0 for Iron Sword"))
        assertTrue(gameplay.contains("craftless-smoke-inventory-fallback"))
        assertTrue(gameplay.contains("target item Iron Sword was not observed"))
    }

    @Test
    fun `fabric smoke action availability comes from live openapi metadata`() {
        val openApi =
            """
            {
              "openapi": "3.1.0",
              "info": { "title": "Craftless smoke API", "version": "1" },
              "paths": {},
              "x-craftless": {},
              "x-craftless-actions": [
                {
                  "id": "player.chat",
                  "schemaVersion": "1",
                  "args": {},
                  "availability": "available"
                }
              ]
            }
            """.trimIndent()

        openApi.requireAvailableSmokeAction("player.chat")
        val error =
            kotlin.test.assertFailsWith<IllegalStateException> {
                openApi.requireAvailableSmokeAction("player.fly")
            }
        assertEquals("fabric smoke action player.fly is not available in connected client OpenAPI", error.message)
    }

    @Test
    fun `fabric smoke resource artifacts come from live openapi metadata`() {
        val openApi =
            """
            {
              "openapi": "3.1.0",
              "info": { "title": "Craftless smoke API", "version": "1" },
              "paths": {},
              "x-craftless": {},
              "x-craftless-actions": [],
              "x-craftless-resources": [
                {
                  "id": "inventory",
                  "actions": ["inventory.query"],
                  "availability": "available",
                  "availabilityReasons": [],
                  "actionDescriptors": [
                    {
                      "id": "inventory.query",
                      "schemaVersion": "1",
                      "args": {},
                      "availability": "available"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val resources = openApi.smokeResourceArtifactFromOpenApi()

        assertTrue(resources.contains("\"id\":\"inventory\""))
        assertTrue(resources.contains("\"availability\":\"available\""))
    }

    @Test
    fun `fabric smoke readiness gate waits for client readiness before connecting`() {
        val gateway = RecordingFabricClientGateway()
        gateway.ready = false

        assertFalse(gateway.awaitReadyToConnect(timeout = 20.milliseconds, pollInterval = 5.milliseconds))
        assertEquals(emptyList(), gateway.actions)

        gateway.ready = true
        assertTrue(gateway.awaitReadyToConnect(timeout = 20.milliseconds, pollInterval = 5.milliseconds))
        assertEquals(emptyList(), gateway.actions)
    }

    private fun resourceJson(path: String) =
        json
            .parseToJsonElement(
                requireNotNull(javaClass.classLoader.getResource(path)) { "missing resource $path" }.readText(),
            ).jsonObject

    private fun smokeBackend(gateway: RecordingFabricClientGateway): FabricDriverBackend =
        FabricDriverBackend.real(
            gateway = gateway,
            runtimeMetadataProvider =
                FabricRuntimeMetadataProvider {
                    DriverRuntimeMetadata(
                        loaderVersion = "test-loader",
                        driver = "craftless-driver-fabric",
                        driverVersion = "0.1.0-SNAPSHOT",
                        mappings = "craftless-fabric-bindings",
                        installedModsFingerprint = "mods:test-runtime",
                        registryFingerprint = "registries:test-runtime",
                        serverFeatureFingerprint = "server-features:test-runtime",
                        permissionsFingerprint = "permissions:local-client",
                    )
                },
        )
}

private fun enqueueBasicSmokeQueryResults(gateway: RecordingFabricClientGateway) {
    gateway.queryResults +=
        buildJsonObject {
            put("ticks", 4)
            put(
                "position-before",
                buildJsonObject {
                    put("x", 0.0)
                    put("y", 64.0)
                    put("z", 0.0)
                },
            )
        }
    gateway.queryResults +=
        buildJsonObject {
            put("open", false)
        }
    gateway.queryResults +=
        buildJsonObject {
            put("time", 1234)
            put("time-of-day", 5678)
        }
    gateway.queryResults +=
        buildJsonObject {
            put(
                "position",
                buildJsonObject {
                    put("x", 0.0)
                    put("y", 64.0)
                    put("z", 0.0)
                },
            )
            put("selected-slot", 0)
        }
    gateway.queryResults +=
        buildJsonObject {
            put("origin", buildJsonObject { put("x", 0.0) })
            put("radius", 16.0)
            put("count", 1)
            put(
                "entities",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("handle", "entity.handle-42")
                            put("label", "Cow")
                            put("category", "passive")
                        },
                    )
                },
            )
        }
    gateway.queryResults +=
        buildJsonObject {
            put("selected-slot", 0)
            put("slot-count", 46)
            put(
                "slots",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("slot", 1)
                            put("empty", false)
                            put("count", 1)
                            put("item-name", "Iron Sword")
                        },
                    )
                },
            )
        }
    gateway.queryResults +=
        buildJsonObject {
            put("hit", true)
            put("target-kind", "block")
        }
    gateway.queryResults +=
        buildJsonObject {
            put("hit", true)
            put("target-kind", "block")
            put("accepted", true)
        }
}

private class RecordingFabricClientGateway : FabricClientGateway {
    var scheduled = 0
    val actions: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val queryResults = ArrayDeque<Any>()
    var queryResult: Any =
        buildJsonObject {
            put("hit", true)
            put("target-kind", "block")
        }
    var screenOpen = false
    var screenProbeQueries = 0
    var capabilities =
        FabricClientCapabilitySnapshot(
            connected = true,
            player = true,
            inventory = true,
            camera = true,
            interactionManager = true,
            world = true,
        )
    var capabilityProbeQueries = 0
    var graphCapabilityProbeQueries = 0

    @Volatile
    var connected = false

    var connectMarksConnected = true

    @Volatile
    var ready = true

    override fun execute(action: () -> Unit) {
        scheduled += 1
        action()
    }

    override fun connect(target: ConnectionTarget) {
        actions += "connect ${target.host}:${target.port}"
        if (connectMarksConnected) {
            connected = true
        }
    }

    override fun executeOnClient(action: net.minecraft.client.MinecraftClient.() -> Unit) {
        scheduled += 1
        actions += "client-action"
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> queryOnClient(query: net.minecraft.client.MinecraftClient.() -> T): T {
        if (isCapabilityGraphProbe()) {
            graphCapabilityProbeQueries += 1
            return if (graphCapabilityProbeQueries % 2 == 1) {
                capabilities as T
            } else {
                screenOpen as T
            }
        }
        if (isCapabilityDiscoveryProbe()) {
            capabilityProbeQueries += 1
            return capabilities as T
        }
        if (isScreenCloseDiscoveryProbe()) {
            screenProbeQueries += 1
            return screenOpen as T
        }
        if (isServerFeatureMetadataProbe()) {
            val queued = queryResults.firstOrNull()
            if (queued is List<*>) {
                scheduled += 1
                actions += "client-query"
                return queryResults.removeFirst() as T
            }
            return listOf("connection:connected", "server:test", "feature-set:test") as T
        }
        scheduled += 1
        actions += "client-query"
        return (queryResults.removeFirstOrNull() ?: queryResult) as T
    }

    override fun stop() {
        actions += "stop"
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun isReadyToConnect(): Boolean = ready

    fun awaitActions(count: Int) {
        val deadline = System.nanoTime() + 1_000_000_000
        while (System.nanoTime() < deadline) {
            if (actionSnapshot().size >= count) {
                return
            }
            Thread.sleep(10)
        }
        error("timed out waiting for $count gateway actions; observed=${actionSnapshot()}")
    }

    fun awaitAction(action: String) {
        val deadline = System.nanoTime() + 1_000_000_000
        while (System.nanoTime() < deadline) {
            if (action in actionSnapshot()) {
                return
            }
            Thread.sleep(10)
        }
        error("timed out waiting for gateway action $action; observed=${actionSnapshot()}")
    }

    fun actionSnapshot(): List<String> = synchronized(actions) { actions.toList() }

    private fun isScreenCloseDiscoveryProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.methodName == "discoverScreenCloseAction"
        }

    private fun isCapabilityDiscoveryProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.methodName == "discoverClientCapabilities"
        }

    private fun isCapabilityGraphProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.className.endsWith("FabricClientStateCapabilityProbe")
        }

    private fun isServerFeatureMetadataProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.className.endsWith("GatewayFabricServerFeatureProvider")
        }
}
