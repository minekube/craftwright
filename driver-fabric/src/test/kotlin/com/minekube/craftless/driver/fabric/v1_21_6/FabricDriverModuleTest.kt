package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.runtime.DriverBackendAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FabricDriverModuleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fabric metadata declares client entrypoint and mixin config`() {
        val metadata = resourceJson("fabric.mod.json")

        assertEquals("craftless-driver-fabric", metadata["id"]?.jsonPrimitive?.content)
        assertEquals("0.1.0-SNAPSHOT", metadata["version"]?.jsonPrimitive?.content)
        assertEquals("com.minekube.craftless.driver.fabric.v1_21_6.CraftlessFabricClientEntrypoint", metadata["entrypoints"]
            ?.jsonObject
            ?.get("client")
            ?.jsonArray
            ?.single()
            ?.jsonPrimitive
            ?.content)
        assertEquals("craftless-driver-fabric.mixins.json", metadata["mixins"]?.jsonArray?.single()?.jsonPrimitive?.content)

        val mixins = resourceJson("craftless-driver-fabric.mixins.json")
        assertEquals("com.minekube.craftless.driver.fabric.v1_21_6.mixin", mixins["package"]?.jsonPrimitive?.content)
        assertEquals("client", mixins["environment"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fabric gateway does not expose raw command dispatch`() {
        assertTrue(FabricClientGateway::class.java.methods.none { it.name == "dispatchCommand" })
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
    fun `fabric backend schedules real client actions through a gateway`() {
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
                "chat hello client",
                "stop",
            ),
            gateway.actions,
        )
    }

    @Test
    fun `fabric backend maps player move action to movement intent`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        val result = backend.invoke(
            "alice",
            DriverActionInvocation(
                action = "player.move",
                arguments = mapOf(
                    "forward" to JsonPrimitive(true),
                    "jump" to JsonPrimitive(true),
                    "ticks" to JsonPrimitive(20),
                ),
            )
        )

        assertEquals("player.move", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("move forward jump ticks=20"), gateway.actions)
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
                    arguments = mapOf(
                        "forward" to JsonPrimitive(true),
                        "ticks" to JsonPrimitive(0),
                    ),
                )
            )
        }

        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    @Test
    fun `fabric backend maps player chat action to chat execution`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        val result = backend.invoke(
            "alice",
            DriverActionInvocation(
                action = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("hello action")),
            )
        )

        assertEquals("player.chat", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals(listOf("chat hello action"), gateway.actions)
        assertEquals(1, gateway.scheduled)
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
                )
            )
        }

        assertEquals(emptyList(), gateway.actions)
        assertEquals(0, gateway.scheduled)
    }

    private fun resourceJson(path: String) =
        json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResource(path)) { "missing resource $path" }.readText()
        ).jsonObject
}

private class RecordingFabricClientGateway : FabricClientGateway {
    var scheduled = 0
    val actions = mutableListOf<String>()

    override fun execute(action: () -> Unit) {
        scheduled += 1
        action()
    }

    override fun connect(target: ConnectionTarget) {
        actions += "connect ${target.host}:${target.port}"
    }

    override fun dispatchChatMessage(message: String) {
        actions += "chat $message"
    }

    override fun move(intent: FabricMovementIntent) {
        actions += buildString {
            append("move")
            if (intent.forward) append(" forward")
            if (intent.backward) append(" backward")
            if (intent.left) append(" left")
            if (intent.right) append(" right")
            if (intent.jump) append(" jump")
            if (intent.sneak) append(" sneak")
            if (intent.sprint) append(" sprint")
            append(" ticks=${intent.ticks}")
        }
    }

    override fun stop() {
        actions += "stop"
    }
}
