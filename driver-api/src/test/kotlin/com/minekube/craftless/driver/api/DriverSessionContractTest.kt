package com.minekube.craftless.driver.api

import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DriverSessionContractTest {
    @Test
    fun `driver session exposes lifecycle and generic action methods only`() {
        val methodNames =
            DriverSession::class.java
                .declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet()

        assertEquals(
            setOf(
                "getClientId",
                "snapshot",
                "connect",
                "actions",
                "runtimeMetadata",
                "runtimeGraph",
                "operationAdapters",
                "invoke",
                "stop",
                "events",
            ),
            methodNames,
        )
        assertTrue(
            methodNames.none { name ->
                name in
                    setOf(
                        "sendChat",
                        "player",
                        "inventory",
                        "world",
                        "raycast",
                    )
            },
        )
    }

    @Test
    fun `driver session derives actions from runtime graph operations by default`() {
        val session = GraphOnlyDriverSession()

        val actions = session.actions()

        assertEquals(listOf("inventory.query"), actions.map { action -> action.id })
        val action = actions.single()
        assertEquals(DriverActionSource.RUNTIME_PROBE, action.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, action.availability)
        assertEquals("client-not-connected", action.availabilityReason)
        val filter = action.arguments.getValue("filter")
        val data = action.result.properties.getValue("data")

        assertEquals("object", filter.type)
        assertEquals(true, filter.required)
        assertEquals("string", filter.properties.getValue("item").type)
        assertEquals("object", data.type)
        assertEquals("array", data.properties.getValue("items").type)
    }

    @Test
    fun `driver action descriptors reject invalid argument metadata`() {
        listOf(
            "",
            "Player",
            "player.input",
            "player/input",
            "player:input",
            "minecraft-command",
            "--message",
            "_message",
            "message_",
        ).forEach { argumentName ->
            assertFailsWith<IllegalArgumentException> {
                DriverActionDescriptor(
                    id = "player.move",
                    schemaVersion = "1",
                    arguments = mapOf(argumentName to DriverActionArgument("boolean")),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            DriverActionDescriptor(
                id = "player.move",
                schemaVersion = "1",
                arguments = mapOf("forward" to DriverActionArgument("")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DriverActionArgument("minecraft-block-pos")
        }
    }

    @Test
    fun `driver action descriptors reject invalid action ids`() {
        listOf(
            "player",
            "Player.move",
            "player/move",
            "player:move",
            "minecraft.player.move",
            ".move",
            "player.",
        ).forEach { actionId ->
            assertFailsWith<IllegalArgumentException> {
                DriverActionDescriptor(
                    id = actionId,
                    schemaVersion = "1",
                )
            }
        }
    }

    @Test
    fun `driver action descriptors carry discovery source and availability`() {
        val descriptor =
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
                source = DriverActionSource.RUNTIME_PROBE,
                availability = DriverActionAvailability.UNAVAILABLE,
                availabilityReason = "client-not-connected",
            )

        assertEquals(DriverActionSource.RUNTIME_PROBE, descriptor.source)
        assertEquals(DriverActionAvailability.UNAVAILABLE, descriptor.availability)
        assertEquals("client-not-connected", descriptor.availabilityReason)
    }

    @Test
    fun `unavailable driver action descriptors require machine readable availability reason`() {
        assertFailsWith<IllegalArgumentException> {
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
                source = DriverActionSource.RUNTIME_PROBE,
                availability = DriverActionAvailability.UNAVAILABLE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
                source = DriverActionSource.RUNTIME_PROBE,
                availability = DriverActionAvailability.UNAVAILABLE,
                availabilityReason = "client is not connected",
            )
        }
    }

    @Test
    fun `driver action invocations reject invalid action metadata`() {
        listOf(
            "",
            "player",
            "Player.move",
            "player/move",
            "minecraft.player.move",
            ".move",
            "player.",
        ).forEach { actionId ->
            assertFailsWith<IllegalArgumentException> {
                DriverActionInvocation(action = actionId)
            }
        }

        listOf(
            "",
            "Player",
            "player.input",
            "player/input",
            "player:input",
            "minecraft-command",
            "--message",
            "_message",
            "message_",
        ).forEach { argumentName ->
            assertFailsWith<IllegalArgumentException> {
                DriverActionInvocation(
                    action = "player.move",
                    arguments = mapOf(argumentName to JsonPrimitive(true)),
                )
            }
        }
    }

    @Test
    fun `driver action results reject invalid action ids`() {
        listOf(
            "",
            "player",
            "Player.move",
            "player/move",
            "minecraft.player.move",
            ".move",
            "player.",
        ).forEach { actionId ->
            assertFailsWith<IllegalArgumentException> {
                DriverActionResult(
                    action = actionId,
                    status = DriverActionStatus.ACCEPTED,
                )
            }
        }
    }

    @Test
    fun `driver action results carry generic json payloads`() {
        val result =
            DriverActionResult(
                action = "player.raycast",
                status = DriverActionStatus.ACCEPTED,
                data =
                    buildJsonObject {
                        put("hit", true)
                        put("target-kind", "block")
                    },
            )

        assertEquals(true, result.data["hit"]?.jsonPrimitive?.boolean)
        assertEquals("block", result.data["target-kind"]?.jsonPrimitive?.content)
    }

    @Test
    fun `driver action results do not carry static event type metadata`() {
        val fieldNames =
            DriverActionResult::class.java
                .declaredFields
                .filterNot { field -> field.isSynthetic }
                .map { field -> field.name }
                .toSet()

        assertTrue("eventType" !in fieldNames, fieldNames.toString())
    }

    @Test
    fun `driver event types do not expose static gameplay categories`() {
        val eventTypes = DriverEventType.entries.map { type -> type.name }.toSet()

        assertEquals(
            setOf("CLIENT_CREATED", "CLIENT_CONNECTED", "CLIENT_STOPPED", "ERROR"),
            eventTypes,
        )
    }

    @Test
    fun `driver runtime metadata rejects non craftless public driver names`() {
        listOf(
            "",
            "recording-backend",
            "headlessmc",
            "hmc-specifics",
            "minecraft-launcher",
        ).forEach { driverName ->
            assertFailsWith<IllegalArgumentException> {
                DriverRuntimeMetadata(driver = driverName)
            }
        }
    }
}

private class GraphOnlyDriverSession : DriverSession {
    override val clientId: String = "graph-only"

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun runtimeMetadata(): DriverRuntimeMetadata = DriverRuntimeMetadata(driver = "craftless-test")

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("inventory", RuntimeAvailability.unavailable("client-not-connected"))),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "inventory.query",
                        resource = "inventory",
                        adapter = "test.inventory-query",
                        arguments =
                            mapOf(
                                "filter" to
                                    RuntimeSchema(
                                        type = "object",
                                        required = true,
                                        properties = mapOf("item" to RuntimeSchema("string")),
                                    ),
                            ),
                        result =
                            RuntimeSchema(
                                type = "object",
                                properties =
                                    mapOf(
                                        "items" to RuntimeSchema(type = "array", items = RuntimeSchema("object")),
                                    ),
                            ),
                        availability = RuntimeAvailability.unavailable("client-not-connected"),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.UNSUPPORTED)

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}
