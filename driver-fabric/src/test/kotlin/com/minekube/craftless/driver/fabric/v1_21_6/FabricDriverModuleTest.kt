package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.runtime.DriverBackendAction
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
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class FabricDriverModuleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fabric metadata declares client entrypoint and mixin config`() {
        val metadata = resourceJson("fabric.mod.json")

        assertEquals("craftless-driver-fabric", metadata["id"]?.jsonPrimitive?.content)
        assertEquals("0.1.0-SNAPSHOT", metadata["version"]?.jsonPrimitive?.content)
        assertEquals(
            "com.minekube.craftless.driver.fabric.v1_21_6.CraftlessFabricClientEntrypoint",
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

        val mixins = resourceJson("craftless-driver-fabric.mixins.json")
        assertEquals("com.minekube.craftless.driver.fabric.v1_21_6.mixin", mixins["package"]?.jsonPrimitive?.content)
        assertEquals("client", mixins["environment"]?.jsonPrimitive?.content)
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
        assertEquals("1.21.6", plan.minecraftVersion)
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
    fun `fabric backend schedules generated actions through generic client execution`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

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
    fun `fabric backend rejects nonpositive movement ticks before scheduling gateway`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        assertFailsWith<IllegalArgumentException> {
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
        }

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
    fun `fabric backend does not advertise static placeholder gameplay actions`() {
        val backend = FabricDriverBackend.metadataOnly()

        val actionIds = backend.actions("alice").map { it.id }.toSet()

        assertEquals(setOf("player.chat", "player.move"), actionIds)
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
        val backend = FabricDriverBackend.real(gateway)

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
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["started"]?.jsonPrimitive?.boolean)
        assertEquals("1 64 1", result.data["block"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
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
        assertEquals("object", unavailableInteract.result.properties["data"]?.type)
        assertEquals(DriverActionStatus.UNSUPPORTED, unavailableResult.status)
        assertEquals("client-not-connected", unavailableResult.message)

        gateway.connected = true
        gateway.queryResult =
            buildJsonObject {
                put("hit", true)
                put("target-kind", "block")
                put("accepted", true)
                put("block", "1 64 1")
            }

        val blockInteract = backend.actions("alice").single { it.id == "world.block.interact" }
        val result =
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "world.block.interact",
                    arguments = mapOf("max-distance" to JsonPrimitive(4.0)),
                ),
            )

        assertEquals(DriverActionSource.BINDING, blockInteract.source)
        assertEquals(DriverActionAvailability.AVAILABLE, blockInteract.availability)
        assertEquals(null, blockInteract.availabilityReason)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(true, result.data["accepted"]?.jsonPrimitive?.boolean)
        assertEquals("1 64 1", result.data["block"]?.jsonPrimitive?.content)
        assertEquals(listOf("client-query"), gateway.actions)
        assertEquals(1, gateway.scheduled)
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
    fun `fabric backend rejects raw minecraft command strings as chat action input`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        assertFailsWith<IllegalArgumentException> {
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("/server lobby")),
                ),
            )
        }

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

        assertEquals(0.milliseconds, controller.startupSettleDelay)
        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitActions(12)
        assertEquals(
            listOf(
                "connect localhost:25567",
                "client-action",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-action",
                "client-action",
                "client-query",
                "client-query",
                "stop",
            ),
            gateway.actions,
        )
        assertTrue(Files.readString(artifactsDir.resolve("client-openapi.json")).contains("/clients/fabric-smoke:run"))
        assertTrue(Files.readString(artifactsDir.resolve("client-openapi.json")).contains("craftless-driver-fabric"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions.json")).contains("player.chat"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions.json")).contains("player.move"))
        assertTrue(Files.readString(artifactsDir.resolve("client-resources.json")).contains("\"id\":\"player\""))
        val connectedOpenApi = Files.readString(artifactsDir.resolve("client-openapi-connected.json"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/player:query"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/player:look"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/screen:query"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/block:break"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/block:interact"))
        assertTrue(connectedOpenApi.contains("/clients/fabric-smoke/world/time:query"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("player.query"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("player.look"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("inventory.query"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("inventory.equip"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("screen.query"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("world.block.break"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("world.block.interact"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("world.time.query"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions-connected.json")).contains("\"availability\":\"available\""))
        val connectedResources = Files.readString(artifactsDir.resolve("client-resources-connected.json"))
        assertTrue(connectedResources.contains("\"id\":\"player\""))
        assertTrue(connectedResources.contains("\"id\":\"inventory\""))
        assertTrue(connectedResources.contains("\"id\":\"screen\""))
        assertTrue(connectedResources.contains("\"id\":\"world.block\""))
        assertTrue(connectedResources.contains("\"id\":\"world.time\""))
        assertTrue(connectedResources.contains("\"availability\":\"available\""))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("player.query"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("position-before"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("screen.query"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("world.time.query"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("player.look"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("inventory.query"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("inventory.equip"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("slot 1"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("Iron Sword"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("world.block.break"))
        assertTrue(Files.readString(artifactsDir.resolve("gameplay-results.jsonl")).contains("world.block.interact"))
        assertTrue(Files.readString(artifactsDir.resolve("runtime-metadata.json")).contains("craftless-driver-fabric"))
        assertTrue(Files.readString(artifactsDir.resolve("client-events.jsonl")).contains("hello from fabric smoke"))
        assertTrue(Files.readString(artifactsDir.resolve("client-events.jsonl")).contains("player.move"))
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

        gateway.awaitActions(13)
        assertEquals(
            listOf(
                "connect 127.0.0.1:25565",
                "client-action",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-action",
                "client-action",
                "client-query",
                "client-query",
                "stop",
            ),
            gateway.actions,
        )
        val gameplay = Files.readString(artifactsDir.resolve("gameplay-results.jsonl"))
        assertTrue(gameplay.contains("craftless-smoke-target-item-observed"))
        assertTrue(gameplay.contains("slot 2"))
        assertTrue(gameplay.contains("Iron Sword"))
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
    fun `fabric smoke controller waits for client readiness before connecting`() {
        val gateway = RecordingFabricClientGateway()
        gateway.ready = false
        val backend = smokeBackend(gateway)
        val controller =
            FabricClientSmokeController.fromEnvironment(
                mapOf(
                    "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                    "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                    "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS" to "1000",
                    "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS" to "0",
                ),
            )

        assertTrue(controller.start(backend, gateway, pollInterval = 10.milliseconds))

        Thread.sleep(100)
        assertEquals(emptyList(), gateway.actions)

        gateway.ready = true
        gateway.awaitActions(12)
        assertEquals(
            listOf(
                "connect 127.0.0.1:25567",
                "client-action",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-query",
                "client-action",
                "client-action",
                "client-query",
                "client-query",
                "stop",
            ),
            gateway.actions,
        )
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

private class RecordingFabricClientGateway : FabricClientGateway {
    var scheduled = 0
    val actions = mutableListOf<String>()
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

    @Volatile
    var connected = false

    @Volatile
    var ready = true

    override fun execute(action: () -> Unit) {
        scheduled += 1
        action()
    }

    override fun connect(target: ConnectionTarget) {
        actions += "connect ${target.host}:${target.port}"
        connected = true
    }

    override fun executeOnClient(action: net.minecraft.client.MinecraftClient.() -> Unit) {
        scheduled += 1
        actions += "client-action"
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> queryOnClient(query: net.minecraft.client.MinecraftClient.() -> T): T {
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
            if (actions.size >= count) {
                return
            }
            Thread.sleep(10)
        }
    }

    private fun isScreenCloseDiscoveryProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.methodName == "discoverScreenCloseAction"
        }

    private fun isCapabilityDiscoveryProbe(): Boolean =
        Thread.currentThread().stackTrace.any { frame ->
            frame.methodName == "discoverClientCapabilities"
        }
}
