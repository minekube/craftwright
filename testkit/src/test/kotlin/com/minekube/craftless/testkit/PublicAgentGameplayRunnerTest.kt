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

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blockerWithActions())
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
                ),
                server.requests.take(18),
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
    fun `runner uses generated client openapi actions as authority over actions projection`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    projectedActions = emptyList(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blockerWithActions())
            assertTrue(server.requests.contains("GET /clients/fabric-smoke/openapi.json"))
            assertTrue(server.requests.contains("GET /clients/fabric-smoke/actions"))
            assertEquals("[]", result.actions)
            assertEquals(completeActionCatalog().toSet(), result.availableActions.toSet())
            assertTrue(result.actionLog.map { it.action }.contains("world.block.break"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
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
    fun `runner still uses public drop perception when block target pickup navigation fails`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    navigationFollowResponses =
                        listOf(
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                            """
                            {
                              "action": "navigation.follow",
                              "status": "FAILED",
                              "message": "navigation-did-not-start",
                              "data": {
                                "task-id": "task:navigation:public-agent",
                                "state": "failed"
                              }
                            }
                            """.trimIndent(),
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                        ),
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
            assertTrue(server.requestBodies.any { it.contains(""""x":14.5""") })
            assertTrue(server.requestBodies.any { it.contains(""""z":-6.5""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner uses generated player move toward material drop when pickup navigation fails`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "player.move",
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                        ),
                    playerQueryResponse =
                        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":10.0,"y":64.0,"z":-6.0}}}""",
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
                                "distance": 4.0,
                                "position": {"x": 14.5, "y": 64.0, "z": -6.5}
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    inventoryResponseAfterPlayerMove =
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
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(
                PublicAgentGameplayState.RAN,
                result.state,
                "${result.blocker}\n${result.actionLog.map { it.action }}\n${server.requestBodies.joinToString(separator = "\n")}",
            )
            assertTrue(result.actionLog.map { it.action }.contains("player.move"))
            assertTrue(server.requestBodies.any { it.contains(""""forward":true""") })
            assertTrue(server.requestBodies.any { it.contains(""""ticks"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner jumps during generated pickup fallback movement toward elevated material drop`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "player.move",
                    blockQueryResponses =
                        listOf(
                            """
                            {
                              "action": "world.block.query",
                              "status": "ACCEPTED",
                              "data": {
                                "count": 1,
                                "blocks": [
                                  {
                                    "handle": "world.block:20:74:-291",
                                    "category": "log",
                                    "distance": 3.3,
                                    "position": {"x": 20, "y": 74, "z": -291}
                                  }
                                ]
                              }
                            }
                            """.trimIndent(),
                        ),
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                        ),
                    playerQueryResponse =
                        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":20.8,"y":73.0,"z":-287.7}}}""",
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
                                "distance": 3.3,
                                "position": {"x": 20.6, "y": 74.0, "z": -290.9}
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    inventoryResponseAfterPlayerMove =
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
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(
                server.requestBodies.any { it.contains(""""player.move"""") && it.contains(""""jump":true""") },
                server.requestBodies.joinToString(separator = "\n"),
            )
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
    fun `runner keeps collecting recipe materials until public inventory count increases`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft"),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 2),
                            logCountInventoryResponse(count = 2),
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.break" } >= 2)
            assertTrue(result.actionLog.map { it.action }.count { it == "inventory.query" } >= 5)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner tries generated recipes with partial material evidence when later collection navigation fails`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft"),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            logCountInventoryResponse(count = 1),
                            craftedMaterialInventoryResponse,
                        ),
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                        ),
                    recipeQueryResponse = materialRecipeQueryResponse,
                    recipeCraftResponse = MATERIAL_RECIPE_CRAFT_RESPONSE,
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertTrue(result.actionLog.map { it.action }.contains("recipe.query"))
            assertTrue(result.actionLog.map { it.action }.contains("recipe.craft"))
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.break" } >= 2)
            assertFalse(result.blocker == "insufficient-public-evidence:inventory.query.recipe-material", result.blocker)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner invokes generic entity attack from discovered public handle when available`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
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
    fun `runner blocks when entity attack lacks public outcome evidence`() =
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

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:entity.attack.outcome", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("entity.attack"))
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 2)
            assertTrue(result.actionLog.map { it.action }.count { it == "inventory.query" } >= 3)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner pauses between unproven generated attack attempts`() =
        runBlocking {
            var pauses = 0
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner =
                PublicAgentGameplayRunner(
                    baseUrl = server.url,
                    clientId = "fabric-smoke",
                    http = server.http,
                    combatPause = { pauses += 1 },
                )

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blockerWithActions())
            assertEquals(1, pauses)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.attack" } >= 2)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner uses configured bounded generated attack attempts before blocking without outcome evidence`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(EMPTY_ENTITY_QUERY_RESPONSE) +
                            List(40) { aliveCowEntityQueryResponse },
                )
            val runner =
                PublicAgentGameplayRunner(
                    baseUrl = server.url,
                    clientId = "fabric-smoke",
                    http = server.http,
                    combatEvidenceAttempts = 7,
                )

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:entity.attack.outcome", result.blocker)
            assertEquals(7, result.actionLog.map { it.action }.count { it == "entity.attack" })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner refreshes public attack target position between unproven attacks`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            movedCowEntityQueryResponse,
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.attack" } >= 2)
            assertTrue(server.requestBodies.any { it.contains(""""x":24.0""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner keeps same public attack target while outcome is unproven`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            aliveCowAndCloserChickenEntityQueryResponse,
                            closeChickenAndCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.attack" } >= 2)
            assertFalse(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-7"}""")
                },
            )
            assertTrue(
                server.requestBodies.count {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                } >= 2,
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner does not renavigate to same public attack target while it remains reachable`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.attack" } >= 2)
            assertEquals(
                0,
                server.requestBodies.count { it.contains(""""position":{"x":14.5,"y":64.0,"z":-6.5}""") },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner repositions when refreshed public attack target moves out of reach`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            movedCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(server.requestBodies.any { it.contains(""""x":24.0""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner revalidates public attack target after generated attack misses`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("entity.attack", "player.move"),
                    entityAttackResponses =
                        listOf(
                            """{"action":"entity.attack","status":"ACCEPTED","data":{"hit":true,"handle":"entity.handle-42"}}""",
                            """{"action":"entity.attack","status":"FAILED","message":"entity-target-out-of-range","data":{"hit":false,"reason":"entity-target-out-of-range"}}""",
                            """{"action":"entity.attack","status":"ACCEPTED","data":{"hit":true,"handle":"entity.handle-42"}}""",
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            movedCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blockerWithActions())
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.attack" } >= 3)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 4)
            assertTrue(result.actionLog.map { it.action }.contains("player.query"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner navigates to vertically offset public attack targets`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        List(4) { EMPTY_ENTITY_QUERY_RESPONSE } +
                            listOf(
                                verticallyOffsetCowEntityQueryResponse,
                                EMPTY_ENTITY_QUERY_RESPONSE,
                                reachableMovedCowEntityQueryResponse,
                                deadMovedCowEntityQueryResponse,
                            ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(server.requestBodies.any { it.contains(""""x":24.0""") })
            assertTrue(server.requestBodies.any { it.contains(""""y":70.0""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner blocks when discovered entity attack has no public attack target`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponse = EMPTY_ENTITY_QUERY_RESPONSE,
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:entity.query.attack-target", result.blocker)
            assertFalse(result.actionLog.map { it.action }.contains("entity.attack"))
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 2)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner explores with generated navigation to find public attack target`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("entity.attack"))
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 4)
            assertTrue(result.actionLog.map { it.action }.count { it == "navigation.plan" } >= 3)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner continues generated attack exploration after a waypoint navigation miss`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("entity.attack"))
            assertTrue(result.actionLog.map { it.action }.count { it == "navigation.follow" } >= 4)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner prefers vertically reachable public attack targets`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            lowerAquaticAndReachableSheepEntityQueryResponse,
                            reachableSheepEntityQueryResponse,
                            deadSheepEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-2"}""")
                },
            )
            assertFalse(server.requestBodies.any { it.contains(""""target":{"handle":"entity.handle-6"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner prefers public attack targets with configured loot evidence`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            closeSquidAndReachableCowEntityQueryResponse,
                            closeSquidAndReachableCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.any { it.contains(""""target":{"handle":"entity.handle-6"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner prefers earlier configured public attack target evidence over distance`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            closerPigAndReachableCowEntityQueryResponse,
                            closerPigAndReachableCowEntityQueryResponse,
                            deadPigAndReachableCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-5"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner keeps exploring when only generic aquatic living targets are visible`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            reachableSalmonEntityQueryResponse,
                            reachableSalmonEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.any { it.contains(""""target":{"handle":"entity.handle-salmon"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner continues bounded generated attack exploration beyond first waypoint ring`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(EMPTY_ENTITY_QUERY_RESPONSE) +
                            List(24) { reachableSalmonEntityQueryResponse } +
                            listOf(
                                aliveCowEntityQueryResponse,
                                aliveCowEntityQueryResponse,
                                deadCowEntityQueryResponse,
                            ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 27)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.any { it.contains(""""target":{"handle":"entity.handle-salmon"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner revalidates public attack target after navigation before attacking`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveFarSheepEntityQueryResponse,
                            reachableSheepEntityQueryResponse,
                            deadSheepEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 4)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-2"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner re-equips generated combat slot after navigation before attacking`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft", "entity.attack"),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 1),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationlessWeaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveFarSheepEntityQueryResponse,
                            reachableSheepEntityQueryResponse,
                            deadSheepEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            val firstAttackIndex = server.requestBodies.indexOfFirst { it.contains("entity.attack") }
            val lastNavigationIndex =
                server.requestBodies
                    .take(firstAttackIndex)
                    .indexOfLast { it.contains("navigation.follow") }
            val equippedAfterNavigation =
                server.requestBodies
                    .subList(lastNavigationIndex + 1, firstAttackIndex)
                    .any { body ->
                        body.contains("inventory.equip") &&
                            body.contains(""""slot":2""")
                    }
            assertTrue(equippedAfterNavigation)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner does not attack public entity target that remains outside generated attack range`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    blockQueryResponses = listOf(combatReachLogBlockQueryResponse),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            unreachableCloseCowEntityQueryResponse,
                        ),
                    playerQueryResponse =
                        """
                        {
                          "action": "player.query",
                          "status": "ACCEPTED",
                          "data": {
                            "position": {"x": 25.5, "y": 70.0, "z": -300.8}
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals(
                "insufficient-public-evidence:entity.query.attack-target.reachable",
                result.blocker,
                result.blockerWithActions(),
            )
            assertFalse(result.actionLog.map { it.action }.contains("entity.attack"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner uses generated player move when final combat navigation cannot close reach gap`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("entity.attack", "player.move"),
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            unreachableCloseCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("player.move"))
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner uses generated player move when combat navigation succeeds but target remains out of reach`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("entity.attack", "player.move"),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            unreachableCloseCowEntityQueryResponse,
                        ),
                    entityQueryResponsesAfterPlayerMove =
                        listOf(
                            reachableMovedCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            val playerMoveIndex = server.requestBodies.indexOfFirst { it.contains("player.move") }
            val firstAttackIndex = server.requestBodies.indexOfFirst { it.contains("entity.attack") }
            assertTrue(playerMoveIndex >= 0, result.blockerWithActions())
            assertTrue(firstAttackIndex > playerMoveIndex, result.blockerWithActions())
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner re-queries wider public entity perception after fallback combat movement loses close target`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("entity.attack", "player.move"),
                    navigationFollowResponses =
                        listOf(
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_SUCCEEDED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                            NAVIGATION_FAILED_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            unreachableCloseCowEntityQueryResponse,
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            unreachableCloseCowEntityQueryResponse,
                            reachableMovedCowEntityQueryResponse,
                            deadMovedCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("player.move"))
            assertTrue(result.actionLog.map { it.action }.count { it == "entity.query" } >= 5)
            assertTrue(
                server.requestBodies.any {
                    it.contains("entity.attack") &&
                        it.contains(""""target":{"handle":"entity.handle-42"}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner picks up public combat loot entity before treating combat evidence as complete`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "entity.attack",
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowWithRawBeefEntityQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 0),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            rawBeefInventoryQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(
                server.requestBodies.any {
                    it.contains(""""goal":{"kind":"block","position":{"x":14.8,"y":64.0,"z":-6.2}""")
                },
            )
            assertTrue(
                result.actionLog
                    .last()
                    .response
                    .contains("Raw Beef"),
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner crafts useful inventory output through generated recipe actions when available`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft", "world.block.interact"),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses = listOf(logBlockQueryResponse, placementSupportBlockQueryResponse),
                    inventoryResponses =
                        listOf(
                            """{"action":"inventory.query","status":"ACCEPTED","data":{"selected-slot":0,"slots":[]}}""",
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedToolInventoryResponse,
                        ),
                    recipeQueryResponse =
                        """
                        {
                          "action": "recipe.query",
                          "status": "ACCEPTED",
                          "data": {
                            "count": 1,
                            "recipes": [
                              {
                                "handle": "recipe.handle:tool-1",
                                "label": "Useful tool",
                                "category": "tool",
                                "craftable": true,
                                "produces": [
                                  {"label": "Wooden Sword", "category": "weapon", "count": 1}
                                ]
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    recipeCraftResponse =
                        """
                        {
                          "action": "recipe.craft",
                          "status": "ACCEPTED",
                          "data": {
                            "handle": "recipe.handle:tool-1",
                            "accepted": true,
                            "changed": true,
                            "requested-count": 1,
                            "crafted-count": 1
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            val actions = result.actionLog.map { it.action }
            assertTrue(actions.contains("recipe.query"))
            assertTrue(actions.contains("recipe.craft"))
            assertFalse(actions.contains("world.block.interact"))
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:tool-1"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner treats generated material recipes as useful crafting progress`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft"),
                    inventoryResponses =
                        listOf(
                            """{"action":"inventory.query","status":"ACCEPTED","data":{"selected-slot":0,"slots":[]}}""",
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                        ),
                    recipeQueryResponse =
                        """
                        {
                          "action": "recipe.query",
                          "status": "ACCEPTED",
                          "data": {
                            "count": 1,
                            "recipes": [
                              {
                                "handle": "recipe.handle:material-1",
                                "craftable": true,
                                "outputs": [
                                  {"label": "Oak Planks", "category": "material", "count": 4}
                                ]
                              }
                            ]
                          }
                        }
                        """.trimIndent(),
                    recipeCraftResponse =
                        """
                        {
                          "action": "recipe.craft",
                          "status": "ACCEPTED",
                          "data": {
                            "handle": "recipe.handle:material-1",
                            "accepted": true,
                            "changed": true,
                            "requested-count": 1,
                            "crafted-count": 1
                          }
                        }
                        """.trimIndent(),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("recipe.craft"))
            assertTrue(
                result.actionLog
                    .first { it.action == "recipe.craft" }
                    .response
                    .contains(""""requested-count": 1"""),
            )
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:material-1"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner composes generated material and combat recipes before combat`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft", "entity.attack"),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 1),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationlessWeaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertEquals(2, result.actionLog.map { it.action }.count { it == "recipe.craft" })
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:material-1"}""") })
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:weapon-1"}""") })
            assertTrue(
                server.requestBodies.any {
                    it.contains("inventory.equip") &&
                        it.contains(""""slot":2""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner blocks when generated combat recipe lacks combat inventory proof`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + listOf("recipe.query", "recipe.craft"),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                        ),
                    recipeQueryResponse = stationlessWeaponRecipeQueryResponse,
                    recipeCraftResponse = WEAPON_RECIPE_CRAFT_RESPONSE,
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:inventory.query.combat-item", result.blocker)
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:weapon-1"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner opens generated crafting station before station backed combat recipe`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertEquals(4, result.actionLog.map { it.action }.count { it == "recipe.craft" })
            assertTrue(result.actionLog.map { it.action }.contains("screen.query"))
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.interact" } >= 2)
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:station-1"}""") })
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:weapon-1"}""") })
            assertTrue(
                server.requestBodies.any {
                    it.contains("inventory.equip") &&
                        it.contains(""""slot":8""")
                },
            )
            assertTrue(
                server.requestBodies.count {
                    it.contains("inventory.equip") &&
                        it.contains(""""slot":8""")
                } >= 2,
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner collects more generated material before station backed recipe composition`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            secondReachableLogBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            singleLogInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logAndPlanksInventoryResponse,
                            logAndStationInventoryResponse(selectedSlot = 1),
                            logAndStationInventoryResponse(selectedSlot = 8),
                            logAndStationInventoryResponse(selectedSlot = 8),
                            logOnlyInventoryResponse(selectedSlot = 8),
                            logOnlyInventoryResponse(selectedSlot = 0),
                            craftedMaterialInventoryResponse,
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            materialRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            val actionsBeforeFirstRecipeCraft = result.actionLog.map { it.action }.takeWhile { it != "recipe.craft" }
            assertEquals(2, actionsBeforeFirstRecipeCraft.count { it == "world.block.break" })
            assertTrue(
                server.requestBodies.any {
                    it.contains("world.block.break") &&
                        it.contains(""""handle":"world.block:12:66:-4"""")
                },
            )
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:weapon-1"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner can reuse generated material recipe handles after opening a station`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logAndPlanksInventoryResponse,
                            logAndStationInventoryResponse(selectedSlot = 1),
                            logAndStationInventoryResponse(selectedSlot = 8),
                            logAndStationInventoryResponse(selectedSlot = 8),
                            logOnlyInventoryResponse(selectedSlot = 8),
                            logOnlyInventoryResponse(selectedSlot = 0),
                            craftedMaterialInventoryResponse,
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            materialRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertEquals(
                2,
                server.requestBodies.count { it.contains(""""target":{"handle":"recipe.handle:material-1"}""") },
            )
            assertTrue(server.requestBodies.any { it.contains(""""target":{"handle":"recipe.handle:weapon-1"}""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner opens reachable generated station without post placement navigation`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertFalse(
                server.requestBodies.any {
                    it.contains("navigation.plan") &&
                        it.contains(""""x":11""") &&
                        it.contains(""""y":65""") &&
                        it.contains(""""z":-4""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner retries alternate public support targets when placing generated crafting station`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlocksQueryResponse,
                            placementSupportBlockQueryResponse,
                            placementSupportBlockWithNorthFaceQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": false,
                                "changed": false,
                                "handle": "world.block:11:64:-4"
                              }
                            }
                            """.trimIndent(),
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.interact" } >= 3)
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:11:64:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:12:64:-4"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner rejects air adjacent placement evidence before opening generated station`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlocksQueryResponse,
                            placementSupportBlockQueryResponse,
                            placementSupportBlockWithNorthFaceQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": true,
                                "changed": true,
                                "handle": "world.block:11:64:-4",
                                "adjacent-handle": "world.block:11:65:-4",
                                "adjacent-position": {"x": 11, "y": 65, "z": -4},
                                "adjacent-category": "air",
                                "adjacent-replaceable": true,
                                "side": "up"
                              }
                            }
                            """.trimIndent(),
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": true,
                                "changed": true,
                                "handle": "world.block:12:64:-4",
                                "adjacent-handle": "world.block:12:64:-5",
                                "adjacent-position": {"x": 12, "y": 64, "z": -5},
                                "adjacent-category": "block",
                                "adjacent-replaceable": false,
                                "side": "north"
                              }
                            }
                            """.trimIndent(),
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.interact" } >= 3)
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:11:64:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:12:64:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:12:64:-5"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner verifies placed generated station through public block query before opening`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                            "world.block.query" to listOf("target"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlocksQueryResponse,
                            placementSupportBlockQueryResponse,
                            queriedAirPlacedStationResponse,
                            placementSupportBlockWithNorthFaceQueryResponse,
                            queriedSolidPlacedStationResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": true,
                                "changed": true,
                                "handle": "world.block:12:64:-4",
                                "adjacent-handle": "world.block:12:64:-5",
                                "adjacent-position": {"x": 12, "y": 64, "z": -5},
                                "adjacent-category": "block",
                                "adjacent-replaceable": false,
                                "side": "north"
                              }
                            }
                            """.trimIndent(),
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.interact" } >= 3)
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:11:65:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:12:64:-5"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner selects empty public hotbar slot before opening placed generated station`() =
        runBlocking {
            val stationInventoryWithEmptySlot =
                """
                {
                  "action": "inventory.query",
                  "status": "ACCEPTED",
                  "data": {
                    "selected-slot": 8,
                    "slots": [
                      {"slot": 0, "empty": false, "count": 3, "item-name": "Dirt"},
                      {"slot": 2, "empty": true},
                      {"slot": 8, "empty": false, "count": 1, "item-name": "Crafting Table"}
                    ]
                  }
                }
                """.trimIndent()
            val emptyHandInventory =
                """
                {
                  "action": "inventory.query",
                  "status": "ACCEPTED",
                  "data": {
                    "selected-slot": 2,
                    "slots": [
                      {"slot": 0, "empty": false, "count": 3, "item-name": "Dirt"},
                      {"slot": 2, "empty": true},
                      {"slot": 8, "empty": true}
                    ]
                  }
                }
                """.trimIndent()
            val inventoryBeforeOpeningStation =
                """
                {
                  "action": "inventory.query",
                  "status": "ACCEPTED",
                  "data": {
                    "selected-slot": 0,
                    "slots": [
                      {"slot": 0, "empty": false, "count": 3, "item-name": "Dirt"},
                      {"slot": 2, "empty": true},
                      {"slot": 8, "empty": true}
                    ]
                  }
                }
                """.trimIndent()
            val server =
                RecordingCraftlessHttpServer(
                    actions =
                        completeActionCatalog() +
                            listOf(
                                "recipe.query",
                                "recipe.craft",
                                "world.block.interact",
                                "screen.query",
                                "entity.attack",
                            ),
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                        ),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            craftedMaterialInventoryResponse,
                            craftedStationOnlyInventoryResponse(selectedSlot = 1),
                            craftedStationOnlyInventoryResponse(selectedSlot = 8),
                            stationInventoryWithEmptySlot,
                            inventoryBeforeOpeningStation,
                            emptyHandInventory,
                            craftedStickInventoryResponse,
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                            craftedWeaponInventoryResponse(selectedSlot = 2),
                        ),
                    recipeQueryResponses =
                        listOf(
                            materialRecipeQueryResponse,
                            stationAndWeaponRecipeQueryResponse,
                            stickRecipeQueryResponse,
                            weaponRecipeQueryResponse,
                        ),
                    recipeCraftResponses =
                        listOf(
                            MATERIAL_RECIPE_CRAFT_RESPONSE,
                            STATION_RECIPE_CRAFT_RESPONSE,
                            STICK_RECIPE_CRAFT_RESPONSE,
                            WEAPON_RECIPE_CRAFT_RESPONSE,
                        ),
                    blockInteractResponses =
                        listOf(
                            stationPlaceInteractResponse,
                            stationOpenInteractResponse,
                        ),
                    screenQueryResponses =
                        listOf(
                            SCREEN_CLOSED_RESPONSE,
                            SCREEN_OPEN_CRAFTING_TABLE_RESPONSE,
                        ),
                    entityQueryResponses =
                        listOf(
                            EMPTY_ENTITY_QUERY_RESPONSE,
                            aliveCowEntityQueryResponse,
                            aliveCowEntityQueryResponse,
                            deadCowEntityQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state, result.blocker)
            assertTrue(
                server.requestBodies.any {
                    it.contains("inventory.equip") &&
                        it.contains(""""slot":2""")
                },
            )
            val openStationInteractIndex =
                server.requestBodies.indexOfLast {
                    it.contains("world.block.interact") &&
                        it.contains(""""target":{"handle":"world.block:11:65:-4"""")
                }
            val navigationBeforeOpenIndex =
                server.requestBodies
                    .take(openStationInteractIndex)
                    .indexOfLast { it.contains("navigation.follow") }
            val emptyEquipBeforeOpenIndex =
                server.requestBodies
                    .take(openStationInteractIndex)
                    .indexOfLast {
                        it.contains("inventory.equip") &&
                            it.contains(""""slot":2""")
                    }
            assertTrue(emptyEquipBeforeOpenIndex > navigationBeforeOpenIndex)
            assertFalse(
                server.requestBodies
                    .subList(emptyEquipBeforeOpenIndex + 1, openStationInteractIndex)
                    .any { it.contains("navigation.follow") },
            )
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
    fun `runner retries alternate public block interact targets before blocking placement`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlocksQueryResponse,
                            placementSupportBlockQueryResponse,
                            placementSupportBlockWithNorthFaceQueryResponse,
                        ),
                    blockInteractResponses =
                        listOf(
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": false,
                                "changed": false,
                                "handle": "world.block:11:64:-4"
                              }
                            }
                            """.trimIndent(),
                            """
                            {
                              "action": "world.block.interact",
                              "status": "ACCEPTED",
                              "data": {
                                "accepted": true,
                                "changed": true,
                                "handle": "world.block:12:64:-4",
                                "adjacent-handle": "world.block:12:65:-4"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertEquals(2, result.actionLog.map { it.action }.count { it == "world.block.interact" })
            assertTrue(result.actionLog.map { it.action }.count { it == "player.look" } >= 3)
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:11:64:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:12:64:-4"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner uses public block face metadata for targetable block interact`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses = listOf(logBlockQueryResponse, placementSupportBlockWithNorthFaceQueryResponse),
                    inventoryResponses =
                        listOf(
                            EMPTY_INVENTORY_QUERY_RESPONSE,
                            logInSlotOneInventoryQueryResponse(selectedSlot = 0),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                            logInSlotOneInventoryQueryResponse(selectedSlot = 1),
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(
                server.requestBodies.any {
                    it.contains("world.block.interact") &&
                        it.contains(""""handle":"world.block:12:64:-4"""") &&
                        it.contains(""""side":"north"""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner re-equips collected material after navigation before targetable block interact`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses = listOf(logBlockQueryResponse, placementSupportBlockWithNorthFaceQueryResponse),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "inventory.equip" } >= 2)
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner skips replaceable block query targets for targetable block interact support`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses = listOf(logBlockQueryResponse, replaceableThenStableSupportBlocksQueryResponse),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertFalse(server.requestBodies.any { it.contains(""""handle":"world.block:13:64:-4"""") })
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:14:64:-4"""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner refreshes public support block evidence after navigation before targetable block interact`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            placementSupportBlockQueryResponse,
                            refreshedPlacementSupportBlockQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.query" } >= 3)
            assertTrue(
                server.requestBodies.any {
                    it.contains("world.block.interact") &&
                        it.contains(""""target":{"handle":"world.block:15:64:-4","position":{"x":15,"y":64,"z":-4}}""")
                },
            )
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner skips support blocks without unoccupied replaceable placement faces`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog() + "world.block.interact",
                    actionArguments =
                        mapOf(
                            "world.block.interact" to listOf("target", "side", "max-distance"),
                        ),
                    blockQueryResponses =
                        listOf(
                            logBlockQueryResponse,
                            occupiedThenFreePlacementSupportBlocksQueryResponse,
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertFalse(
                server.requestBodies.any {
                    it.contains("world.block.interact") && it.contains(""""handle":"world.block:16:64:-4"""")
                },
            )
            assertTrue(
                server.requestBodies.any {
                    it.contains("world.block.interact") &&
                        it.contains(""""handle":"world.block:17:64:-4"""") &&
                        it.contains(""""side":"north"""")
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
                ),
                result.actionLog.map { it.action },
            )
            assertTrue(server.requestBodies.count { it.contains(""""category":"log"""") } >= 2)
            assertTrue(server.requestBodies.any { it.contains(""""x":35.0""") })
            assertFalse(server.requestBodies.any { it.contains(""""x":59.0""") })
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner continues material exploration when discovered material target navigation fails`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    blockQueryResponses =
                        listOf(
                            EMPTY_BLOCK_QUERY_RESPONSE,
                            unreachableLogBlockQueryResponse,
                            reachableExplorationLogBlockQueryResponse,
                        ),
                    navigationFollowResponses =
                        listOf(
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                            """
                            {
                              "action": "navigation.follow",
                              "status": "FAILED",
                              "message": "navigation-did-not-start",
                              "data": {
                                "task-id": "task:navigation:public-agent",
                                "state": "failed"
                              }
                            }
                            """.trimIndent(),
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                        ),
                    playerQueryResponse =
                        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":24.0,"y":64.0,"z":-12.0}}}""",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(server.requestBodies.any { it.contains(""""handle":"world.block:24:64:-12"""") })
            assertFalse(server.requestBodies.any { it.contains(""""target":{"handle":"world.block:62:77:-286"""") })
            assertTrue(result.actionLog.map { it.action }.count { it == "world.block.query" } >= 3)
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
            assertTrue(gameplay.contains("public-agent-action-started"))
            assertTrue(gameplay.contains("navigation.follow"))
            assertTrue(gameplay.contains("action-request-failed:navigation.follow"))
            assertFalse(gameplay.contains("task.survival"))
        }

    @Test
    fun `runner blocks when generated navigation follow does not prove success`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    navigationFollowResponse =
                        """
                        {
                          "action": "navigation.follow",
                          "status": "FAILED",
                          "message": "navigation-did-not-start",
                          "data": {
                            "task-id": "task:navigation:public-agent",
                            "state": "failed"
                          }
                        }
                        """.trimIndent(),
                    playerQueryResponse =
                        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":80.0,"y":65.0,"z":-80.0}}}""",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:navigation.follow.succeeded", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("navigation.follow"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner verifies public position after generated navigation reports success`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    navigationFollowResponse = NAVIGATION_SUCCEEDED_RESPONSE,
                    playerQueryResponse =
                        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":80.0,"y":65.0,"z":-80.0}}}""",
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
            assertEquals("insufficient-public-evidence:navigation.follow.succeeded", result.blocker)
            assertTrue(result.actionLog.map { it.action }.contains("player.query"))
            assertFalse(result.actionLog.map { it.action }.contains("world.block.break"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
        }

    @Test
    fun `runner accepts failed generated navigation when public player position is already within goal radius`() =
        runBlocking {
            val server =
                RecordingCraftlessHttpServer(
                    actions = completeActionCatalog(),
                    navigationFollowResponses =
                        listOf(
                            """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
                            """
                            {
                              "action": "navigation.follow",
                              "status": "FAILED",
                              "message": "navigation-did-not-start",
                              "data": {
                                "task-id": "task:navigation:public-agent",
                                "state": "failed"
                              }
                            }
                            """.trimIndent(),
                        ),
                )
            val runner = PublicAgentGameplayRunner(baseUrl = server.url, clientId = "fabric-smoke", http = server.http)

            val result = runner.runOnce()

            assertEquals(PublicAgentGameplayState.RAN, result.state)
            assertTrue(result.actionLog.map { it.action }.contains("player.query"))
            assertFalse(server.requestBodies.anyScenarioShortcut())
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
                    "CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS" to "120000",
                    "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "90000",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "1500000",
                    "CRAFTLESS_PUBLIC_AGENT_COMBAT_EVIDENCE_ATTEMPTS" to "12",
                ),
            )

        assertEquals("http://127.0.0.1:18080", config.baseUrl)
        assertEquals("fabric-smoke", config.clientId)
        assertEquals(Path.of("/tmp/craftless-public-agent-artifacts"), config.artifactsDir)
        assertEquals(120_000, config.actionRequestTimeoutMillis)
        assertEquals(12, config.combatEvidenceAttempts)
    }

    @Test
    fun `runner config prefers fabric smoke action timeout over outer smoke timeout`() {
        val config =
            PublicAgentGameplayRunnerConfig.fromEnvironment(
                mapOf(
                    "CRAFTLESS_PUBLIC_AGENT_BASE_URL" to "http://127.0.0.1:18080",
                    "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "120000",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "1500000",
                ),
            )

        assertEquals(120_000, config.actionRequestTimeoutMillis)
    }

    @Test
    fun `runner config uses smoke action timeout as generated action request timeout`() {
        val config =
            PublicAgentGameplayRunnerConfig.fromEnvironment(
                mapOf(
                    "CRAFTLESS_PUBLIC_AGENT_BASE_URL" to "http://127.0.0.1:18080",
                    "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "300000",
                ),
            )

        assertEquals(300_000, config.actionRequestTimeoutMillis)
    }
}

private class RecordingCraftlessHttpServer(
    private val actions: List<String>,
    private val projectedActions: List<String> = actions,
    private val actionArguments: Map<String, List<String>> = emptyMap(),
    private val blockQueryResponses: List<String> = listOf(logBlockQueryResponse),
    private val inventoryResponses: List<String>? = null,
    private val blockBreakResponse: String =
        """{"action":"world.block.break","status":"ACCEPTED","data":{"hit":true,"target-kind":"block","started":true,"changed":true}}""",
    private val blockInteractResponse: String =
        """{"action":"world.block.interact","status":"ACCEPTED","data":{"accepted":true,"changed":true}}""",
    private val blockInteractResponses: List<String>? = null,
    private val navigationFollowResponse: String =
        """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}""",
    private val navigationFollowResponses: List<String>? = null,
    private val playerQueryResponse: String =
        """{"action":"player.query","status":"ACCEPTED","data":{"position":{"x":11.0,"y":65.0,"z":-3.0}}}""",
    private val entityQueryResponse: String = """{"action":"entity.query","status":"ACCEPTED","data":{"entities":[]}}""",
    private val entityQueryResponses: List<String>? = null,
    private val entityQueryResponsesAfterPlayerMove: List<String>? = null,
    private val entityAttackResponses: List<String>? = null,
    private val recipeQueryResponse: String =
        """{"action":"recipe.query","status":"ACCEPTED","data":{"count":0,"recipes":[]}}""",
    private val recipeQueryResponses: List<String>? = null,
    private val recipeCraftResponse: String =
        """{"action":"recipe.craft","status":"ACCEPTED","data":{"accepted":false,"changed":false,"requested-count":1,"crafted-count":0}}""",
    private val recipeCraftResponses: List<String>? = null,
    private val screenQueryResponse: String = SCREEN_CLOSED_RESPONSE,
    private val screenQueryResponses: List<String>? = null,
    private val inventoryResponseAfterPlayerMove: String? = null,
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
    private var entityQueryCount = 0
    private var entityQueryAfterPlayerMoveCount = 0
    private var blockInteractCount = 0
    private var navigationFollowCount = 0
    private var entityAttackCount = 0
    private var recipeQueryCount = 0
    private var recipeCraftCount = 0
    private var screenQueryCount = 0
    private var playerMoveCount = 0
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
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/openapi.json") -> clientOpenApiJson()
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/actions") -> actionsJson(projectedActions)
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
            body.contains("navigation.follow") -> navigationFollowResponse()
            body.contains("navigation.plan") ->
                """{"action":"navigation.plan","status":"ACCEPTED","data":{"plan-id":"navigation.plan.public-agent.0001","state":"pending"}}"""
            body.contains("player.query") -> playerQueryResponse
            body.contains("player.look") ->
                """{"action":"player.look","status":"ACCEPTED"}"""
            body.contains("player.move") -> playerMoveResponse()
            body.contains("player.raycast") ->
                """{"action":"player.raycast","status":"ACCEPTED","data":{"hit":true,"target-kind":"block"}}"""
            body.contains("world.block.break") -> blockBreakResponse
            body.contains("world.block.interact") -> blockInteractResponse()
            body.contains("inventory.equip") -> """{"action":"inventory.equip","status":"ACCEPTED"}"""
            body.contains("recipe.query") -> recipeQueryResponse()
            body.contains("recipe.craft") -> recipeCraftResponse()
            body.contains("screen.query") -> screenQueryResponse()
            body.contains("entity.attack") -> entityAttackResponse()
            body.contains("entity.query") -> entityQueryResponse()
            else -> """{"action":"unknown","status":"UNSUPPORTED","message":"unexpected action"}"""
        }
    }

    private fun inventoryQueryResponse(): String {
        inventoryQueryCount += 1
        if (inventoryResponseAfterPlayerMove != null && playerMoveCount > 0) {
            return inventoryResponseAfterPlayerMove
        }
        inventoryResponses?.let { responses ->
            return responses.getOrElse(inventoryQueryCount - 1) { responses.last() }
        }
        return if (inventoryQueryCount == 1) {
            """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}"""
        } else {
            finalInventoryResponse
        }
    }

    private fun playerMoveResponse(): String {
        playerMoveCount += 1
        return """{"action":"player.move","status":"ACCEPTED"}"""
    }

    private fun blockQueryResponse(): String {
        blockQueryCount += 1
        return blockQueryResponses.getOrElse(blockQueryCount - 1) { blockQueryResponses.last() }
    }

    private fun entityQueryResponse(): String {
        if (entityQueryResponsesAfterPlayerMove != null && playerMoveCount > 0) {
            entityQueryAfterPlayerMoveCount += 1
            return entityQueryResponsesAfterPlayerMove.getOrElse(entityQueryAfterPlayerMoveCount - 1) {
                entityQueryResponsesAfterPlayerMove.last()
            }
        }
        entityQueryCount += 1
        return entityQueryResponses?.getOrElse(entityQueryCount - 1) { entityQueryResponses.last() }
            ?: entityQueryResponse
    }

    private fun entityAttackResponse(): String {
        entityAttackCount += 1
        return entityAttackResponses?.getOrElse(entityAttackCount - 1) { entityAttackResponses.last() }
            ?: """{"action":"entity.attack","status":"ACCEPTED","data":{"hit":true,"handle":"entity.handle-42"}}"""
    }

    private fun blockInteractResponse(): String {
        blockInteractCount += 1
        return blockInteractResponses?.getOrElse(blockInteractCount - 1) { blockInteractResponses.last() }
            ?: blockInteractResponse
    }

    private fun navigationFollowResponse(): String {
        navigationFollowCount += 1
        return navigationFollowResponses?.getOrElse(navigationFollowCount - 1) { navigationFollowResponses.last() }
            ?: navigationFollowResponse
    }

    private fun recipeQueryResponse(): String {
        recipeQueryCount += 1
        return recipeQueryResponses?.getOrElse(recipeQueryCount - 1) { recipeQueryResponses.last() }
            ?: recipeQueryResponse
    }

    private fun recipeCraftResponse(): String {
        recipeCraftCount += 1
        return recipeCraftResponses?.getOrElse(recipeCraftCount - 1) { recipeCraftResponses.last() }
            ?: recipeCraftResponse
    }

    private fun screenQueryResponse(): String {
        screenQueryCount += 1
        return screenQueryResponses?.getOrElse(screenQueryCount - 1) { screenQueryResponses.last() }
            ?: screenQueryResponse
    }

    private fun clientOpenApiJson(): String = """{"openapi":"3.1.0","x-craftless-actions":${actionsJson(actions)}}"""

    private fun actionsJson(actions: List<String>): String =
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

private const val EMPTY_ENTITY_QUERY_RESPONSE =
    """{"action":"entity.query","status":"ACCEPTED","data":{"entities":[]}}"""

private const val EMPTY_INVENTORY_QUERY_RESPONSE =
    """{"action":"inventory.query","status":"ACCEPTED","data":{"slots":[]}}"""

private const val NAVIGATION_SUCCEEDED_RESPONSE =
    """{"action":"navigation.follow","status":"ACCEPTED","data":{"task-id":"task:navigation:public-agent","state":"succeeded"}}"""

private const val NAVIGATION_FAILED_RESPONSE =
    """{"action":"navigation.follow","status":"FAILED","data":{"task-id":"task:navigation:public-agent","state":"failed"}}"""

private const val SCREEN_CLOSED_RESPONSE =
    """{"action":"screen.query","status":"ACCEPTED","data":{"open":false}}"""

private const val SCREEN_OPEN_CRAFTING_TABLE_RESPONSE =
    """{"action":"screen.query","status":"ACCEPTED","data":{"open":true,"title":"Crafting Table"}}"""

private fun logInSlotOneInventoryQueryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Sapling"},
          {"slot": 1, "empty": false, "count": 2, "item-name": "Oak Log"}
        ]
      }
    }
    """.trimIndent()

private fun logCountInventoryResponse(
    count: Int,
    selectedSlot: Int = 0,
): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": false, "count": $count, "item-name": "Oak Log"}
        ]
      }
    }
    """.trimIndent()

private fun singleLogInSlotOneInventoryQueryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Sapling"},
          {"slot": 1, "empty": false, "count": 1, "item-name": "Oak Log"}
        ]
      }
    }
    """.trimIndent()

private val craftedToolInventoryResponse =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": 1,
        "slots": [
          {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Sapling"},
          {"slot": 1, "empty": false, "count": 1, "item-name": "Wooden Sword"}
        ]
      }
    }
    """.trimIndent()

private val craftedMaterialInventoryResponse =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": 1,
        "slots": [
          {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Sapling"},
          {"slot": 1, "empty": false, "count": 4, "item-name": "Oak Planks"}
        ]
      }
    }
    """.trimIndent()

private val logAndPlanksInventoryResponse =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": 1,
        "slots": [
          {"slot": 1, "empty": false, "count": 1, "item-name": "Oak Log"},
          {"slot": 8, "empty": false, "count": 4, "item-name": "Oak Planks"}
        ]
      }
    }
    """.trimIndent()

private fun logAndStationInventoryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": true},
          {"slot": 1, "empty": false, "count": 1, "item-name": "Oak Log"},
          {"slot": 8, "empty": false, "count": 1, "item-name": "Crafting Table"}
        ]
      }
    }
    """.trimIndent()

private fun logOnlyInventoryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": true},
          {"slot": 1, "empty": false, "count": 1, "item-name": "Oak Log"}
        ]
      }
    }
    """.trimIndent()

private fun craftedStationOnlyInventoryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 8, "empty": false, "count": 1, "item-name": "Crafting Table"}
        ]
      }
    }
    """.trimIndent()

private val craftedStickInventoryResponse =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": 2,
        "slots": [
          {"slot": 7, "empty": false, "count": 4, "item-name": "Stick"},
          {"slot": 8, "empty": false, "count": 2, "item-name": "Oak Planks"}
        ]
      }
    }
    """.trimIndent()

private fun craftedWeaponInventoryResponse(selectedSlot: Int): String =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": $selectedSlot,
        "slots": [
          {"slot": 0, "empty": false, "count": 1, "item-name": "Oak Sapling"},
          {"slot": 1, "empty": false, "count": 2, "item-name": "Oak Planks"},
          {"slot": 2, "empty": false, "count": 1, "item-name": "Wooden Sword"}
        ]
      }
    }
    """.trimIndent()

private val materialRecipeQueryResponse =
    """
    {
      "action": "recipe.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "recipes": [
          {
            "handle": "recipe.handle:material-1",
            "craftable": true,
            "outputs": [
              {"label": "Oak Planks", "category": "material", "count": 4}
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val stationAndWeaponRecipeQueryResponse =
    """
    {
      "action": "recipe.query",
      "status": "ACCEPTED",
      "data": {
        "count": 2,
        "recipes": [
          {
            "handle": "recipe.handle:weapon-1",
            "craftable": true,
            "station": {"label": "Crafting Table", "category": "item", "count": 1},
            "outputs": [
              {"label": "Wooden Sword", "category": "weapon", "count": 1}
            ]
          },
          {
            "handle": "recipe.handle:station-1",
            "craftable": true,
            "outputs": [
              {"label": "Crafting Table", "category": "item", "count": 1}
            ],
            "station": {"label": "Crafting Table", "category": "item", "count": 1}
          }
        ]
      }
    }
    """.trimIndent()

private val stickRecipeQueryResponse =
    """
    {
      "action": "recipe.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "recipes": [
          {
            "handle": "recipe.handle:stick-1",
            "craftable": true,
            "outputs": [
              {"label": "Stick", "category": "material", "count": 4}
            ],
            "station": {"label": "Crafting Table", "category": "item", "count": 1}
          }
        ]
      }
    }
    """.trimIndent()

private val stationlessWeaponRecipeQueryResponse =
    """
    {
      "action": "recipe.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "recipes": [
          {
            "handle": "recipe.handle:weapon-1",
            "craftable": true,
            "outputs": [
              {"label": "Wooden Sword", "category": "weapon", "count": 1}
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val weaponRecipeQueryResponse =
    """
    {
      "action": "recipe.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "recipes": [
          {
            "handle": "recipe.handle:weapon-1",
            "craftable": true,
            "station": {"label": "Crafting Table", "category": "item", "count": 1},
            "outputs": [
              {"label": "Wooden Sword", "category": "weapon", "count": 1}
            ]
          }
        ]
      }
    }
    """.trimIndent()

private const val MATERIAL_RECIPE_CRAFT_RESPONSE =
    """{"action":"recipe.craft","status":"ACCEPTED","data":{"handle":"recipe.handle:material-1","accepted":true,"changed":true,"requested-count":1,"crafted-count":1}}"""

private const val STATION_RECIPE_CRAFT_RESPONSE =
    """{"action":"recipe.craft","status":"ACCEPTED","data":{"handle":"recipe.handle:station-1","accepted":true,"changed":true,"requested-count":1,"crafted-count":1}}"""

private const val STICK_RECIPE_CRAFT_RESPONSE =
    """{"action":"recipe.craft","status":"ACCEPTED","data":{"handle":"recipe.handle:stick-1","accepted":true,"changed":true,"requested-count":1,"crafted-count":1}}"""

private const val WEAPON_RECIPE_CRAFT_RESPONSE =
    """{"action":"recipe.craft","status":"ACCEPTED","data":{"handle":"recipe.handle:weapon-1","accepted":true,"changed":true,"requested-count":1,"crafted-count":1}}"""

private val stationPlaceInteractResponse =
    """
    {
      "action": "world.block.interact",
      "status": "ACCEPTED",
      "data": {
        "accepted": true,
        "changed": true,
        "handle": "world.block:11:64:-4",
        "adjacent-handle": "world.block:11:65:-4",
        "adjacent-position": {"x": 11, "y": 65, "z": -4},
        "adjacent-category": "block",
        "adjacent-replaceable": false,
        "side": "up"
      }
    }
    """.trimIndent()

private val stationOpenInteractResponse =
    """
    {
      "action": "world.block.interact",
      "status": "ACCEPTED",
      "data": {
        "accepted": true,
        "changed": false,
        "handle": "world.block:11:65:-4"
      }
    }
    """.trimIndent()

private val aliveCowEntityQueryResponse =
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
    """.trimIndent()

private val deadCowEntityQueryResponse =
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
            "alive": false,
            "distance": 3.0,
            "position": {"x": 14.5, "y": 64.0, "z": -6.5}
          }
        ]
      }
    }
    """.trimIndent()

private val deadCowWithRawBeefEntityQueryResponse =
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
            "alive": false,
            "distance": 3.0,
            "position": {"x": 14.5, "y": 64.0, "z": -6.5}
          },
          {
            "handle": "entity.handle-99",
            "label": "Raw Beef",
            "category": "object",
            "distance": 2.5,
            "position": {"x": 14.8, "y": 64.0, "z": -6.2}
          }
        ]
      }
    }
    """.trimIndent()

private val rawBeefInventoryQueryResponse =
    """
    {
      "action": "inventory.query",
      "status": "ACCEPTED",
      "data": {
        "selected-slot": 1,
        "slots": [
          {"slot": 1, "empty": false, "count": 2, "item-name": "Oak Log"},
          {"slot": 2, "empty": false, "count": 1, "item-name": "Raw Beef"}
        ]
      }
    }
    """.trimIndent()

private val movedCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 14.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-42",
            "label": "Cow",
            "category": "passive",
            "alive": true,
            "distance": 8.0,
            "position": {"x": 24.0, "y": 64.0, "z": -10.0}
          }
        ]
      }
    }
    """.trimIndent()

private val aliveCowAndCloserChickenEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 14.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-7",
            "label": "Chicken",
            "category": "passive",
            "alive": true,
            "distance": 1.0,
            "position": {"x": 14.2, "y": 64.0, "z": -5.5}
          },
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
    """.trimIndent()

private val closeChickenAndCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 14.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-7",
            "label": "Chicken",
            "category": "passive",
            "alive": true,
            "distance": 1.0,
            "position": {"x": 14.2, "y": 64.0, "z": -5.5}
          },
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
    """.trimIndent()

private val verticallyOffsetCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 14.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-42",
            "label": "Cow",
            "category": "passive",
            "alive": true,
            "distance": 8.0,
            "position": {"x": 24.0, "y": 70.0, "z": -10.0}
          }
        ]
      }
    }
    """.trimIndent()

private val reachableMovedCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 23.0, "y": 64.0, "z": -10.0},
        "entities": [
          {
            "handle": "entity.handle-42",
            "label": "Cow",
            "category": "passive",
            "alive": true,
            "distance": 2.0,
            "position": {"x": 24.0, "y": 64.0, "z": -10.0}
          }
        ]
      }
    }
    """.trimIndent()

private val unreachableCloseCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 25.5, "y": 70.0, "z": -300.8},
        "entities": [
          {
            "handle": "entity.handle-42",
            "label": "Cow",
            "category": "passive",
            "alive": true,
            "distance": 6.0,
            "position": {"x": 22.5, "y": 73.0, "z": -296.5}
          }
        ]
      }
    }
    """.trimIndent()

private val deadMovedCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 23.0, "y": 64.0, "z": -10.0},
        "entities": [
          {
            "handle": "entity.handle-42",
            "label": "Cow",
            "category": "passive",
            "alive": false,
            "distance": 2.0,
            "position": {"x": 24.0, "y": 64.0, "z": -10.0}
          }
        ]
      }
    }
    """.trimIndent()

private val lowerAquaticAndReachableSheepEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 5.0, "y": 63.0, "z": -266.0},
        "entities": [
          {
            "handle": "entity.handle-6",
            "label": "Squid",
            "category": "passive",
            "alive": true,
            "distance": 12.0,
            "position": {"x": 7.5, "y": 53.5, "z": -259.2}
          },
          {
            "handle": "entity.handle-2",
            "label": "Sheep",
            "category": "passive",
            "alive": true,
            "distance": 15.0,
            "position": {"x": 20.0, "y": 67.0, "z": -269.0}
          }
        ]
      }
    }
    """.trimIndent()

private val aliveFarSheepEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 5.0, "y": 63.0, "z": -266.0},
        "entities": [
          {
            "handle": "entity.handle-2",
            "label": "Sheep",
            "category": "passive",
            "alive": true,
            "distance": 15.0,
            "position": {"x": 20.0, "y": 67.0, "z": -269.0}
          }
        ]
      }
    }
    """.trimIndent()

private val closeSquidAndReachableCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 12.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-6",
            "label": "Squid",
            "category": "passive",
            "alive": true,
            "distance": 2.0,
            "position": {"x": 12.5, "y": 64.0, "z": -5.5}
          },
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
    """.trimIndent()

private val closerPigAndReachableCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 12.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-5",
            "label": "Pig",
            "category": "passive",
            "alive": true,
            "distance": 1.0,
            "position": {"x": 12.5, "y": 64.0, "z": -5.5}
          },
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
    """.trimIndent()

private val deadPigAndReachableCowEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 12.0, "y": 64.0, "z": -6.0},
        "entities": [
          {
            "handle": "entity.handle-5",
            "label": "Pig",
            "category": "passive",
            "alive": false,
            "distance": 1.0,
            "position": {"x": 12.5, "y": 64.0, "z": -5.5}
          },
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
    """.trimIndent()

private val reachableSalmonEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 5.0, "y": 63.0, "z": -266.0},
        "entities": [
          {
            "handle": "entity.handle-salmon",
            "label": "Salmon",
            "category": "living",
            "alive": true,
            "distance": 3.0,
            "position": {"x": 12.5, "y": 64.0, "z": -5.5}
          }
        ]
      }
    }
    """.trimIndent()

private val reachableSheepEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "origin": {"x": 19.0, "y": 67.0, "z": -269.0},
        "entities": [
          {
            "handle": "entity.handle-2",
            "label": "Sheep",
            "category": "passive",
            "alive": true,
            "distance": 2.0,
            "position": {"x": 20.0, "y": 67.0, "z": -269.0}
          }
        ]
      }
    }
    """.trimIndent()

private val deadSheepEntityQueryResponse =
    """
    {
      "action": "entity.query",
      "status": "ACCEPTED",
      "data": {
        "entities": [
          {
            "handle": "entity.handle-2",
            "label": "Sheep",
            "category": "passive",
            "alive": false,
            "distance": 2.0,
            "position": {"x": 20.0, "y": 67.0, "z": -269.0}
          }
        ]
      }
    }
    """.trimIndent()

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

private val combatReachLogBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:25:70:-301",
            "category": "log",
            "distance": 2.0,
            "position": {"x": 25, "y": 70, "z": -301}
          }
        ]
      }
    }
    """.trimIndent()

private val secondReachableLogBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:12:66:-4",
            "category": "log",
            "distance": 4.0,
            "position": {"x": 12, "y": 66, "z": -4}
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

private val unreachableLogBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:62:77:-286",
            "category": "log",
            "replaceable": false,
            "distance": 29.8,
            "position": {"x": 62, "y": 77, "z": -286}
          }
        ]
      }
    }
    """.trimIndent()

private val reachableExplorationLogBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:24:64:-12",
            "category": "log",
            "replaceable": false,
            "distance": 4.0,
            "position": {"x": 24, "y": 64, "z": -12}
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
            "replaceable": false,
            "distance": 2.0,
            "position": {"x": 11, "y": 64, "z": -4},
            "faces": [
              {
                "side": "up",
                "adjacent-handle": "world.block:11:65:-4",
                "adjacent-position": {"x": 11, "y": 65, "z": -4},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val placementSupportBlocksQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 2,
        "blocks": [
          {
            "handle": "world.block:11:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 2.0,
            "position": {"x": 11, "y": 64, "z": -4},
            "faces": [
              {
                "side": "up",
                "adjacent-handle": "world.block:11:65:-4",
                "adjacent-position": {"x": 11, "y": 65, "z": -4},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          },
          {
            "handle": "world.block:12:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 3.0,
            "position": {"x": 12, "y": 64, "z": -4},
            "faces": [
              {
                "side": "up",
                "adjacent-handle": "world.block:12:65:-4",
                "adjacent-position": {"x": 12, "y": 65, "z": -4},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val placementSupportBlockWithNorthFaceQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:12:64:-4",
            "category": "block",
            "distance": 3.0,
            "position": {"x": 12, "y": 64, "z": -4},
            "faces": [
              {
                "side": "north",
                "adjacent-handle": "world.block:12:64:-5",
                "adjacent-position": {"x": 12, "y": 64, "z": -5},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val queriedAirPlacedStationResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:11:65:-4",
            "category": "air",
            "replaceable": true,
            "distance": 1.0,
            "position": {"x": 11, "y": 65, "z": -4}
          }
        ]
      }
    }
    """.trimIndent()

private val queriedSolidPlacedStationResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:12:64:-5",
            "category": "block",
            "replaceable": false,
            "distance": 1.0,
            "position": {"x": 12, "y": 64, "z": -5}
          }
        ]
      }
    }
    """.trimIndent()

private val replaceableThenStableSupportBlocksQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 2,
        "blocks": [
          {
            "handle": "world.block:13:64:-4",
            "category": "block",
            "replaceable": true,
            "distance": 2.0,
            "position": {"x": 13, "y": 64, "z": -4},
            "faces": [
              {
                "side": "north",
                "adjacent-handle": "world.block:13:64:-5",
                "adjacent-position": {"x": 13, "y": 64, "z": -5},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          },
          {
            "handle": "world.block:14:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 3.0,
            "position": {"x": 14, "y": 64, "z": -4},
            "faces": [
              {
                "side": "north",
                "adjacent-handle": "world.block:14:64:-5",
                "adjacent-position": {"x": 14, "y": 64, "z": -5},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val refreshedPlacementSupportBlockQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 1,
        "blocks": [
          {
            "handle": "world.block:15:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 1.0,
            "position": {"x": 15, "y": 64, "z": -4},
            "faces": [
              {
                "side": "north",
                "adjacent-handle": "world.block:15:64:-5",
                "adjacent-position": {"x": 15, "y": 64, "z": -5},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private val occupiedThenFreePlacementSupportBlocksQueryResponse =
    """
    {
      "action": "world.block.query",
      "status": "ACCEPTED",
      "data": {
        "count": 2,
        "blocks": [
          {
            "handle": "world.block:16:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 1.0,
            "position": {"x": 16, "y": 64, "z": -4},
            "faces": [
              {
                "side": "up",
                "adjacent-handle": "world.block:16:65:-4",
                "adjacent-position": {"x": 16, "y": 65, "z": -4},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": true
              }
            ]
          },
          {
            "handle": "world.block:17:64:-4",
            "category": "block",
            "replaceable": false,
            "distance": 2.0,
            "position": {"x": 17, "y": 64, "z": -4},
            "faces": [
              {
                "side": "north",
                "adjacent-handle": "world.block:17:64:-5",
                "adjacent-position": {"x": 17, "y": 64, "z": -5},
                "adjacent-category": "air",
                "replaceable": true,
                "occupied-by-player": false
              }
            ]
          }
        ]
      }
    }
    """.trimIndent()

private fun List<String>.anyScenarioShortcut(): Boolean =
    any { body ->
        scenarioShortcutNames.any(body::contains)
    }

private fun PublicAgentGameplayResult.blockerWithActions(): String = "blocker=$blocker actions=${actionLog.map { it.action }}"

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
