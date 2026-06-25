package com.minekube.craftwright.driver.api

import com.minekube.craftwright.protocol.ClientState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriverSessionContractTest {
    @Test
    fun `fake driver session exposes the minimum automation contract`() {
        val session = FakeDriverSession(
            clientId = "alice",
            profileName = "Alice",
        )

        assertEquals(ClientState.RUNNING, session.snapshot().state)

        val connected = session.connect(ConnectionTarget(host = "localhost", port = 25565))
        assertEquals(ClientState.CONNECTED, connected.state)

        val chat = session.sendChat(ChatCommand("hello from driver"))
        assertEquals(DriverEventType.CHAT, chat.type)
        assertEquals("hello from driver", chat.message)

        val player = session.player()
        assertEquals("alice", player.id)
        assertEquals("Alice", player.name)
        assertEquals(ClientState.CONNECTED, player.state)
        assertEquals(PlayerPosition(0.0, 0.0, 0.0), player.position)

        val capability = session.invoke(
            DriverCapabilityInvocation(
                capability = "player.move",
                arguments = mapOf("forward" to "true", "ticks" to "20"),
            )
        )
        assertEquals("player.move", capability.capability)
        assertEquals(DriverCapabilityStatus.ACCEPTED, capability.status)

        val stopped = session.stop()
        assertEquals(ClientState.STOPPED, stopped.state)
        assertTrue(session.events().any { it.type == DriverEventType.CLIENT_STOPPED })
    }
}
