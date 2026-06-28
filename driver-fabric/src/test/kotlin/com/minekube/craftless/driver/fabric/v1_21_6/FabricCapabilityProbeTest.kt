package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeIdentity
import com.minekube.craftless.driver.fabric.runtime.defaultFabricCompatibilityMatrix
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeAvailabilityState
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FabricCapabilityProbeTest {
    @Test
    fun `fabric capability probes compose graph fragments`() {
        val discovery =
            defaultFabricCapabilityDiscovery(
                probes =
                    listOf(
                        FabricCapabilityProbe {
                            FabricCapabilityGraphFragment(
                                resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                            )
                        },
                        FabricCapabilityProbe {
                            FabricCapabilityGraphFragment(
                                operations =
                                    listOf(
                                        RuntimeOperationNode(
                                            id = "player.query",
                                            resource = "player",
                                            adapter = "fabric.player-query",
                                            availability = RuntimeAvailability.available(),
                                        ),
                                    ),
                            )
                        },
                    ),
            )

        val graph =
            discovery.discover(
                FabricCapabilityProbeContext(
                    clientId = "alice",
                    modeId = "real-client",
                    gateway = null,
                ),
            )

        assertEquals("alice", graph.clientId)
        assertEquals(listOf("player"), graph.resources.map { it.id })
        assertEquals(listOf("player.query"), graph.operations.map { it.id })
        assertTrue(graph.fingerprint().startsWith("graph:"))
    }

    @Test
    fun `fabric capability probes reject duplicate graph node ids`() {
        val discovery =
            defaultFabricCapabilityDiscovery(
                probes =
                    listOf(
                        FabricCapabilityProbe {
                            FabricCapabilityGraphFragment(
                                resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                            )
                        },
                        FabricCapabilityProbe {
                            FabricCapabilityGraphFragment(
                                resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                            )
                        },
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            discovery.discover(
                FabricCapabilityProbeContext(
                    clientId = "alice",
                    modeId = "real-client",
                    gateway = null,
                ),
            )
        }
    }

    @Test
    fun `fabric capability probes do not output public action descriptors directly`() {
        val method = FabricCapabilityProbe::class.java.methods.single { it.name == "discover" }

        assertEquals(FabricCapabilityGraphFragment::class.java, method.returnType)
        assertNotEquals(DriverActionDescriptor::class.java, method.returnType)
        assertTrue(method.genericReturnType.typeName.contains("DriverActionDescriptor") == false)
        assertTrue(method.genericReturnType.typeName.contains("OpenApi") == false)
    }

    @Test
    fun `fabric capability probe context does not receive action bindings for graph schemas`() {
        assertTrue(
            FabricCapabilityProbeContext::class.java.declaredFields.none { field ->
                field.name == "bindings" || field.type.name.contains("FabricActionBinding")
            },
        )
        assertTrue(
            FabricCapabilityProbeContext::class.java.constructors.none { constructor ->
                constructor.parameterTypes.any { type -> type.name.contains("FabricActionBinding") }
            },
        )
    }

    @Test
    fun `fabric graph schemas stay available without binding descriptor fallback`() {
        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricClientStateCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "metadata-only",
                        gateway = null,
                    ),
                )

        val operations = graph.operations.associateBy { it.id }
        val raycast = operations.getValue("player.raycast")

        assertEquals("string", operations.getValue("player.chat").arguments["message"]?.type)
        assertEquals(true, operations.getValue("player.chat").arguments["message"]?.required)
        assertEquals("integer", operations.getValue("player.move").arguments["ticks"]?.type)
        assertEquals("integer", operations.getValue("inventory.equip").arguments["slot"]?.type)
        assertEquals(true, operations.getValue("inventory.equip").arguments["slot"]?.required)
        assertEquals("number", raycast.arguments["max-distance"]?.type)
        assertEquals("object", operations.getValue("world.block.break").arguments["target"]?.type)
        assertEquals("object", operations.getValue("world.block.interact").arguments["target"]?.type)
        assertEquals("object", raycast.result.type)
        assertEquals("object", raycast.result.properties["data"]?.type)
    }

    @Test
    fun `fabric graph operations expose graph-owned result schemas`() {
        val graph =
            defaultFabricCapabilityDiscovery()
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "metadata-only",
                        gateway = null,
                    ),
                )

        val raycast = graph.operations.single { it.id == "player.raycast" }

        assertEquals("object", raycast.result.type)
        assertEquals("string", raycast.result.properties["action"]?.type)
        assertEquals(true, raycast.result.properties["action"]?.required)
        assertEquals("string", raycast.result.properties["status"]?.type)
        assertEquals(true, raycast.result.properties["status"]?.required)
        assertEquals("string", raycast.result.properties["message"]?.type)
        assertEquals(false, raycast.result.properties["message"]?.required)
        assertEquals("object", raycast.result.properties["data"]?.type)
        assertEquals(false, raycast.result.properties["data"]?.required)
    }

    @Test
    fun `runtime metadata probe emits private evidence for registry and runtime inputs`() {
        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricRuntimeMetadataCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "real-client",
                        gateway = null,
                        runtimeMetadata =
                            DriverRuntimeMetadata(
                                loaderVersion = "0.19.3",
                                driver = "craftless-driver-fabric",
                                mappings = "craftless-fabric-bindings",
                                installedModsFingerprint = "mods:test",
                                registryFingerprint = "registries:test",
                                serverFeatureFingerprint = "server-features:test",
                                permissionsFingerprint = "permissions:test",
                            ),
                    ),
                )

        val runtime = graph.resources.single { it.id == "runtime" }

        assertEquals(RuntimeAvailabilityState.AVAILABLE, runtime.availability.state)
        assertEquals(
            setOf("installed-mods", "registry", "server-features", "permissions"),
            runtime.sourceEvidence.map { it.kind }.toSet(),
        )
        assertTrue(runtime.sourceEvidence.none { it.fingerprint.contains("minecraft:") })
    }

    @Test
    fun `runtime metadata probe emits sanitized compatibility lane evidence`() {
        val lane =
            defaultFabricCompatibilityMatrix()
                .resolve(
                    FabricRuntimeIdentity(
                        gameVersion = "26.2",
                        loaderVersion = "unverified",
                        fabricApiVersion = "unverified",
                        mappingsFingerprint = "unverified",
                        installedModsFingerprint = "mods:test",
                        registryFingerprint = "registries:test",
                        serverFeatureFingerprint = "server-features:test",
                        permissionsFingerprint = "permissions:test",
                    ),
                )
        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricRuntimeMetadataCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "real-client",
                        gateway = null,
                        compatibilityLane = lane,
                    ),
                )

        val evidence = graph.resources.single { it.id == "runtime" }.sourceEvidence

        assertEquals(
            setOf(
                "installed-mods",
                "registry",
                "server-features",
                "permissions",
                "runtime-lane",
                "runtime-provider",
                "runtime-status",
                "runtime-support",
                "runtime-java",
            ),
            evidence.map { it.kind }.toSet(),
        )
        assertTrue(evidence.any { it.kind == "runtime-lane" && it.fingerprint == "latest-release-26-2" })
        assertTrue(evidence.any { it.kind == "runtime-provider" && it.fingerprint == "no-compatible-client-lane" })
        assertTrue(evidence.any { it.kind == "runtime-status" && it.fingerprint == "unsupported" })
        assertTrue(evidence.any { it.kind == "runtime-support" && it.fingerprint == "runtime-lane-missing" })
        assertTrue(evidence.none { item -> "simulated" in item.fingerprint })
        assertTrue(
            evidence.none { item ->
                listOf("fabric", "minecraft", "yarn", "intermediary").any { token -> token in item.fingerprint }
            },
        )
    }

    @Test
    fun `registry summary probe emits generic registry handles from runtime metadata`() {
        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricRegistrySummaryCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "real-client",
                        gateway = null,
                        runtimeMetadata =
                            DriverRuntimeMetadata(
                                driver = "craftless-driver-fabric",
                                registryFingerprint = "registries:test",
                            ),
                    ),
                )

        val registry = graph.resources.single { it.id == "registry" }
        val handles = graph.handles.associateBy { it.id }

        assertEquals(RuntimeAvailabilityState.AVAILABLE, registry.availability.state)
        assertEquals(listOf("registry"), registry.sourceEvidence.map { it.kind })
        assertEquals("registries:test", registry.sourceEvidence.single().fingerprint)
        assertEquals(
            setOf(
                "registry.block",
                "registry.item",
                "registry.entity",
                "registry.screen",
                "registry.effect",
                "registry.event",
            ),
            handles.keys,
        )
        assertTrue(handles.values.all { handle -> handle.resource == "registry" })
        assertTrue(handles.values.all { handle -> handle.schema.type == "object" })
        assertTrue(handles.values.all { handle -> handle.sourceEvidence.single().kind == "registry" })
        assertEquals(emptyList(), graph.operations)
    }

    @Test
    fun `event source probe emits generic event stream nodes without gameplay operations`() {
        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricEventSourceCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "real-client",
                        gateway = null,
                        runtimeMetadata =
                            DriverRuntimeMetadata(
                                driver = "craftless-driver-fabric",
                                driverVersion = "0.2.0-test",
                            ),
                    ),
                )

        val event = graph.resources.single { it.id == "event" }
        val events = graph.events.associateBy { it.id }

        assertEquals(RuntimeAvailabilityState.AVAILABLE, event.availability.state)
        assertEquals(setOf("event-source", "mixin", "callback"), event.sourceEvidence.map { it.kind }.toSet())
        assertTrue(
            event.sourceEvidence
                .single { it.kind == "mixin" }
                .fingerprint
                .startsWith("craftless-client-tick"),
        )
        assertTrue(
            event.sourceEvidence
                .filter { it.kind == "callback" }
                .map { it.fingerprint }
                .contains("craftless-callback-play-join"),
        )
        assertEquals(
            setOf("event.lifecycle", "event.action", "event.capability"),
            events.keys,
        )
        assertTrue(events.values.all { node -> node.resource == "event" })
        assertTrue(events.values.all { node -> node.payload.type == "object" })
        assertTrue(
            events.values.all { node ->
                node.sourceEvidence.map { it.kind }.toSet() == setOf("event-source", "mixin", "callback")
            },
        )
        assertEquals(emptyList(), graph.operations)
    }

    @Test
    fun `client state probe queries gateway and emits availability graph nodes`() {
        val gateway =
            RecordingCapabilityGateway(
                connected = true,
                queries =
                    ArrayDeque(
                        listOf(
                            FabricClientCapabilitySnapshot(
                                connected = true,
                                player = true,
                                inventory = false,
                                camera = true,
                                interactionManager = true,
                                world = true,
                            ),
                            false,
                        ),
                    ),
            )

        val graph =
            defaultFabricCapabilityDiscovery(probes = listOf(FabricClientStateCapabilityProbe))
                .discover(
                    FabricCapabilityProbeContext(
                        clientId = "alice",
                        modeId = "real-client",
                        gateway = gateway,
                    ),
                )

        val resources = graph.resources.associateBy { it.id }
        val operations = graph.operations.associateBy { it.id }
        val events = graph.events.associateBy { it.id }
        val handles = graph.handles.associateBy { it.id }

        assertEquals(2, gateway.queryCount)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, resources.getValue("player").availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, resources.getValue("inventory").availability.state)
        assertEquals("inventory-unavailable", resources.getValue("inventory").availability.reason)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, resources.getValue("world").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, resources.getValue("entity").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, operations.getValue("player.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, operations.getValue("entity.query").availability.state)
        assertEquals("number", operations.getValue("entity.query").arguments["radius"]?.type)
        assertEquals("integer", operations.getValue("entity.query").arguments["limit"]?.type)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, operations.getValue("entity.attack").availability.state)
        assertEquals("object", operations.getValue("entity.attack").arguments["target"]?.type)
        assertEquals(true, operations.getValue("entity.attack").arguments["target"]?.required)
        assertEquals("number", operations.getValue("entity.attack").arguments["max-distance"]?.type)
        assertEquals("object", operations.getValue("entity.attack").result.type)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, operations.getValue("world.block.query").availability.state)
        assertEquals("number", operations.getValue("world.block.query").arguments["radius"]?.type)
        assertEquals("integer", operations.getValue("world.block.query").arguments["limit"]?.type)
        assertEquals("string", operations.getValue("world.block.query").arguments["category"]?.type)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, operations.getValue("inventory.query").availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, operations.getValue("screen.close").availability.state)
        assertTrue(graph.operations.all { operation -> operation.adapter.startsWith("fabric.") })
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("player.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("entity.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("entity.attack").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("world.block.query").availability.state)
        assertEquals("object", events.getValue("entity.query").payload.type)
        assertEquals("object", events.getValue("entity.attack").payload.type)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, events.getValue("inventory.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, handles.getValue("world.block.handle").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, handles.getValue("entity.handle").availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, handles.getValue("inventory.slot").availability.state)
        assertEquals("inventory-unavailable", handles.getValue("inventory.slot").availability.reason)
        assertEquals("object", handles.getValue("world.block.handle").schema.type)
        assertEquals("object", handles.getValue("entity.handle").schema.type)
    }
}

private class RecordingCapabilityGateway(
    private var connected: Boolean = false,
    private val queries: ArrayDeque<Any> = ArrayDeque(),
) : FabricClientGateway {
    var queryCount = 0

    override fun execute(action: () -> Unit) {
        action()
    }

    override fun executeOnClient(action: net.minecraft.client.MinecraftClient.() -> Unit) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <T> queryOnClient(query: net.minecraft.client.MinecraftClient.() -> T): T {
        queryCount += 1
        return queries.removeFirst() as T
    }

    override fun connect(target: com.minekube.craftless.driver.api.ConnectionTarget) {
        connected = true
    }

    override fun stop() {
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun isReadyToConnect(): Boolean = !connected
}
