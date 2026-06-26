package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.io.IOException
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
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                    "POST /clients/fabric-smoke:run",
                ),
                server.requests.take(19),
            )
            assertEquals(
                listOf(
                    "inventory.query",
                    "world.block.query",
                    "navigation.plan",
                    "navigation.follow",
                    "player.query",
                    "player.look",
                    "player.raycast",
                    "world.block.break",
                    "navigation.plan",
                    "navigation.follow",
                    "entity.query",
                    "inventory.query",
                    "inventory.equip",
                    "inventory.query",
                    "entity.query",
                ),
                result.actionLog.map { it.action },
            )
            assertTrue(server.requestBodies.any { it.contains(""""category":"log"""") })
            assertTrue(server.requestBodies.any { it.contains(""""kind":"block"""") })
            assertTrue(server.requestBodies.any { it.contains(""""x":12""") })
            assertTrue(server.requestBodies.any { it.contains(""""yaw"""") })
            assertTrue(server.requestBodies.any { it.contains(""""pitch"""") })
            assertTrue(server.requestBodies.any { it.contains("player.raycast") })
            assertTrue(server.requestBodies.any { it.contains("world.block.break") })
            assertTrue(server.requestBodies.any { it.contains("inventory.equip") })
            assertTrue(server.requestBodies.any { it.contains(""""slot":0""") })
            assertTrue(server.requestBodies.any { it.contains(""""ticks":80""") })
            assertTrue(
                server.requestBodies.any {
                    it.contains(
                        """"target":{"handle":"world.block:12:65:-4","position":{"x":12,"y":65,"z":-4}}""",
                    )
                },
            )
            assertTrue(server.requestBodies.none { it.contains("task.survival") })
        }

    @Test
    fun `runner prefers lower public material targets for collection reachability`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    blockQueryResponses = listOf(layeredLogBlockQueryResponse),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(
                server.requestBodies.any {
                    it.contains(
                        """"target":{"handle":"world.block:13:63:-5","position":{"x":13,"y":63,"z":-5}}""",
                    )
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner navigates to observed public material drop before inventory proof`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    entityQueryResponse =
                        """
                        {
                          "action": "entity.query",
                          "status": "ACCEPTED",
                          "data": {
                            "entities": [
                              {
                                "handle": "entity.handle-42",
                                "label": "Oak Log",
                                "category": "object",
                                "distance": 3.0,
                                "position": {"x": 14.5, "y": 64.0, "z": -6.5}
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("entity.query"))
            assertTrue(server.requestBodies.any { it.contains(""""x":14.5""") })
            assertTrue(server.requestBodies.any { it.contains(""""z":-6.5""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner retries bounded public pickup evidence before blocking`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    inventoryResponses =
                        listOf(
                            """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}""",
                            """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}""",
                            """
                            {
                              "action": "inventory.query",
                              "status": "ACCEPTED",
                              "data": {
                                "selected-slot": 0,
                                "slots": [
                                  {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Log"}
                                ]
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 2)
            assertTrue(result.actionLog.map { it.action }.count { it == "inventory.query" } >= 3)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner invokes generic entity attack from discovered public handle when available`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponse =
                        """
                        {
                          "action": "entity.query",
                          "status": "ACCEPTED",
                          "data": {
                            "entities": [
                              {
                                "handle": "entity.handle-42",
                                "label": "Cow",
                                "category": "passive",
                                "alive": true,
                                "distance": 3.0,
                                "position": {"x": 14.5, "y": 64.0, "z": -6.5}
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("entity.attack"))
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertTrue(server.requestBodies.any { it.contains(""""max-distance":4.5""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner invokes targetable block interact when generated descriptor supports target`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses = listOf(logBlockQueryResponse, placementSupportBlockQueryResponse),
                    blockInteractResponse =
                        """
                        {
                          "action": "world.block.interact",
                          "status": "ACCEPTED",
                          "data": {
                            "accepted": true,
                            "changed": true,
                            "handle": "world.block:11:64:-4",
                            "adjacent-handle": "world.block:11:65:-4"
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("world.block.interact"))
            assertTrue(server.requestBodies.any { it.contains(""""category":"block"""") })
            assertTrue(
                server.requestBodies.any {
                    it.contains("world.block.interact") &&
                        it.contains(""""target":{"handle":"world.block:11:64:-4","position":{"x":11,"y":64,"z":-4}}""") &&
                        it.contains(""""side":"up"""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner reports insufficient public evidence when block break does not change target state`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    blockBreakResponse =
                        """{"action":"world.block.break","status":"ACCEPTED","data":{"hit":true,"target-kind":"block","started":true,"changed":false}}""",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:world.block.break.changed", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("world.block.break"))
            assertFalse(result.actionLog.map { it.action }.contains("inventory.equip"))
            assertTrue(server.requestBodies.any { it.contains(""""ticks":80""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner reports insufficient public evidence when break does not change inventory`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    finalInventoryResponse = """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}""",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:inventory.query.log", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("world.block.break"))
            assertTrue(result.actionLog.map { it.action }.contains("inventory.query"))
            assertTrue(result.actionLog.map { it.action }.contains("entity.query"))
            assertFalse(server.requestBodies.any { it.contains("task.survival") })
        }

    @Test
    fun `runner reports insufficient public evidence when equip does not select collected material`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    finalInventoryResponse =
                        """
                        {
                          "action": "inventory.query",
                          "status": "ACCEPTED",
                          "data": {
                            "selected-slot": 1,
                            "slots": [
                              {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Log"}
                            ]
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:inventory.equip.selected-slot", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("inventory.equip"))
            assertTrue(result.actionLog.map { it.action }.contains("entity.query"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner reports insufficient public evidence when no log block is projected`() =
        runBlocking {
            val artifactsDir = createTempDirectory(prefix = "craftless-public-agent-blocked-test")
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    blockQueryResponses = List(5) { EMPTY_BLOCK_QUERY_RESPONSE },
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce(artifactsDir = artifactsDir)

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:world.block.query.log", result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.query" } > 1)
            assertTrue(result.actionLog.map { it.action }.contains("navigation.plan"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
            val gameplay = Files.readString(artifactsDir.resolve("public-agent-gameplay-results.jsonl"))
            assertTrue(gameplay.contains("public-agent-action"))
            assertTrue(gameplay.contains("world.block.query"))
            assertTrue(gameplay.contains("public-agent-blocked"))
        }

    @Test
    fun `runner explores with generic navigation when local material query is empty`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    blockQueryResponses = listOf(EMPTY_BLOCK_QUERY_RESPONSE, logBlockQueryResponse),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertEquals(
                listOf(
                    "inventory.query",
                    "world.block.query",
                    "player.query",
                    "navigation.plan",
                    "navigation.follow",
                    "world.block.query",
                    "navigation.plan",
                    "navigation.follow",
                    "player.query",
                    "player.look",
                    "player.raycast",
                    "world.block.break",
                    "navigation.plan",
                    "navigation.follow",
                    "entity.query",
                    "inventory.query",
                    "inventory.equip",
                    "inventory.query",
                    "entity.query",
                ),
                result.actionLog.map { it.action },
            )
            assertTrue(server.requestBodies.count { it.contains(""""category":"log"""") } >= 2)
            assertTrue(server.requestBodies.any { it.contains(""""x":35.0""") })
            assertFalse(server.requestBodies.any { it.contains(""""x":59.0""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner records blocked artifacts when generated action request fails`() =
        runBlocking {
            val artifactsDir = createTempDirectory(prefix = "craftless-public-agent-action-failure")
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    failingActionRequest = "navigation.follow",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce(artifactsDir = artifactsDir)

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("action-request-failed:navigation.follow", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("navigation.follow"))
            val gameplay = Files.readString(artifactsDir.resolve("public-agent-gameplay-results.jsonl"))
            assertTrue(gameplay.contains("navigation.follow"))
            assertTrue(gameplay.contains("action-request-failed:navigation.follow"))
            assertFalse(gameplay.contains("task.survival"))
        }

    @Test
    fun `runner reports missing generic primitive instead of using scenario shortcut`() =
        runBlocking {
            val server = RecordingCraftlessHttpServer(actions = listOf("entity.query", "inventory.query"))
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("missing-generic-primitive:inventory.equip", result.blocker)
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
                            "inventory.equip",
                            "navigation.plan",
                            "navigation.follow",
                            "player.query",
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
    private val actionArguments: Map<String, List<String>> = emptyMap(),
    private val blockQueryResponses: List<String> = listOf(logBlockQueryResponse),
    private val inventoryResponses: List<String>? = null,
    private val blockBreakResponse: String =
        """{"action":"world.block.break","status":"ACCEPTED","data":{"hit":true,"target-kind":"block","started":true,"changed":true}}""",
    private val blockInteractResponse: String =
        """{"action":"world.block.interact","status":"ACCEPTED","data":{"accepted":true,"changed":true}}""",
    private val entityQueryResponse: String = """{"action":"entity.query","status":"ACCEPTED","data":{"entities":[]}}""",
    private val finalInventoryResponse: String =
        """
        {
          "action": "inventory.query",
          "status": "ACCEPTED",
          "data": {
            "selected-slot": 0,
            "slots": [
              {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Log"}
            ]
          }
        }
        """.trimIndent(),
    private val failingActionRequest: String? = null,
) {
    val url = "http://craftless.test"
    val requests = mutableListOf<String>()
    val requestBodies = mutableListOf<String>()
    private var inventoryQueryCount = 0
    private var blockQueryCount = 0
    val http =
        HttpClient(
            MockEngine { request ->
                requests += "${request.method.value} ${request.url.encodedPath}"
                requestBodies += request.body.contentText()
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
            request.method == HttpMethod.Post && request.url.encodedPath.endsWith(":run") -> actionResponse()
            else -> """{"code":"not-found","message":"unexpected request"}"""
        }

    private fun actionResponse(): String {
        val body = requestBodies.lastOrNull().orEmpty()
        failingActionRequest?.let { failingAction ->
            if (body.contains(failingAction)) {
                throw IOException("simulated generated action request failure for $failingAction")
            }
        }
        return when {
            body.contains("inventory.query") -> inventoryQueryResponse()
            body.contains("world.block.query") -> blockQueryResponse()
            body.contains("navigation.plan") ->
                """{"action":"navigation.plan","status":"ACCEPTED","data":{"plan-id":"navigation.plan.public-agent.0001","state":"pending"}}"""
            body.contains("navigation.follow") ->
                """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"running"}}"""
            body.contains("player.query") ->
                """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":11.0,"y":65.0,"z":-3.0}}}"""
            body.contains("player.look") ->
                """{"action":"player.look","status":"ACCEPTED"}"""
            body.contains("player.raycast") ->
                """{"action":"player.raycast","status":"ACCEPTED","data":{"hit":true,"target-kind":"block"}}"""
            body.contains("world.block.break") -> blockBreakResponse
            body.contains("world.block.interact") -> blockInteractResponse
            body.contains("inventory.equip") -> """{"action":"inventory.equip","status":"ACCEPTED"}"""
            body.contains("entity.attack") ->
                """{"action":"entity.attack","status":"ACCEPTED","data":{"hit":true,"handle":"entity.handle-42"}}"""
            body.contains("entity.query") -> entityQueryResponse
            else -> """{"action":"unknown","status":"UNSUPPORTED","message":"unexpected action"}"""
        }
    }

    private fun inventoryQueryResponse(): String {
        inventoryQueryCount += 1
        inventoryResponses?.let { responses ->
            return responses.getOrElse(inventoryQueryCount - 1) { responses.last() }
        }
        return if (inventoryQueryCount == 1) {
            """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}"""
        } else {
            finalInventoryResponse
        }
    }

    private fun blockQueryResponse(): String {
        blockQueryCount += 1
        return blockQueryResponses.getOrElse(blockQueryCount - 1) { blockQueryResponses.last() }
    }

    private fun actionsJson(): String =
        actions.joinToString(prefix = "[", postfix = "]") { action ->
            val args =
                actionArguments
                    .getOrDefault(action, emptyList())
                    .joinToString(prefix = "{", postfix = "}") { argument ->
                        """"$argument":{"type":"object","required":false}"""
                    }
            """{"id":"$action","schemaVersion":"1","args":$args,"result":{"properties":{},"required":[]},"source":"runtime-probe","availability":"available"}"""
        }

    private fun OutgoingContent.contentText(): String =
        when (this) {
            is TextContent -> text
            is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
            else -> ""
        }
}

private const val EMPTY_BLOCK_QUERY_RESPONSE =
    """{"action":"world.block.query","status":"ACCEPTED","data":{"count":0,"blocks":[]}}"""

private val logBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:12:65:-4",
            "category": "log",
            "distance": 5.0,
            "position": {"x": 12, "y": 65, "z": -4}
          }
        ]
      }
    }
    """.trimIndent()

private val layeredLogBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 2,
        "blocks": [
          {
            "handle": "world.block:12:70:-4",
            "category": "log",
            "distance": 4.0,
            "position": {"x": 12, "y": 70, "z": -4}
          },
          {
            "handle": "world.block:13:63:-5",
            "category": "log",
            "distance": 6.0,
            "position": {"x": 13, "y": 63, "z": -5}
          }
        ]
      }
    }
    """.trimIndent()

private val placementSupportBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:11:64:-4",
            "category": "block",
            "distance": 2.0,
            "position": {"x": 11, "y": 64, "z": -4}
          }
        ]
      }
    }
    """.trimIndent()

private fun List<String>.anyScenarioShortcut(): Boolean =
    any { body ->
        scenarioShortcutNames.any(body::contains)
    }

private val scenarioShortcutNames =
    listOf(
        "task.survival",
        "find.tree",
        "mine.log",
        "collect.wood",
        "craft.sword",
        "kill.cow",
    )

private fun completeActionCatalog(): List<String> =
    listOf(
        "entity.query",
        "inventory.query",
        "inventory.equip",
        "navigation.plan",
        "navigation.follow",
        "player.query",
        "player.look",
        "player.raycast",
        "world.block.query",
        "world.block.break",
    )
