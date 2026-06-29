package com.minekube.craftless.testkit

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ClientState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FakeDriverSessionTest {
    @Test
    fun `fake driver session exercises the minimum automation contract`() {
        val session =
            FakeDriverSession(
                clientId = "alice",
            )

        assertEquals(ClientState.RUNNING, session.snapshot().state)

        val connected = session.connect(ConnectionTarget(host = "localhost", port = 25565))
        assertEquals(ClientState.CONNECTED, connected.state)

        assertTrue(DriverSession::class.java.methods.none { it.name == "sendChat" })
        assertTrue(DriverSession::class.java.methods.none { it.name == "capabilities" })
        assertTrue(DriverSession::class.java.methods.none { it.name == "player" })
        assertTrue(DriverActionDescriptor::class.java.methods.none { it.name.startsWith("player") })
        assertTrue(
            DriverActionDescriptor::class.java.declaredClasses
                .flatMap { it.methods.toList() }
                .none { it.name.startsWith("player") },
        )

        val actions = session.actions()
        val graphOperationIds =
            session
                .runtimeGraph()
                .operations
                .map { operation -> operation.id }
                .sorted()
        val actionIds = actions.map { action -> action.id }.sorted()
        assertEquals(graphOperationIds, actionIds)
        assertTrue(actions.all { it.source == DriverActionSource.RUNTIME_PROBE })
        val moveAction = actions.single { it.id == "player.move" }
        val chatActionDescriptor = actions.single { it.id == "player.chat" }
        assertEquals("1", moveAction.schemaVersion)
        assertEquals("1", chatActionDescriptor.schemaVersion)
        assertEquals(listOf("action", "status"), moveAction.result.required)
        assertEquals("string", moveAction.result.properties["action"]?.type)
        assertEquals("string", moveAction.result.properties["status"]?.type)
        assertEquals("string", moveAction.result.properties["message"]?.type)

        val runtime = session.runtimeMetadata()
        assertEquals("none", runtime.loaderVersion)
        assertEquals("craftless-fake", runtime.driver)
        assertEquals("0.1.0-SNAPSHOT", runtime.driverVersion)
        assertEquals("none", runtime.mappings)
        assertEquals("none", runtime.installedModsFingerprint)
        assertEquals("none", runtime.registryFingerprint)
        assertEquals("none", runtime.serverFeatureFingerprint)
        assertEquals("local-fake", runtime.permissionsFingerprint)

        val chatAction =
            session.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("hello through action")),
                ),
            )
        assertEquals("player.chat", chatAction.action)
        assertEquals(DriverActionStatus.ACCEPTED, chatAction.status)
        assertEquals("hello through action", chatAction.message)
        val missingChatMessage =
            session.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = emptyMap(),
                ),
            )
        val blankChatMessage =
            session.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("  ")),
                ),
            )
        val commandChatMessage =
            session.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("/server lobby")),
                ),
            )
        assertEquals(DriverActionStatus.FAILED, missingChatMessage.status)
        assertEquals("missing-message", missingChatMessage.message)
        assertEquals(JsonPrimitive(false), missingChatMessage.data["sent"])
        assertEquals(JsonPrimitive("missing-message"), missingChatMessage.data["reason"])
        assertEquals(DriverActionStatus.FAILED, blankChatMessage.status)
        assertEquals("blank-message", blankChatMessage.message)
        assertEquals(JsonPrimitive(false), blankChatMessage.data["sent"])
        assertEquals(JsonPrimitive("blank-message"), blankChatMessage.data["reason"])
        assertEquals(DriverActionStatus.FAILED, commandChatMessage.status)
        assertEquals("minecraft-command-rejected", commandChatMessage.message)
        assertEquals(JsonPrimitive(false), commandChatMessage.data["sent"])
        assertEquals(JsonPrimitive("minecraft-command-rejected"), commandChatMessage.data["reason"])

        val action =
            session.invoke(
                DriverActionInvocation(
                    action = "player.move",
                    arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(20)),
                ),
            )
        assertEquals("player.move", action.action)
        assertEquals(DriverActionStatus.ACCEPTED, action.status)
        assertEquals("accepted player.move for alice", action.message)
        val eventCountBeforeInvalidTicks = session.events().size
        val invalidTicks =
            session.invoke(
                DriverActionInvocation(
                    action = "player.move",
                    arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(0)),
                ),
            )
        assertEquals(DriverActionStatus.FAILED, invalidTicks.status)
        assertEquals("invalid-ticks", invalidTicks.message)
        assertEquals(JsonPrimitive(false), invalidTicks.data["moved"])
        assertEquals(JsonPrimitive("invalid-ticks"), invalidTicks.data["reason"])
        assertEquals(eventCountBeforeInvalidTicks, session.events().size)

        val unknown =
            session.invoke(
                DriverActionInvocation(
                    action = "player.fly",
                    arguments = emptyMap(),
                ),
            )
        assertEquals("player.fly", unknown.action)
        assertEquals(DriverActionStatus.UNSUPPORTED, unknown.status)
        assertTrue(
            session.events().any {
                it.type == DriverEventType.ERROR &&
                    it.message == "unsupported fake action player.fly"
            },
        )

        val stopped = session.stop()
        assertEquals(ClientState.STOPPED, stopped.state)
        assertTrue(session.events().any { it.type == DriverEventType.CLIENT_STOPPED })
    }

    @Test
    fun `fake driver exposes screenshot capture through runtime graph`() {
        val session = FakeDriverSession(clientId = "alice")
        val graph = session.runtimeGraph()

        assertTrue(graph.resources.any { resource -> resource.id == "media.screenshot" })
        val operation = graph.operations.single { operation -> operation.id == "media.screenshot.capture" }
        assertEquals("media.screenshot", operation.resource)
        assertEquals("media.screenshot", operation.adapter)
        assertEquals("object", operation.result.type)
        assertEquals(true, operation.result.required)
        assertEquals("string", operation.result.properties["artifact-id"]?.type)
        assertEquals("string", operation.result.properties["media-type"]?.type)
        assertEquals("integer", operation.result.properties["byte-size"]?.type)
        assertEquals("string", operation.result.properties["sha256"]?.type)
        assertEquals("integer", operation.result.properties["width"]?.type)
        assertEquals("integer", operation.result.properties["height"]?.type)
        assertEquals("string", operation.result.properties["created-at"]?.type)
        assertEquals("string", operation.result.properties["download-url"]?.type)

        val result =
            session.invoke(
                DriverActionInvocation(
                    action = "media.screenshot.capture",
                    arguments = emptyMap(),
                ),
            )

        assertEquals("media.screenshot.capture", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        val data: JsonObject = result.data
        assertEquals("screenshot-0001.png", data["artifact-id"]?.jsonPrimitive?.content)
        assertEquals("image/png", data["media-type"]?.jsonPrimitive?.content)
        assertEquals(68, data["byte-size"]?.jsonPrimitive?.int)
        assertNotNull(data["sha256"]?.jsonPrimitive?.content)
        assertEquals(1, data["width"]?.jsonPrimitive?.int)
        assertEquals(1, data["height"]?.jsonPrimitive?.int)
        assertEquals("2026-06-29T00:00:00Z", data["created-at"]?.jsonPrimitive?.content)
        assertEquals("/clients/alice/artifacts/screenshot-0001.png", data["download-url"]?.jsonPrimitive?.content)
    }
}
