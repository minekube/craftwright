package com.minekube.craftwright.driver.runtime

import com.minekube.craftwright.driver.api.ChatCommand
import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverCapabilityDescriptor
import com.minekube.craftwright.driver.api.DriverCapabilityInvocation
import com.minekube.craftwright.driver.api.DriverCapabilityResult
import com.minekube.craftwright.driver.api.DriverCapabilityStatus
import com.minekube.craftwright.driver.api.DriverEventType
import com.minekube.craftwright.driver.api.DriverRuntimeMetadata
import com.minekube.craftwright.driver.api.PlayerPosition
import com.minekube.craftwright.bridge.hmc.HmcBridgeBackend
import com.minekube.craftwright.protocol.ClientState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
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
        assertTrue(session.capabilities().any { it.id == "player.move" })
        assertTrue(session.capabilities().any { it.id == "player.chat" })
        assertEquals("recording-backend", session.runtimeMetadata().driver)
        assertEquals("test-mappings", session.runtimeMetadata().mappings)

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
                arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(20)),
            )
        )

        assertEquals("player.move", result.capability)
        assertEquals(DriverCapabilityStatus.ACCEPTED, result.status)
        assertEquals("capability alice player.move forward=true ticks=20", backend.calls.single())

        val chat = session.invoke(
            DriverCapabilityInvocation(
                capability = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("chat through action")),
            )
        )

        assertEquals("player.chat", chat.capability)
        assertEquals(DriverCapabilityStatus.ACCEPTED, chat.status)
        assertEquals("capability alice player.chat message=chat through action", backend.calls.last())
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
        assertEquals(
            DriverCapabilityStatus.ACCEPTED,
            backend.invoke(
                "alice",
                DriverCapabilityInvocation(
                    capability = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("hello as action")),
                ),
            ).status,
        )
        assertEquals("craftwright-driver-bridge", backend.runtimeMetadata("alice").driver)
        assertEquals("bridge-evidence", backend.runtimeMetadata("alice").permissionsFingerprint)
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

    override fun capabilities(clientId: String): List<DriverCapabilityDescriptor> =
        listOf(
            DriverCapabilityDescriptor.playerMove(),
            DriverCapabilityDescriptor.playerChat(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = "test-loader",
            driver = "recording-backend",
            driverVersion = "test-driver",
            mappings = "test-mappings",
            installedModsFingerprint = "mods-test",
            registryFingerprint = "registries-test",
            serverFeatureFingerprint = "features-test",
            permissionsFingerprint = "permissions-test",
        )

    override fun invoke(clientId: String, invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        calls += "capability $clientId ${invocation.capability} ${invocation.arguments.entries.joinToString(" ") { "${it.key}=${it.value.jsonPrimitive.content}" }}"
        return DriverCapabilityResult(invocation.capability, DriverCapabilityStatus.ACCEPTED)
    }
}
