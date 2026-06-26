package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.RuntimeAvailabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricNavigationDiscoveryTest {
    @Test
    fun `pathfinder presence is private evidence and public graph stays craftless owned`() {
        val graph =
            defaultFabricCapabilityDiscovery(
                probes =
                    listOf(
                        FabricNavigationDiscovery(classExists = { className ->
                            className == "baritone.api.BaritoneAPI"
                        }),
                    ),
            ).discover(navigationContext())

        assertEquals(listOf("navigation", "task"), graph.resources.map { it.id })
        assertTrue(graph.operations.any { it.id == "navigation.plan" })
        assertTrue(graph.operations.any { it.id == "navigation.follow" })
        assertTrue(graph.operations.any { it.id == "task.run" })
        assertEquals(
            RuntimeAvailabilityState.UNAVAILABLE,
            graph.operations
                .single { it.id == "navigation.plan" }
                .availability
                .state,
        )
        assertEquals(
            "adapter-unavailable",
            graph.operations
                .single { it.id == "navigation.plan" }
                .availability
                .reason,
        )
        assertFalse(graph.toString().contains("baritone", ignoreCase = true))
        assertFalse(graph.toString().contains("swarmbot", ignoreCase = true))
    }

    @Test
    fun `missing pathfinder records unavailable navigation with reason`() {
        val graph =
            defaultFabricCapabilityDiscovery(
                probes =
                    listOf(
                        FabricNavigationDiscovery(classExists = { false }),
                    ),
            ).discover(navigationContext())

        val navigation = graph.resources.single { it.id == "navigation" }
        val operation = graph.operations.single { it.id == "navigation.plan" }

        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, navigation.availability.state)
        assertEquals("pathfinder-unavailable", navigation.availability.reason)
        assertEquals(RuntimeAvailabilityState.UNAVAILABLE, operation.availability.state)
        assertEquals("pathfinder-unavailable", operation.availability.reason)
    }
}

private fun navigationContext(): FabricCapabilityProbeContext =
    FabricCapabilityProbeContext(
        clientId = "alice",
        modeId = "real-client",
        gateway = null,
    )
