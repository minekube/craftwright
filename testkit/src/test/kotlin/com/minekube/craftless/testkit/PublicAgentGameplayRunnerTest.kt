package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PublicAgentGameplayRunnerTest {
    @Test
    fun `runner fetches live client spec before invoking gameplay actions`() =
        runBlocking {
            val server = RecordingCraftlessHttpServer(actions = completeActionCatalog())
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertEquals(
                listOf(
                    "GET /openapi.json",
                    "GET /clients/fabric-smoke/openapi.json",
                    "GET /clients/fabric-smoke/actions",
                    "GET /clients/fabric-smoke/events:stream",
                    "POST /clients/fabric-smoke:run",
                ),
                server.requests.take(5),
            )
        }

    @Test
    fun `runner reports missing generic primitive instead of using scenario shortcut`() =
        runBlocking {
            val server = RecordingCraftlessHttpServer(actions = listOf("entity.query", "inventory.query"))
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("missing-generic-primitive:navigation.plan", result.blocker)
            assertFalse(server.requests.any { it.contains("task.survival") })
            assertFalse(server.requests.any { it.startsWith("POST ") })
        }
}

private class RecordingCraftlessHttpServer(
    private val actions: List<String>,
) {
    val url = "http://craftless.test"
    val requests = mutableListOf<String>()
    val http =
        HttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.encodedPath}"
                respond(
                    content = responseBody(request),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

    private fun responseBody(request: HttpRequestData): String =
        when {
            request.method == HttpMethod.Get && request.url.encodedPath == "/openapi.json" -> """{"openapi":"3.1.0"}"""
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/openapi.json") -> """{"openapi":"3.1.0"}"""
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/actions") -> actionsJson()
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/events:stream") -> "event: ready\ndata: {}\n\n"
            request.method == HttpMethod.Post && request.url.encodedPath.endsWith(":run") ->
                """{"action":"entity.query","status":"ACCEPTED"}"""
            else -> """{"code":"not-found","message":"unexpected request"}"""
        }

    private fun actionsJson(): String =
        actions.joinToString(prefix = "[", postfix = "]") { action ->
            """{"id":"$action","schemaVersion":"1","args":{},"result":{"properties":{},"required":[]},"source":"runtime-probe","availability":"available"}"""
        }
}

private fun completeActionCatalog(): List<String> =
    listOf(
        "entity.query",
        "inventory.query",
        "navigation.plan",
        "navigation.follow",
        "player.look",
        "player.raycast",
        "world.block.break",
    )
