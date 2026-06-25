package dev.minekube.craftwright.driver.runtime

import dev.minekube.craftwright.driver.api.ChatCommand
import dev.minekube.craftwright.driver.api.ConnectionTarget
import dev.minekube.craftwright.driver.api.DriverCapabilityInvocation
import dev.minekube.craftwright.driver.api.DriverCapabilityResult
import dev.minekube.craftwright.driver.api.DriverCapabilityStatus
import dev.minekube.craftwright.driver.api.DriverEventType
import dev.minekube.craftwright.driver.api.PlayerPosition
import dev.minekube.craftwright.bridge.hmc.HmcBridgeBackend
import dev.minekube.craftwright.protocol.ClientState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackendDriverSessionTest {
    @Test
    fun `runtime driver session delegates automation actions to a backend`() {
        val backend = RecordingDriverBackend()
        val session = BackendDriverSession(
            clientId = "alice",
            profileName = "Alice",
            backend = backend,
        )

        assertEquals(ClientState.RUNNING, session.snapshot().state)

        val connected = session.connect(ConnectionTarget(host = "127.0.0.1", port = 25565))
        assertEquals(ClientState.CONNECTED, connected.state)
        assertEquals("connect alice 127.0.0.1:25565", backend.calls.single())

        val chat = session.sendChat(ChatCommand("hello runtime"))
        assertEquals(DriverEventType.CHAT, chat.type)
        assertEquals("hello runtime", chat.message)
        assertEquals("chat alice hello runtime", backend.calls.last())

        assertEquals("Alice", session.player().name)
        assertEquals(ClientState.CONNECTED, session.player().state)

        val stopped = session.stop()
        assertEquals(ClientState.STOPPED, stopped.state)
        assertEquals("stop alice", backend.calls.last())
        assertTrue(session.events().any { it.type == DriverEventType.CLIENT_STOPPED })
    }

    @Test
    fun `runtime driver session reads observed player state from backend`() {
        val backend = RecordingDriverBackend(
            observedPlayer = DriverBackendPlayer(
                name = "ObservedAlice",
                state = ClientState.CONNECTED,
                position = PlayerPosition(x = 12.5, y = 64.0, z = -8.25),
            )
        )
        val session = BackendDriverSession(
            clientId = "alice",
            profileName = "Alice",
            backend = backend,
        )

        val player = session.player()

        assertEquals("ObservedAlice", player.name)
        assertEquals(ClientState.CONNECTED, player.state)
        assertEquals(PlayerPosition(x = 12.5, y = 64.0, z = -8.25), player.position)
        assertEquals(listOf("player alice"), backend.calls)
    }

    @Test
    fun `runtime driver session invokes generic backend capabilities`() {
        val backend = RecordingDriverBackend()
        val session = BackendDriverSession(
            clientId = "alice",
            profileName = "Alice",
            backend = backend,
        )

        val result = session.invoke(
            DriverCapabilityInvocation(
                capability = "player.move",
                arguments = mapOf("forward" to "true", "ticks" to "20"),
            )
        )

        assertEquals("player.move", result.capability)
        assertEquals(DriverCapabilityStatus.ACCEPTED, result.status)
        assertEquals("capability alice player.move forward=true ticks=20", backend.calls.single())
    }

    @Test
    fun `hmc bridge backend adapts the temporary bridge to runtime backend actions`() {
        val backend = HmcBridgeDriverBackend(HmcBridgeBackend.dryRun())

        assertEquals(
            DriverBackendAction.CONNECT,
            backend.connect("alice", ConnectionTarget(host = "127.0.0.1", port = 25565)).action,
        )
        assertEquals(
            DriverBackendAction.CHAT,
            backend.sendChat("alice", ChatCommand("hello bridge")).action,
        )
        assertEquals(DriverBackendAction.STOP, backend.stop("alice").action)
    }
}

private class RecordingDriverBackend(
    private val observedPlayer: DriverBackendPlayer? = null,
) : DriverBackend {
    val calls = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        calls += "connect $clientId ${target.host}:${target.port}"
        return DriverBackendResult(DriverBackendAction.CONNECT, "connected")
    }

    override fun sendChat(clientId: String, command: ChatCommand): DriverBackendResult {
        calls += "chat $clientId ${command.message}"
        return DriverBackendResult(DriverBackendAction.CHAT, command.message)
    }

    override fun stop(clientId: String): DriverBackendResult {
        calls += "stop $clientId"
        return DriverBackendResult(DriverBackendAction.STOP, "stopped")
    }

    override fun player(clientId: String): DriverBackendPlayer? {
        calls += "player $clientId"
        return observedPlayer
    }

    override fun invoke(clientId: String, invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        calls += "capability $clientId ${invocation.capability} ${invocation.arguments.entries.joinToString(" ") { "${it.key}=${it.value}" }}"
        return DriverCapabilityResult(invocation.capability, DriverCapabilityStatus.ACCEPTED)
    }
}
