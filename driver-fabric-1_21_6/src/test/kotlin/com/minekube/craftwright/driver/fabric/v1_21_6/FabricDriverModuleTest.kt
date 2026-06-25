package com.minekube.craftwright.driver.fabric.v1_21_6

import com.minekube.craftwright.driver.api.ChatCommand
import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverCapabilityInvocation
import com.minekube.craftwright.driver.api.DriverCapabilityStatus
import com.minekube.craftwright.driver.api.PlayerPosition
import com.minekube.craftwright.driver.runtime.DriverBackendAction
import com.minekube.craftwright.protocol.ClientState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FabricDriverModuleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `fabric metadata declares client entrypoint and mixin config`() {
        val metadata = resourceJson("fabric.mod.json")

        assertEquals("craftwright-driver-fabric-1-21-6", metadata["id"]?.jsonPrimitive?.content)
        assertEquals("0.1.0-SNAPSHOT", metadata["version"]?.jsonPrimitive?.content)
        assertEquals("com.minekube.craftwright.driver.fabric.v1_21_6.CraftwrightFabricClientEntrypoint", metadata["entrypoints"]
            ?.jsonObject
            ?.get("client")
            ?.jsonArray
            ?.single()
            ?.jsonPrimitive
            ?.content)
        assertEquals("craftwright-driver-fabric-1_21_6.mixins.json", metadata["mixins"]?.jsonArray?.single()?.jsonPrimitive?.content)

        val mixins = resourceJson("craftwright-driver-fabric-1_21_6.mixins.json")
        assertEquals("com.minekube.craftwright.driver.fabric.v1_21_6.mixin", mixins["package"]?.jsonPrimitive?.content)
        assertEquals("client", mixins["environment"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fabric backend exposes driver runtime actions without changing daemon contract`() {
        val backend = FabricDriverBackend.placeholder()

        assertEquals(
            DriverBackendAction.CONNECT,
            backend.connect("alice", ConnectionTarget("127.0.0.1", 25565)).action,
        )
        assertEquals(
            DriverBackendAction.CHAT,
            backend.sendChat("alice", ChatCommand("hello fabric")).action,
        )
        assertEquals(DriverBackendAction.STOP, backend.stop("alice").action)
        assertTrue(backend.events().any { it.contains("connect alice 127.0.0.1:25565") })
    }

    @Test
    fun `fabric backend schedules real client actions through a gateway`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        backend.connect("alice", ConnectionTarget("127.0.0.1", 25565))
        backend.sendChat("alice", ChatCommand("hello client"))
        backend.sendChat("alice", ChatCommand("/server lobby"))
        backend.stop("alice")

        assertEquals(4, gateway.scheduled)
        assertEquals(
            listOf(
                "connect 127.0.0.1:25565",
                "chat hello client",
                "command server lobby",
                "stop",
            ),
            gateway.actions,
        )
    }

    @Test
    fun `fabric backend exposes observed player state from gateway`() {
        val gateway = RecordingFabricClientGateway(
            observedPlayer = FabricClientPlayer(
                name = "ObservedAlice",
                state = ClientState.CONNECTED,
                position = PlayerPosition(x = 4.5, y = 70.0, z = -11.25),
            )
        )
        val backend = FabricDriverBackend.real(gateway)

        val player = backend.player("alice")

        assertEquals("ObservedAlice", player?.name)
        assertEquals(ClientState.CONNECTED, player?.state)
        assertEquals(PlayerPosition(x = 4.5, y = 70.0, z = -11.25), player?.position)
        assertEquals(listOf("player"), gateway.actions)
    }

    @Test
    fun `fabric backend maps player move capability to movement intent`() {
        val gateway = RecordingFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)

        val result = backend.invoke(
            "alice",
            DriverCapabilityInvocation(
                capability = "player.move",
                arguments = mapOf(
                    "forward" to "true",
                    "jump" to "true",
                    "ticks" to "20",
                ),
            )
        )

        assertEquals("player.move", result.capability)
        assertEquals(DriverCapabilityStatus.ACCEPTED, result.status)
        assertEquals(listOf("move forward jump ticks=20"), gateway.actions)
        assertEquals(1, gateway.scheduled)
    }

    private fun resourceJson(path: String) =
        json.parseToJsonElement(
            requireNotNull(javaClass.classLoader.getResource(path)) { "missing resource $path" }.readText()
        ).jsonObject
}

private class RecordingFabricClientGateway(
    private val observedPlayer: FabricClientPlayer? = null,
) : FabricClientGateway {
    var scheduled = 0
    val actions = mutableListOf<String>()

    override fun execute(action: () -> Unit) {
        scheduled += 1
        action()
    }

    override fun connect(target: ConnectionTarget) {
        actions += "connect ${target.host}:${target.port}"
    }

    override fun sendChat(message: String) {
        actions += "chat $message"
    }

    override fun sendCommand(command: String) {
        actions += "command $command"
    }

    override fun player(): FabricClientPlayer? {
        actions += "player"
        return observedPlayer
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
