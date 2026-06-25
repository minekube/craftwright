package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
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
    fun `fabric client smoke plan is opt in and bridge independent`() {
        val plan = FabricClientSmokePlan.default()

        assertEquals("CRAFTLESS_FABRIC_CLIENT_SMOKE", plan.environmentGate)
        assertEquals("1.21.6", plan.minecraftVersion)
        assertEquals(listOf(":driver-fabric:fabricClientSmoke"), plan.gradleTasks)
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.START_LOCAL_SERVER })
        assertTrue(plan.steps.any { it.description.contains("kept running", ignoreCase = true) })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.LAUNCH_FABRIC_CLIENT })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.INVOKE_GENERATED_CHAT_ACTION })
        assertTrue(plan.steps.any { it.kind == FabricSmokeStepKind.ASSERT_SERVER_EVIDENCE })
        assertTrue(plan.artifacts.contains("server-evidence.jsonl"))
        assertTrue(plan.artifacts.contains("client-openapi.json"))
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

        assertEquals("player.move", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("client-action"), gateway.actions)
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
        val backend = FabricDriverBackend.real(gateway)
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

        assertEquals(0.milliseconds, controller.startupSettleDelay)
        assertTrue(controller.start(backend, gateway, pollInterval = 1.milliseconds))

        gateway.awaitActions(4)
        assertEquals(
            listOf(
                "connect localhost:25567",
                "client-action",
                "client-action",
                "stop",
            ),
            gateway.actions,
        )
        assertTrue(Files.readString(artifactsDir.resolve("client-openapi.json")).contains("/clients/fabric-smoke:run"))
        assertTrue(Files.readString(artifactsDir.resolve("client-openapi.json")).contains("craftless-driver-fabric"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions.json")).contains("player.chat"))
        assertTrue(Files.readString(artifactsDir.resolve("client-actions.json")).contains("player.move"))
        assertTrue(Files.readString(artifactsDir.resolve("runtime-metadata.json")).contains("craftless-driver-fabric"))
        assertTrue(Files.readString(artifactsDir.resolve("client-events.jsonl")).contains("hello from fabric smoke"))
        assertTrue(Files.readString(artifactsDir.resolve("client-events.jsonl")).contains("player.move"))
    }

    @Test
    fun `fabric smoke controller waits for client readiness before connecting`() {
        val gateway = RecordingFabricClientGateway()
        gateway.ready = false
        val backend = FabricDriverBackend.real(gateway)
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
        gateway.awaitActions(4)
        assertEquals(
            listOf(
                "connect 127.0.0.1:25567",
                "client-action",
                "client-action",
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
}

private class RecordingFabricClientGateway : FabricClientGateway {
    var scheduled = 0
    val actions = mutableListOf<String>()
    var queryResult =
        buildJsonObject {
            put("hit", true)
            put("target-kind", "block")
        }

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
        scheduled += 1
        actions += "client-query"
        return queryResult as T
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
}
