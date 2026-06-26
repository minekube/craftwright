package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
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
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, operations.getValue("inventory.query").availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, operations.getValue("screen.close").availability.state)
        assertTrue(graph.operations.all { operation -> operation.adapter.startsWith("fabric.") })
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("player.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, events.getValue("entity.query").availability.state)
        assertEquals("object", events.getValue("entity.query").payload.type)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, events.getValue("inventory.query").availability.state)
        assertEquals(RuntimeAvailabilityState.AVAILABLE, handles.getValue("entity.handle").availability.state)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, handles.getValue("inventory.slot").availability.state)
        assertEquals("inventory-unavailable", handles.getValue("inventory.slot").availability.reason)
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
