package com.minekube.craftless.driver.api

import com.minekube.craftless.protocol.ClientState
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DriverSessionContractTest {
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

    @Test
    fun `fake driver session exposes the minimum automation contract`() {
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
        assertEquals("1", actions.single { it.id == "player.move" }.schemaVersion)
        assertEquals("1", actions.single { it.id == "player.chat" }.schemaVersion)

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
        assertFailsWith<IllegalArgumentException> {
            session.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("/server lobby")),
                ),
            )
        }

        val action =
            session.invoke(
                DriverActionInvocation(
                    action = "player.move",
                    arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(20)),
                ),
            )
        assertEquals("player.move", action.action)
        assertEquals(DriverActionStatus.ACCEPTED, action.status)
        assertTrue(
            session.events().any {
                it.type.name == "MOVEMENT" &&
                    it.message == "accepted player.move for alice"
            },
        )
        assertFailsWith<IllegalArgumentException> {
            session.invoke(
                DriverActionInvocation(
                    action = "player.move",
                    arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(0)),
                ),
            )
        }

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
}
