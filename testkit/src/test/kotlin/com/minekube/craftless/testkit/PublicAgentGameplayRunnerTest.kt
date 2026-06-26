package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                ),
                server.requests.take(7),
            )
            assertEquals(listOf("inventory.query", "world.block.query", "entity.query"), result.actionLog.map { it.action })
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

    @Test
    fun `runner requires generic block discovery before claiming gameplay can run`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        listOf(
                            "entity.query",
                            "inventory.query",
                            "navigation.plan",
                            "navigation.follow",
                            "player.look",
                            "player.raycast",
                            "world.block.break",
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("missing-generic-primitive:world.block.query", result.blocker)
            assertFalse(server.requests.any { it.startsWith("POST ") })
        }

    @Test
    fun `runner writes public agent action and state artifacts`() =
        runBlocking {
            val artifactsDir = createTempDirectory(prefix = "craftless-public-agent-test")
            val server = RecordingCraftlessHttpServer(actions = completeActionCatalog())
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce(artifactsDir = artifactsDir)

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            val gameplay = Files.readString(artifactsDir.resolve("public-agent-gameplay-results.jsonl"))
            val state = Files.readString(artifactsDir.resolve("public-agent-state.jsonl"))
            assertTrue(gameplay.contains("public-agent-action"))
            assertTrue(gameplay.contains("entity.query"))
            assertTrue(gameplay.contains("world.block.query"))
            assertTrue(gameplay.contains("inventory.query"))
            assertTrue(state.contains("public-agent-discovery"))
            assertTrue(state.contains("GET /clients/fabric-smoke/events:stream"))
            assertFalse(gameplay.contains("task.survival"))
        }

    @Test
    fun `runner config parses process external environment`() {
        val config =
            PublicAgentGameplayRunnerConfig.fromEnvironment(
                mapOf(
                    "CRAFTLESS_PUBLIC_AGENT_BASE_URL" to "http://127.0.0.1:18080",
                    "CRAFTLESS_PUBLIC_AGENT_CLIENT_ID" to "fabric-smoke",
                    "CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR" to "/tmp/craftless-public-agent-artifacts",
                ),
            )

        assertEquals("http://127.0.0.1:18080", config.baseUrl)
        assertEquals("fabric-smoke", config.clientId)
        assertEquals(Path.of("/tmp/craftless-public-agent-artifacts"), config.artifactsDir)
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
        "world.block.query",
        "world.block.break",
    )
