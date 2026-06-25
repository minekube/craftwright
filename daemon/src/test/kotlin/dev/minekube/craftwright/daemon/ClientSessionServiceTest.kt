package dev.minekube.craftwright.daemon

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
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/actions/chat" })
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
}
