package com.minekube.craftless.driver.runtime

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.bridge.hmc.HmcBridgeBackend
import com.minekube.craftless.protocol.ClientState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackendDriverSessionTest {
    @Test
    fun `runtime driver session delegates automation actions to a backend`() {
        val backend = RecordingDriverBackend()
        val session = BackendDriverSession(
            clientId = "alice",
            backend = backend,
        )

        assertEquals(ClientState.RUNNING, session.snapshot().state)

        val connected = session.connect(ConnectionTarget(host = "127.0.0.1", port = 25565))
        assertEquals(ClientState.CONNECTED, connected.state)
        assertEquals("connect alice 127.0.0.1:25565", backend.calls.single())

        assertTrue(session.actions().any { it.id == "player.move" })
        assertTrue(session.actions().any { it.id == "player.chat" })
        assertEquals("recording-backend", session.runtimeMetadata().driver)
        assertEquals("test-mappings", session.runtimeMetadata().mappings)

        val stopped = session.stop()
        assertEquals(ClientState.STOPPED, stopped.state)
        assertEquals("stop alice", backend.calls.last())
        assertTrue(session.events().any { it.type == DriverEventType.CLIENT_STOPPED })
    }

    @Test
    fun `runtime driver session invokes generic backend actions`() {
        val backend = RecordingDriverBackend(
            resultEventType = DriverEventType.MOVEMENT,
        )
        val session = BackendDriverSession(
            clientId = "alice",
            backend = backend,
        )

        val result = session.invoke(
            DriverActionInvocation(
                action = "player.move",
                arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(20)),
            )
        )

        assertEquals("player.move", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertEquals("action alice player.move forward=true ticks=20", backend.calls.single())
        assertTrue(session.events().any {
            it.type == DriverEventType.MOVEMENT &&
                it.message == "action alice player.move forward=true ticks=20"
        })

        val chat = session.invoke(
            DriverActionInvocation(
                action = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("chat through action")),
            )
        )

        assertEquals("player.chat", chat.action)
        assertEquals(DriverActionStatus.ACCEPTED, chat.status)
        assertEquals("action alice player.chat message=chat through action", backend.calls.last())
    }

    @Test
    fun `runtime driver session records error events for rejected actions`() {
        val backend = RecordingDriverBackend(
            rejectedAction = "player.fail",
        )
        val session = BackendDriverSession(
            clientId = "alice",
            backend = backend,
        )

        val result = session.invoke(
            DriverActionInvocation(
                action = "player.fail",
                arguments = mapOf("message" to JsonPrimitive("boom")),
            )
        )

        assertEquals("player.fail", result.action)
        assertEquals(DriverActionStatus.FAILED, result.status)
        assertTrue(session.events().any {
            it.type == DriverEventType.ERROR &&
                it.message == "rejected player.fail"
        })
    }

    @Test
    fun `runtime driver session records accepted events from driver result metadata`() {
        val backend = RecordingDriverBackend(
            resultEventType = DriverEventType.MOVEMENT,
        )
        val session = BackendDriverSession(
            clientId = "alice",
            backend = backend,
        )

        val result = session.invoke(
            DriverActionInvocation(
                action = "world.scan",
                arguments = mapOf("radius" to JsonPrimitive(4)),
            )
        )

        assertEquals("world.scan", result.action)
        assertEquals(DriverActionStatus.ACCEPTED, result.status)
        assertTrue(session.events().any {
            it.type == DriverEventType.MOVEMENT &&
                it.message == "action alice world.scan radius=4"
        })
    }

    @Test
    fun `hmc bridge backend adapts the temporary bridge to runtime backend actions`() {
        val backend = HmcBridgeDriverBackend(HmcBridgeBackend.dryRun())

        assertEquals(
            DriverBackendAction.CONNECT,
            backend.connect("alice", ConnectionTarget(host = "127.0.0.1", port = 25565)).action,
        )
        assertEquals(
            DriverActionStatus.ACCEPTED,
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("hello as action")),
                ),
            ).status,
        )
        assertEquals("craftless-driver-bridge", backend.runtimeMetadata("alice").driver)
        assertEquals("bridge-evidence", backend.runtimeMetadata("alice").permissionsFingerprint)
        assertEquals(
            setOf("forward", "backward", "left", "right", "ticks"),
            backend.actions("alice").single { it.id == "player.move" }.arguments.keys,
        )
        assertFailsWith<IllegalArgumentException> {
            backend.invoke(
                "alice",
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("/server lobby")),
                ),
            )
        }
        assertEquals(DriverBackendAction.STOP, backend.stop("alice").action)
    }
}

private class RecordingDriverBackend(
    private val rejectedAction: String? = null,
    private val resultEventType: DriverEventType? = null,
) : DriverBackend {
    val calls = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        calls += "connect $clientId ${target.host}:${target.port}"
        return DriverBackendResult(DriverBackendAction.CONNECT, "connected")
    }

    override fun stop(clientId: String): DriverBackendResult {
        calls += "stop $clientId"
        return DriverBackendResult(DriverBackendAction.STOP, "stopped")
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        listOf(
            testPlayerMoveActionDescriptor(),
            testPlayerChatActionDescriptor(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = "test-loader",
            driver = "recording-backend",
            driverVersion = "test-driver",
            mappings = "test-mappings",
            installedModsFingerprint = "mods-test",
            registryFingerprint = "registries-test",
            serverFeatureFingerprint = "features-test",
            permissionsFingerprint = "permissions-test",
        )

    override fun invoke(clientId: String, invocation: DriverActionInvocation): DriverActionResult {
        if (invocation.action == rejectedAction) {
            return DriverActionResult(invocation.action, DriverActionStatus.FAILED, "rejected ${invocation.action}")
        }
        val message = "action $clientId ${invocation.action} ${
            invocation.arguments.entries.joinToString(" ") { "${it.key}=${it.value.jsonPrimitive.content}" }
        }"
        calls += message
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = message,
            eventType = resultEventType,
        )
    }
}

private fun testPlayerMoveActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.move",
        schemaVersion = "1",
        arguments = mapOf(
            "forward" to DriverActionArgument("boolean"),
            "backward" to DriverActionArgument("boolean"),
            "left" to DriverActionArgument("boolean"),
            "right" to DriverActionArgument("boolean"),
            "jump" to DriverActionArgument("boolean"),
            "sneak" to DriverActionArgument("boolean"),
            "sprint" to DriverActionArgument("boolean"),
            "ticks" to DriverActionArgument("integer"),
        ),
    )

private fun testPlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments = mapOf(
            "message" to DriverActionArgument("string", required = true),
        ),
    )
