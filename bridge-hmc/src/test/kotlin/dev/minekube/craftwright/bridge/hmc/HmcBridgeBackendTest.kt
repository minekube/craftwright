package dev.minekube.craftwright.bridge.hmc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HmcBridgeBackendTest {
    @Test
    fun `bridge maps craftwright chat action without exposing hmc command names`() {
        val backend = HmcBridgeBackend.dryRun()
        val result = backend.chat("alice", "hello")

        assertEquals(ClientAction.CHAT, result.action)
        assertFalse(result.publicDescription.contains("hmc", ignoreCase = true))
        assertFalse(result.publicDescription.contains("specifics", ignoreCase = true))
        assertTrue(result.internalCommand.redacted().contains("<internal bridge command>"))
    }

    @Test
    fun `bridge supports required first milestone actions`() {
        val backend = HmcBridgeBackend.dryRun()

        assertEquals(ClientAction.CONNECT, backend.connect("alice", "127.0.0.1:25567").action)
        assertEquals(ClientAction.MOVE, backend.move("alice", MoveIntent.FORWARD, ticks = 20).action)
        assertEquals(ClientAction.JUMP, backend.jump("alice").action)
        assertEquals(ClientAction.LOOK, backend.look("alice", yaw = 90.0, pitch = 0.0).action)
    }
}
