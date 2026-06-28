package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
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

    @Test
    fun `registry graph fragment exposes available registry resource and handles from metadata evidence`() {
        val metadata =
            DriverRuntimeMetadata
                .runtimeAdapter()
                .copy(registryFingerprint = "registries:abc123")

        val fragment =
            fabricRegistryGraphFragment(
                metadata = metadata,
                available = true,
            )

        assertEquals(listOf("registry"), fragment.resources.map { it.id })
        assertEquals(RuntimeAvailability.available(), fragment.resources.single().availability)
        assertEquals(
            listOf(
                "registry.block",
                "registry.effect",
                "registry.entity",
                "registry.event",
                "registry.item",
                "registry.screen",
            ),
            fragment.handles.map { it.id }.sorted(),
        )
        assertTrue(
            fragment.resources
                .single()
                .sourceEvidence
                .any { evidence -> evidence.kind == "registry" && evidence.fingerprint == "registries:abc123" },
        )
    }

    @Test
    fun `registry graph fragment reports unavailable registry when metadata has no discovery evidence`() {
        val metadata =
            DriverRuntimeMetadata
                .runtimeAdapter()
                .copy(registryFingerprint = "registries:not-discovered")

        val fragment =
            fabricRegistryGraphFragment(
                metadata = metadata,
                available = false,
            )

        val unavailable = RuntimeAvailability.unavailable("registry-not-discovered")
        assertEquals(unavailable, fragment.resources.single().availability)
        assertTrue(fragment.handles.isNotEmpty())
        assertTrue(fragment.handles.all { handle -> handle.availability == unavailable })
    }
}
