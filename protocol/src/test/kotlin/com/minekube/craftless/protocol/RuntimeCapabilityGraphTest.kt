package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RuntimeCapabilityGraphTest {
    @Test
    fun `graph validates resource operation handle and event ids`() {
        val graph =
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources =
                    listOf(
                        RuntimeResourceNode(
                            id = "inventory",
                            availability = RuntimeAvailability.available(),
                            sourceEvidence =
                                listOf(
                                    RuntimeSourceEvidence(
                                        kind = "registry",
                                        fingerprint = "sha256:test-registry",
                                    ),
                                ),
                        ),
                    ),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "inventory.query",
                            resource = "inventory",
                            adapter = "fabric-client-thread",
                            result = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                handles =
                    listOf(
                        RuntimeHandleNode(
                            id = "inventory.slot",
                            resource = "inventory",
                            schema = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                events =
                    listOf(
                        RuntimeEventNode(
                            id = "inventory.changed",
                            resource = "inventory",
                            payload = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )

        assertEquals("alice", graph.clientId)
        assertEquals(listOf("inventory"), graph.resources.map { it.id })
        assertEquals(listOf("inventory.query"), graph.operations.map { it.id })
        assertEquals(listOf("inventory.slot"), graph.handles.map { it.id })
        assertEquals(listOf("inventory.changed"), graph.events.map { it.id })
        assertEquals(
            "registry",
            graph.resources
                .single()
                .sourceEvidence
                .single()
                .kind,
        )
    }

    @Test
    fun `graph rejects duplicates and unavailable nodes without reasons`() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources =
                    listOf(
                        RuntimeResourceNode("inventory", RuntimeAvailability.available()),
                        RuntimeResourceNode("inventory", RuntimeAvailability.available()),
                    ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            RuntimeResourceNode(
                id = "inventory",
                availability = RuntimeAvailability(RuntimeAvailabilityState.UNAVAILABLE),
            )
        }
    }

    @Test
    fun `graph rejects public namespace leaks`() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeResourceNode("minecraft.inventory", RuntimeAvailability.available())
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeOperationNode(
                id = "fabric.inventory.query",
                resource = "inventory",
                adapter = "fabric-client-thread",
                result = RuntimeSchema.objectSchema(),
                availability = RuntimeAvailability.available(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeHandleNode(
                id = "inventory.yarn-slot",
                resource = "inventory",
                schema = RuntimeSchema.objectSchema(),
                availability = RuntimeAvailability.available(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeEventNode(
                id = "inventory.minecraft-changed",
                resource = "inventory",
                payload = RuntimeSchema.objectSchema(),
                availability = RuntimeAvailability.available(),
            )
        }
    }

    @Test
    fun `graph fingerprint is stable and changes with schema or availability`() {
        val baseline =
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources = listOf(RuntimeResourceNode("inventory", RuntimeAvailability.available())),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "inventory.query",
                            resource = "inventory",
                            adapter = "fabric-client-thread",
                            result = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )
        val reordered =
            baseline.copy(
                resources = listOf(RuntimeResourceNode("inventory", RuntimeAvailability.available())),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "inventory.query",
                            resource = "inventory",
                            adapter = "fabric-client-thread",
                            result = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )
        val unavailable =
            baseline.copy(
                resources = listOf(RuntimeResourceNode("inventory", RuntimeAvailability.available())),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "inventory.query",
                            resource = "inventory",
                            adapter = "fabric-client-thread",
                            result = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.unavailable("screen-open"),
                        ),
                    ),
            )

        assertEquals(baseline.fingerprint(), reordered.fingerprint())
        assertNotEquals(baseline.fingerprint(), unavailable.fingerprint())
    }

    @Test
    fun `graph fingerprint changes with private source evidence`() {
        val baseline =
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources =
                    listOf(
                        RuntimeResourceNode(
                            id = "inventory",
                            availability = RuntimeAvailability.available(),
                            sourceEvidence =
                                listOf(
                                    RuntimeSourceEvidence(
                                        kind = "registry",
                                        fingerprint = "sha256:one",
                                    ),
                                ),
                        ),
                    ),
            )
        val changed =
            baseline.copy(
                resources =
                    listOf(
                        RuntimeResourceNode(
                            id = "inventory",
                            availability = RuntimeAvailability.available(),
                            sourceEvidence =
                                listOf(
                                    RuntimeSourceEvidence(
                                        kind = "registry",
                                        fingerprint = "sha256:two",
                                    ),
                                ),
                        ),
                    ),
            )

        assertNotEquals(baseline.fingerprint(), changed.fingerprint())
        assertFailsWith<IllegalArgumentException> {
            RuntimeSourceEvidence(kind = "minecraft-registry", fingerprint = "sha256:one")
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeSourceEvidence(kind = "registry", fingerprint = "")
        }
    }
}
