package com.minekube.craftless.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiveEventModelsTest {
    @Test
    fun `live events validate craftless owned names and correlation metadata`() {
        val event =
            LiveEvent(
                id = "event:alice:0001",
                type = "player.chat",
                clientId = "alice",
                resourceId = "player",
                operationId = "player.chat",
                correlationId = "rpc:alice:0001",
                payload =
                    buildJsonObject {
                        put("message", "hello")
                    },
                timestamp = "2026-06-26T12:00:00Z",
            )

        assertEquals("event:alice:0001", event.id)
        assertEquals("player.chat", event.type)
        assertEquals("rpc:alice:0001", event.correlationId)

        listOf(
            { event.copy(type = "minecraft.player.chat") },
            { event.copy(clientId = "bad/client") },
            { event.copy(resourceId = "MinecraftClient") },
            { event.copy(operationId = "player/send-chat") },
            { event.copy(correlationId = "bad correlation") },
        ).forEach { factory ->
            assertFailsWith<IllegalArgumentException> { factory() }
        }
    }

    @Test
    fun `live event filters validate optional server side filters`() {
        val filter =
            LiveEventFilter(
                types = listOf("player.chat", "client.created"),
                clientId = "alice",
                resourceId = "player",
                operationId = "player.chat",
                correlationId = "rpc:alice:0001",
            )

        assertTrue(
            filter.matches(
                type = "player.chat",
                clientId = "alice",
                resourceId = "player",
                operationId = "player.chat",
                correlationId = "rpc:alice:0001",
            ),
        )
        assertTrue(!filter.matches(type = "player.move", clientId = "alice", resourceId = "player", operationId = "player.move"))

        assertFailsWith<IllegalArgumentException> { filter.copy(types = listOf("minecraft.client")) }
        assertFailsWith<IllegalArgumentException> { filter.copy(correlationId = "bad id") }
    }

    @Test
    fun `json rpc envelopes allow only generic control methods`() {
        val request =
            JsonRpcRequest(
                id = "rpc:alice:0001",
                method = JsonRpcMethod.INVOKE,
                params =
                    buildJsonObject {
                        put("action", "player.chat")
                    },
            )
        val response =
            JsonRpcResponse.result(
                id = request.id,
                result =
                    buildJsonObject {
                        put("accepted", true)
                    },
            )

        assertEquals("2.0", request.jsonrpc)
        assertEquals(JsonRpcMethod.INVOKE, request.method)
        assertEquals("rpc:alice:0001", response.id)

        assertFailsWith<IllegalArgumentException> {
            JsonRpcRequest(id = "bad id", method = JsonRpcMethod.QUERY)
        }
        assertFailsWith<IllegalArgumentException> {
            JsonRpcRequest(id = "rpc:alice:0002", method = "player.chat")
        }

        val encoded = Json.encodeToString(JsonRpcResponse.serializer(), response)
        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(encoded.contains("\"result\""))
    }
}
