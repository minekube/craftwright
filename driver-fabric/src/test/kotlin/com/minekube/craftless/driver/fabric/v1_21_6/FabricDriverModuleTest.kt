package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverOperationInvocation
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FabricDriverModuleTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { path -> path.parent }
            .first { path -> Files.exists(path.resolve("settings.gradle.kts")) }

    private fun transitionalFabricActionAllowlist(root: Path): List<String> =
        Files
            .readAllLines(root.resolve("docs/architecture/transitional-fabric-action-allowlist.txt"))
            .map { line -> line.substringBefore("#").trim() }
            .filter { line -> line.isNotBlank() }
            .sorted()

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
                driverVersion = "0.1.0-SNAPSHOT",
                installedMods = listOf("minecraft@1.21.6", "fabricloader@0.19.3"),
                registries = listOf("block:craftless-test"),
                serverFeatures = listOf("environment:test"),
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
            listOf("ClientRecipeBookAccessor", "MinecraftClientMixin"),
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
    fun `transitional fabric binding ids are represented as runtime graph operations`() {
        val bindingIds = defaultFabricActionBindings().map { it.descriptor.id }.sorted()
        val graphOperationIds =
            FabricDriverBackend
                .metadataOnly()
                .runtimeGraph("alice")
                .operations
                .map { it.id }
                .sorted()

        assertEquals(bindingIds, graphOperationIds.filter { it in bindingIds })
    }

    @Test
    fun `fabric runtime metadata fingerprints runtime registry entries`() {
        val provider =
            SnapshotFabricRuntimeMetadataProvider(
                FabricRuntimeMetadataSnapshot(
                    loaderVersion = "0.19.3",
                    driverVersion = "0.1.0-SNAPSHOT",
                    installedMods = listOf("minecraft@1.21.6", "fabricloader@0.19.3"),
                    registries = listOf("item:minecraft:iron_sword", "block:minecraft:stone"),
                    serverFeatures = listOf("environment:dev"),
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
    fun `fabric final gameplay plan gates completion on graph streams artifacts and robin chat`() {
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
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.INVITE_ROBIN })
        assertTrue(plan.steps.any { it.kind == FabricFinalGameplayStepKind.WAIT_FOR_ROBIN_CHAT_CONFIRMATION })
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
        assertTrue(plan.completionGates.any { it.contains("Robin", ignoreCase = true) && it.contains("Minecraft chat", ignoreCase = true) })
        assertTrue(plan.completionGates.any { it.contains("SSE", ignoreCase = true) })
        assertTrue(plan.completionGates.any { it.contains("no server-side item provisioning", ignoreCase = true) })
        assertFalse(plan.completionGates.any { it.contains("provisioned", ignoreCase = true) && !it.contains("no", ignoreCase = true) })
        assertTrue(plan.completionGates.none { it.contains("static fallback", ignoreCase = true) && !it.contains("no", ignoreCase = true) })
        assertFalse(plan.artifacts.contains("provisioned-iron-sword"))
    }

    @Test
    fun `fabric final gameplay outer timeout covers public agent runtime and human hold window`() {
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
        assertTrue(buildScript.contains("\"runtime-lane-missing\""))
        assertTrue(buildScript.contains("\"unsupported-version\""))
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

        assertEquals(setOf("player.chat", "player.move"), actionIds)
    }

    @Test
    fun `hand written fabric gameplay descriptors stay transitional and graph represented`() {
        val root = repositoryRoot()
        val allowlist = transitionalFabricActionAllowlist(root)
        val source =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionBindings.kt",
                ),
            )
        val descriptorIds =
            Regex("""id = "([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*)"""")
                .findAll(source)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toList()
        val graphOperationIds =
            FabricDriverBackend
                .metadataOnly()
                .runtimeGraph("alice")
                .operations
                .map { operation -> operation.id }
                .toSet()

        assertEquals(
            allowlist,
            descriptorIds,
            "Hand-written Fabric gameplay descriptors are transitional only; " +
                "new public gameplay breadth must be discovered through the runtime graph.",
        )
        assertTrue(
            graphOperationIds.containsAll(allowlist),
            "Transitional descriptor ids must be represented by runtime graph operations.",
        )
    }

    @Test
    fun `fabric backend can expose unavailable actions only from runtime discovery probes`() {
        val backend =
            FabricDriverBackend.metadataOnly(
                actionDiscovery =
                    FabricActionDiscovery { context ->
                        assertEquals("metadata-only", context.modeId)
                        assertEquals(null, context.gateway)
                        listOf(
                            FabricDiscoveredAction(
                                descriptor =
                                    DriverActionDescriptor(
                                        id = "player.raycast",
                                        schemaVersion = "1",
                                        source = DriverActionSource.RUNTIME_PROBE,
                                        availability = DriverActionAvailability.UNAVAILABLE,
                                        availabilityReason = "client-not-connected",
                                    ),
                            ),
                        )
                    },
            )

        val action = backend.actions("alice").single()
        val result = backend.invoke("alice", DriverActionInvocation("player.raycast"))

        assertEquals("player.raycast", action.id)
        assertEquals(DriverActionSource.RUNTIME_PROBE, action.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, action.availability)
        assertEquals("client-not-connected", action.availabilityReason)
        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("client-not-connected", result.message)
    }

    @Test
    fun `fabric default discovery is composed from runtime probes`() {
        val discovery =
            defaultFabricActionDiscovery(
                probes =
                    listOf(
                        FabricActionProbe { context ->
                            assertEquals("metadata-only", context.modeId)
                            listOf(
                                FabricDiscoveredAction(
                                    descriptor =
                                        DriverActionDescriptor(
                                            id = "player.raycast",
                                            schemaVersion = "1",
                                            source = DriverActionSource.RUNTIME_PROBE,
                                            availability = DriverActionAvailability.UNAVAILABLE,
                                            availabilityReason = "client-not-connected",
                                        ),
                                ),
                            )
                        },
                        FabricActionProbe {
                            listOf(
                                FabricDiscoveredAction(
                                    descriptor =
                                        DriverActionDescriptor(
                                            id = "screen.query",
                                            schemaVersion = "1",
                                            source = DriverActionSource.RUNTIME_PROBE,
                                            availability = DriverActionAvailability.UNAVAILABLE,
                                            availabilityReason = "screen-unavailable",
                                        ),
                                ),
                            )
                        },
                    ),
            )
        val backend = FabricDriverBackend.metadataOnly(discovery)

        assertEquals(listOf("player.raycast", "screen.query"), backend.actions("alice").map { it.id })
    }

    @Test
    fun `fabric discovery rejects duplicate action ids from probes`() {
        val discovery =
            defaultFabricActionDiscovery(
                probes =
                    listOf(
                        FabricActionProbe {
                            listOf(
                                FabricDiscoveredAction(
                                    descriptor = FabricPlayerQueryActionBinding.descriptor,
                                    binding = FabricPlayerQueryActionBinding,
                                ),
                            )
                        },
                        FabricActionProbe {
                            listOf(
                                FabricDiscoveredAction(
                                    descriptor = FabricPlayerQueryActionBinding.descriptor,
                                    binding = FabricPlayerQueryActionBinding,
                                ),
                            )
                        },
                    ),
            )
        val backend = FabricDriverBackend.metadataOnly(discovery)

        val error = assertFailsWith<IllegalArgumentException> { backend.actions("alice") }

        assertEquals("duplicate discovered Fabric action id player.query", error.message)
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
        assertEquals(DriverActionSource.BINDING, connectedRaycast.source)
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

        assertEquals(DriverActionSource.BINDING, player.source)
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

        assertEquals(DriverActionSource.BINDING, look.source)
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

        assertEquals(DriverActionSource.BINDING, inventory.source)
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

        assertEquals(DriverActionSource.BINDING, equip.source)
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

        assertEquals(DriverActionSource.BINDING, blockBreak.source)
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

        assertTrue(craftRecipeSource.contains("clickRecipe("))
        assertTrue(craftRecipeSource.contains("AbstractCraftingScreenHandler"))
        assertTrue(craftRecipeSource.contains("getOutputSlot()"))
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

        assertEquals(DriverActionSource.BINDING, blockInteract.source)
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

        assertEquals(DriverActionSource.BINDING, worldTime.source)
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
        assertEquals(2, gateway.capabilityProbeQueries)
    }

    @Test
    fun `fabric runtime discovery exposes screen query only from live client state`() {
        val metadataOnly = FabricDriverBackend.metadataOnly()
        assertTrue(metadataOnly.actions("alice").none { it.id == "screen.query" })

        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)
        gateway.queryResult =
            buildJsonObject {
                put("open", true)
                put("title", "Inventory")
            }

        val screen = backend.actions("alice").single { it.id == "screen.query" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.query"))

        assertEquals(DriverActionSource.BINDING, screen.source)
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
        assertTrue(metadataOnly.actions("alice").none { it.id == "screen.close" })

        val gateway = RecordingFabricClientGateway()
        gateway.screenOpen = false
        val backend = FabricDriverBackend.real(gateway)

        val close = backend.actions("alice").single { it.id == "screen.close" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.close"))

        assertEquals(DriverActionSource.RUNTIME_PROBE, close.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, close.availability)
        assertEquals("screen-not-open", close.availabilityReason)
        assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
        assertEquals("screen-not-open", result.message)
        assertEquals(2, gateway.screenProbeQueries)
        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric runtime discovery exposes screen close only when a screen is open`() {
        val gateway = RecordingFabricClientGateway()
        gateway.screenOpen = true
        val backend = FabricDriverBackend.real(gateway)

        val close = backend.actions("alice").single { it.id == "screen.close" }
        val result = backend.invoke("alice", DriverActionInvocation("screen.close"))

        assertEquals(DriverActionSource.BINDING, close.source)
        assertEquals(DriverActionAvailability.AVAILABLE, close.availability)
        assertEquals(null, close.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(2, gateway.screenProbeQueries)
        assertEquals(listOf("client-action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    @Test
    fun `fabric discovery rejects available actions without execution binding`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                FabricDiscoveredAction(
                    descriptor =
                        DriverActionDescriptor(
                            id = "player.look",
                            schemaVersion = "1",
                        ),
                    binding = null,
                )
            }

        assertEquals("discovered action player.look must have a binding or unavailable runtime-probe metadata", error.message)
    }

    @Test
    fun `fabric backend reports missing player chat message as action failure`() {
        val gateway = RecordingFabricClientGateway()
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
}
