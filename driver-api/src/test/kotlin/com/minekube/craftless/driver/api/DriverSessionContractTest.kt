package com.minekube.craftless.driver.api

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
