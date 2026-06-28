package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FabricRuntimeGraphTest {
    @Test
    fun `runtime graph composes resources and operations from fragments`() {
        val graph =
            fabricRuntimeGraph(
                clientId = "alice",
                fragments =
                    listOf(
                        FabricRuntimeGraphFragment(
                            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                        ),
                        FabricRuntimeGraphFragment(
                            operations =
                                listOf(
                                    RuntimeOperationNode(
                                        id = "player.query",
                                        resource = "player",
                                        adapter = "fabric.player-query",
                                        availability = RuntimeAvailability.available(),
                                    ),
                                ),
                        ),
                    ),
            )

        assertEquals("alice", graph.clientId)
        assertEquals(listOf("player"), graph.resources.map { it.id })
        assertEquals(listOf("player.query"), graph.operations.map { it.id })
        assertTrue(graph.fingerprint().startsWith("graph:"))
    }

    @Test
    fun `runtime graph composer preserves graph validation for duplicate nodes`() {
        assertFailsWith<IllegalArgumentException> {
            fabricRuntimeGraph(
                clientId = "alice",
                fragments =
                    listOf(
                        FabricRuntimeGraphFragment(
                            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                        ),
                        FabricRuntimeGraphFragment(
                            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
                        ),
                    ),
            )
        }
    }
}
