package dev.minekube.craftwright.daemon

import dev.minekube.craftwright.driver.api.ChatCommand
import dev.minekube.craftwright.driver.api.ConnectionTarget
import dev.minekube.craftwright.driver.api.DriverEventType
import dev.minekube.craftwright.driver.runtime.BackendDriverSession
import dev.minekube.craftwright.driver.runtime.DriverBackend
import dev.minekube.craftwright.driver.runtime.DriverBackendAction
import dev.minekube.craftwright.driver.runtime.DriverBackendResult
import dev.minekube.craftwright.protocol.ClientState
import dev.minekube.craftwright.protocol.CreateClientRequest
import dev.minekube.craftwright.protocol.Loader
import dev.minekube.craftwright.protocol.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientSessionServiceTest {
    @Test
    fun `offline session creates running client with generated api route`() {
        val service = ClientSessionService.inMemory()
        val client = service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        assertEquals("alice", client.id)
        assertEquals(ClientState.RUNNING, client.state)
        assertEquals("Alice", client.profile.name)
        assertEquals("/clients/alice/events", service.routesFor("alice").first { it.path.endsWith("/events") }.path)
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/player/sendChat" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/connection/connect" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/player/position" })
    }

    @Test
    fun `minecraft usernames longer than sixteen characters are rejected`() {
        val service = ClientSessionService.inMemory()

        val result = runCatching {
            service.createClient(
                CreateClientRequest(
                    id = "too-long",
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("CraftwrightApiBotTooLong"),
                )
            )
        }

        assertTrue(result.isFailure)
        assertEquals("offline profile name must be 16 characters or fewer", result.exceptionOrNull()?.message)
    }

    @Test
    fun `created clients expose a driver session contract`() {
        val service = ClientSessionService.inMemory()
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        val driver = service.driverFor("alice")
        assertEquals(ClientState.RUNNING, driver.snapshot().state)
        assertEquals(ClientState.CONNECTED, driver.connect(ConnectionTarget("localhost", 25565)).state)
        assertEquals(DriverEventType.CHAT, driver.sendChat(ChatCommand("from driver")).type)
        assertEquals("Alice", driver.player().name)
    }

    @Test
    fun `session service can create clients with an injected runtime driver factory`() {
        val backend = RecordingDriverBackend()
        val service = ClientSessionService.inMemory { request ->
            BackendDriverSession(
                clientId = request.id,
                profileName = request.profile.name,
                backend = backend,
            )
        }

        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        service.connectClient("alice", ConnectionTarget("localhost", 25565))
        service.driverFor("alice").sendChat(ChatCommand("runtime route"))

        assertEquals(
            listOf("connect alice localhost:25565", "chat alice runtime route"),
            backend.calls,
        )
    }
}

private class RecordingDriverBackend : DriverBackend {
    val calls = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        calls += "connect $clientId ${target.host}:${target.port}"
        return DriverBackendResult(DriverBackendAction.CONNECT)
    }

    override fun sendChat(clientId: String, command: ChatCommand): DriverBackendResult {
        calls += "chat $clientId ${command.message}"
        return DriverBackendResult(DriverBackendAction.CHAT, command.message)
    }

    override fun stop(clientId: String): DriverBackendResult {
        calls += "stop $clientId"
        return DriverBackendResult(DriverBackendAction.STOP)
    }
}
