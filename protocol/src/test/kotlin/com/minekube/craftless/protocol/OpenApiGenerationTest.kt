package com.minekube.craftless.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiGenerationTest {
    @Test
    fun `openapi json omits nulls and default empty sections`() {
        val encoded = PrettyOpenApiJson.encodeToString(OpenApiDocument.from(ApiRouteCatalog.sessionDefaults()))
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertTrue(encoded.contains("\"openapi\""))
        assertTrue(encoded.contains("\"info\""))
        assertFalse(encoded.contains(": null"))
        assertFalse("x-craftless-actions" in root)
        assertFalse("x-craftless-handles" in root)
        assertFalse("x-craftless-events" in root)
        assertFalse("post" in requireNotNull(root["paths"]).jsonObject.getValue("/openapi.json").jsonObject)
    }

    @Test
    fun `openapi document includes craftless metadata for generic action route`() {
        val document =
            OpenApiDocument.from(
                catalog =
                    ApiRouteCatalog(
                        ApiRouteCatalog.sessionDefaults().routes +
                            ApiRoute(
                                method = "POST",
                                path = "/clients/{id}/world:scan",
                                operationId = "runWorldScan",
                                tag = "clients",
                                owner = "clients",
                                member = "run",
                                target = "client",
                                source = "action",
                                actionId = "world.scan",
                            ),
                    ),
                actions =
                    listOf(
                        OpenApiAction(
                            id = "world.scan",
                            schemaVersion = "1",
                            arguments =
                                mapOf(
                                    "target" to
                                        OpenApiActionArgument(
                                            type = "object",
                                            required = true,
                                            properties =
                                                mapOf(
                                                    "handle" to OpenApiActionArgument("string"),
                                                    "position" to
                                                        OpenApiActionArgument(
                                                            type = "object",
                                                            properties =
                                                                mapOf(
                                                                    "x" to OpenApiActionArgument("integer"),
                                                                    "y" to OpenApiActionArgument("integer"),
                                                                    "z" to OpenApiActionArgument("integer"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                ),
                            result =
                                OpenApiActionResult(
                                    properties =
                                        mapOf(
                                            "action" to OpenApiActionSchema("string"),
                                            "blocks" to
                                                OpenApiActionSchema(
                                                    type = "array",
                                                    items =
                                                        OpenApiActionSchema(
                                                            type = "object",
                                                            properties =
                                                                mapOf(
                                                                    "handle" to OpenApiActionSchema("string"),
                                                                    "category" to OpenApiActionSchema("string"),
                                                                ),
                                                        ),
                                                ),
                                            "status" to OpenApiActionSchema("string"),
                                        ),
                                    required = listOf("action", "status", "blocks"),
                                ),
                        ),
                    ),
            )

        val operation = document.paths["/clients/{id}:run"]?.post
        assertNotNull(operation)
        assertEquals("runClientAction", operation.operationId)
        assertEquals("clients", operation.tags.single())
        assertTrue(operation.extensions.keys.none { it.startsWith("x-craftless-java-") })
        assertTrue(operation.extensions.keys.none { it == "x-craftless-thread" })
        assertEquals("clients", operation.extensions["x-craftless-owner"])
        assertEquals("client", operation.extensions["x-craftless-target"])
        assertEquals("action", operation.extensions["x-craftless-source"])
        assertEquals("run", operation.extensions["x-craftless-member"])
        val schema =
            operation.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(schema)
        assertEquals("object", schema.type)
        assertEquals(listOf("action"), schema.required)
        assertEquals("string", schema.properties["action"]?.type)
        assertEquals("object", schema.properties["args"]?.type)
        val responseSchema =
            operation.responses["200"]
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(responseSchema)
        assertEquals("object", responseSchema.type)
        assertEquals(listOf("action", "status"), responseSchema.required)
        assertEquals("string", responseSchema.properties["action"]?.type)
        assertEquals("string", responseSchema.properties["status"]?.type)
        assertEquals("string", responseSchema.properties["message"]?.type)

        val aliasResponseSchema =
            document.paths["/clients/{id}/world:scan"]
                ?.post
                ?.responses
                ?.get("200")
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(aliasResponseSchema)
        assertEquals(listOf("action", "status", "blocks"), aliasResponseSchema.required)
        val aliasBlocksSchema = aliasResponseSchema.properties["blocks"]
        val aliasBlockItemSchema = aliasBlocksSchema?.items
        assertEquals("array", aliasBlocksSchema?.type)
        assertEquals("object", aliasBlockItemSchema?.type)
        assertEquals("string", aliasBlockItemSchema?.properties?.get("handle")?.type)
        assertEquals("string", aliasBlockItemSchema?.properties?.get("category")?.type)

        val aliasRequestSchema =
            document.paths["/clients/{id}/world:scan"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(aliasRequestSchema)
        assertEquals(listOf("target"), aliasRequestSchema.required)
        val targetSchema = aliasRequestSchema.properties["target"]
        val positionSchema = targetSchema?.properties?.get("position")
        assertEquals("object", targetSchema?.type)
        assertEquals("string", targetSchema?.properties?.get("handle")?.type)
        assertEquals("object", positionSchema?.type)
        assertEquals("integer", positionSchema?.properties?.get("x")?.type)
        assertEquals("integer", positionSchema?.properties?.get("y")?.type)
        assertEquals("integer", positionSchema?.properties?.get("z")?.type)
    }

    @Test
    fun `stable supervisor openapi does not publish gameplay action metadata`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        assertTrue(document.actions.isEmpty())
        assertTrue(document.resources.isEmpty())
        assertTrue(document.paths.keys.none { it == "/clients/{id}/player:chat" })
        assertTrue(document.paths.keys.none { it == "/clients/{id}/player:move" })
        assertTrue(
            document.paths.keys.none {
                it.matches(Regex("""^/clients/\{id}/[^/]+:[^/]+$""")) &&
                    it != "/clients/{id}/events:stream"
            },
        )
    }

    @Test
    fun `stable supervisor openapi describes cache preparation`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
        val operation = document.paths["/cache:prepare"]?.post
        assertNotNull(operation)
        assertEquals("prepareCache", operation.operationId)
        assertEquals("cache", operation.tags.single())
        assertEquals("cache", operation.extensions["x-craftless-owner"])
        assertEquals("prepare", operation.extensions["x-craftless-member"])
        assertEquals("supervisor", operation.extensions["x-craftless-target"])

        val requestSchema =
            operation.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(requestSchema)
        assertEquals(listOf("minecraftVersion", "loader"), requestSchema.required)
        assertEquals("string", requestSchema.properties["minecraftVersion"]?.type)
        assertEquals("string", requestSchema.properties["loader"]?.type)
        assertEquals("string", requestSchema.properties["loaderVersion"]?.type)
        assertEquals(true, requestSchema.properties["loaderVersion"]?.nullable)

        val responseSchema = requireNotNull(operation.okSchema())
        assertEquals(
            listOf(
                "minecraftVersion",
                "loader",
                "loaderVersion",
                "cacheRoot",
                "minecraftVersionRoot",
                "loaderRoot",
                "runtimeRoot",
                "manifest",
                "status",
                "artifacts",
                "launch",
            ),
            responseSchema.required,
        )
        assertEquals("string", responseSchema.properties["cacheRoot"]?.type)
        assertEquals(true, responseSchema.properties["loaderVersion"]?.nullable)
        assertEquals("string", responseSchema.properties["status"]?.type)
        assertEquals("array", responseSchema.properties["artifacts"]?.type)
        val launchSchema = requireNotNull(responseSchema.properties["launch"])
        assertEquals("object", launchSchema.type)
        assertEquals("array", launchSchema.properties["classpath"]?.type)
        assertEquals("array", launchSchema.properties["nativePath"]?.type)
        assertEquals("string", launchSchema.properties["javaExecutable"]?.type)
        assertEquals(true, launchSchema.properties["javaExecutable"]?.nullable)
        assertEquals("string", launchSchema.properties["arguments"]?.type)
        assertEquals(true, launchSchema.properties["arguments"]?.nullable)
        assertErrorSchema(requireNotNull(operation.errorSchema("400")))
    }

    @Test
    fun `stable supervisor openapi makes client creation lifecycle explicit`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val createOperation = requireNotNull(document.paths["/clients"]?.post)
        val createDescription = requireNotNull(createOperation.description)

        assertTrue(createDescription.contains("launches a new daemon-managed real Minecraft Java client process"))
        assertTrue(createDescription.contains("is not a selector, retry, or reuse operation"))
        assertTrue(createDescription.contains("GET /clients"))
        assertTrue(createDescription.contains("POST /clients/{id}:stop"))
        assertTrue(createDescription.contains("Creating fresh timestamped ids for retries leaves multiple Minecraft clients running"))

        val listOperation = requireNotNull(document.paths["/clients"]?.get)
        assertTrue(requireNotNull(listOperation.description).contains("before creating another client"))

        val stopOperation = requireNotNull(document.paths["/clients/{id}:stop"]?.post)
        assertTrue(requireNotNull(stopOperation.description).contains("before replacing or retrying"))
    }

    @Test
    fun `stable supervisor openapi describes every core pillar route`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
        val operations =
            document.paths.flatMap { (path, item) ->
                listOfNotNull(
                    item.get?.let { "GET $path" to it },
                    item.post?.let { "POST $path" to it },
                )
            }

        operations.forEach { (route, operation) ->
            val summary = operation.summary
            val description = operation.description

            assertTrue(!summary.isNullOrBlank(), "$route must have an OpenAPI summary")
            assertTrue(summary.length >= 12, "$route summary should be useful to agents")
            assertTrue(!description.isNullOrBlank(), "$route must have an OpenAPI description")
            assertTrue(description.length >= 80, "$route description should explain when to use the route")
        }

        assertTrue(requireNotNull(document.paths["/openapi.json"]?.get?.description).contains("stable supervisor API"))
        assertTrue(requireNotNull(document.paths["/version"]?.get?.description).contains("runtime identity"))
        assertTrue(requireNotNull(document.paths["/events:stream"]?.get?.description).contains("Server-Sent Events"))
        assertTrue(requireNotNull(document.paths["/cache:prepare"]?.post?.description).contains("Minecraft"))
        assertTrue(requireNotNull(document.paths["/runtimes/java:resolve"]?.post?.description).contains("Java runtime"))
        assertTrue(requireNotNull(document.paths["/clients/{id}/openapi.json"]?.get?.description).contains("generated live API"))
        assertTrue(requireNotNull(document.paths["/clients/{id}/actions"]?.get?.description).contains("projection"))
        assertTrue(requireNotNull(document.paths["/clients/{id}/resources"]?.get?.description).contains("handles"))
        assertTrue(requireNotNull(document.paths["/clients/{id}:run"]?.post?.description).contains("advertised action"))
        assertTrue(requireNotNull(document.paths["/clients/{id}:rpc"]?.post?.description).contains("JSON-RPC"))
    }

    @Test
    fun `stable supervisor openapi describes cache export and cleanup`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val exportOperation = document.paths["/cache:export"]?.post
        assertNotNull(exportOperation)
        assertEquals("exportCache", exportOperation.operationId)
        assertEquals("cache", exportOperation.tags.single())
        assertEquals("export", exportOperation.extensions["x-craftless-member"])
        val exportRequestSchema =
            requireNotNull(
                exportOperation.requestBody
                    ?.content
                    ?.get("application/json")
                    ?.schema,
            )
        assertEquals(listOf("manifest"), exportRequestSchema.required)
        assertEquals("string", exportRequestSchema.properties["manifest"]?.type)
        assertEquals("string", exportRequestSchema.properties["archive"]?.type)
        assertEquals(true, exportRequestSchema.properties["archive"]?.nullable)
        val exportResponseSchema = requireNotNull(exportOperation.okSchema())
        assertEquals("string", exportResponseSchema.properties["archive"]?.type)
        assertEquals("array", exportResponseSchema.properties["included"]?.type)
        assertErrorSchema(requireNotNull(exportOperation.errorSchema("400")))

        val cleanupOperation = document.paths["/cache:cleanup"]?.post
        assertNotNull(cleanupOperation)
        assertEquals("cleanupCache", cleanupOperation.operationId)
        assertEquals("cache", cleanupOperation.tags.single())
        assertEquals("cleanup", cleanupOperation.extensions["x-craftless-member"])
        val cleanupRequestSchema =
            requireNotNull(
                cleanupOperation.requestBody
                    ?.content
                    ?.get("application/json")
                    ?.schema,
            )
        assertEquals(listOf("manifest"), cleanupRequestSchema.required)
        assertEquals("string", cleanupRequestSchema.properties["manifest"]?.type)
        val cleanupResponseSchema = requireNotNull(cleanupOperation.okSchema())
        assertEquals("array", cleanupResponseSchema.properties["deleted"]?.type)
        assertEquals("array", cleanupResponseSchema.properties["missing"]?.type)
        assertErrorSchema(requireNotNull(cleanupOperation.errorSchema("400")))
    }

    @Test
    fun `openapi document projects resources from discovered actions`() {
        val document =
            OpenApiDocument.from(
                catalog = ApiRouteCatalog.sessionDefaults(),
                actions =
                    listOf(
                        OpenApiAction(
                            id = "player.query",
                            schemaVersion = "1",
                        ),
                        OpenApiAction(
                            id = "player.raycast",
                            schemaVersion = "1",
                            source = OpenApiActionSource.RUNTIME_PROBE,
                            availability = OpenApiActionAvailability.UNAVAILABLE,
                            availabilityReason = "client-not-connected",
                        ),
                        OpenApiAction(
                            id = "world.block.break",
                            schemaVersion = "1",
                        ),
                    ),
            )

        assertEquals(listOf("player", "world.block"), document.resources.map { it.id })
        val player = document.resources.single { it.id == "player" }
        assertEquals(listOf("player.query", "player.raycast"), player.actions)
        assertEquals(OpenApiResourceAvailability.PARTIAL, player.availability)
        assertEquals(listOf("client-not-connected"), player.availabilityReasons)
        assertEquals(listOf("player.query", "player.raycast"), player.actionDescriptors.map { it.id })
        val raycast = player.actionDescriptors.single { it.id == "player.raycast" }
        assertEquals("1", raycast.schemaVersion)
        assertEquals(OpenApiActionSource.RUNTIME_PROBE, raycast.source)
        assertEquals(OpenApiActionAvailability.UNAVAILABLE, raycast.availability)
        assertEquals("client-not-connected", raycast.availabilityReason)
        val block = document.resources.single { it.id == "world.block" }
        assertEquals(listOf("world.block.break"), block.actions)
        assertEquals(OpenApiResourceAvailability.AVAILABLE, block.availability)
        assertEquals(emptyList(), block.availabilityReasons)
        assertEquals(listOf("world.block.break"), block.actionDescriptors.map { it.id })
    }

    @Test
    fun `openapi document projects actions resources aliases and events from runtime graph`() {
        val graph =
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources =
                    listOf(
                        RuntimeResourceNode("runtime", RuntimeAvailability.available()),
                        RuntimeResourceNode("player", RuntimeAvailability.available()),
                        RuntimeResourceNode(
                            id = "entity",
                            availability = RuntimeAvailability.available(),
                            schema =
                                RuntimeSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "handle" to RuntimeSchema("string"),
                                            "position" to
                                                RuntimeSchema(
                                                    type = "object",
                                                    properties =
                                                        mapOf(
                                                            "x" to RuntimeSchema("number"),
                                                            "y" to RuntimeSchema("number"),
                                                            "z" to RuntimeSchema("number"),
                                                        ),
                                                ),
                                        ),
                                ),
                        ),
                    ),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "player.move",
                            resource = "player",
                            adapter = "fabric.player-move",
                            arguments =
                                mapOf(
                                    "forward" to RuntimeSchema("boolean"),
                                    "target" to
                                        RuntimeSchema(
                                            type = "object",
                                            required = true,
                                            properties =
                                                mapOf(
                                                    "handle" to RuntimeSchema("string"),
                                                    "position" to
                                                        RuntimeSchema(
                                                            type = "object",
                                                            properties =
                                                                mapOf(
                                                                    "x" to RuntimeSchema("integer"),
                                                                    "y" to RuntimeSchema("integer"),
                                                                    "z" to RuntimeSchema("integer"),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                ),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                handles =
                    listOf(
                        RuntimeHandleNode(
                            id = "entity.handle",
                            resource = "entity",
                            schema =
                                RuntimeSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "handle" to RuntimeSchema("string"),
                                            "kind" to RuntimeSchema("string"),
                                        ),
                                ),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                events =
                    listOf(
                        RuntimeEventNode(
                            id = "player.chat.received",
                            resource = "player",
                            payload =
                                RuntimeSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "message" to RuntimeSchema("string"),
                                            "sender" to
                                                RuntimeSchema(
                                                    type = "object",
                                                    properties =
                                                        mapOf(
                                                            "name" to RuntimeSchema("string"),
                                                            "handle" to RuntimeSchema("string"),
                                                        ),
                                                ),
                                        ),
                                ),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )

        val document = OpenApiDocument.fromRuntimeGraph(graph)

        assertEquals(graph.fingerprint(), document.extensions["runtimeGraphFingerprint"])
        assertEquals(listOf("player.move"), document.actions.map { it.id })
        val move = document.actions.single()
        assertEquals(OpenApiActionSource.RUNTIME_PROBE, move.source)
        assertEquals(OpenApiActionAvailability.AVAILABLE, move.availability)
        assertEquals("boolean", move.arguments.getValue("forward").type)
        val targetArgument = move.arguments.getValue("target")
        val positionArgument = targetArgument.properties["position"]
        assertEquals("object", targetArgument.type)
        assertEquals(true, targetArgument.required)
        assertEquals("string", targetArgument.properties["handle"]?.type)
        assertEquals("object", positionArgument?.type)
        assertEquals("integer", positionArgument?.properties?.get("x")?.type)
        val moveRequestSchema =
            document.paths["/clients/{id}/player:move"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(moveRequestSchema)
        val moveTargetSchema = moveRequestSchema.properties["target"]
        val movePositionSchema = moveTargetSchema?.properties?.get("position")
        assertEquals(listOf("target"), moveRequestSchema.required)
        assertEquals("object", moveTargetSchema?.type)
        assertEquals("string", moveTargetSchema?.properties?.get("handle")?.type)
        assertEquals("object", movePositionSchema?.type)
        assertEquals("integer", movePositionSchema?.properties?.get("x")?.type)
        assertEquals(listOf("runtime", "player", "entity"), document.resources.map { it.id })
        val entityResource = document.resources.single { it.id == "entity" }
        assertEquals("object", entityResource.schema.type)
        assertEquals("string", entityResource.schema.properties["handle"]?.type)
        val entityPositionSchema = entityResource.schema.properties["position"]
        assertEquals("object", entityPositionSchema?.type)
        assertEquals("number", entityPositionSchema?.properties?.get("x")?.type)
        val projectedEntityHandle = document.handles.single { it.id == "entity.handle" }
        assertEquals("object", projectedEntityHandle.schema.type)
        assertEquals("string", projectedEntityHandle.schema.properties["handle"]?.type)
        assertEquals("string", projectedEntityHandle.schema.properties["kind"]?.type)
        val chatEvent = document.events.single { it.id == "player.chat.received" }
        assertEquals("object", chatEvent.payload.type)
        assertEquals("string", chatEvent.payload.properties["message"]?.type)
        val senderSchema = chatEvent.payload.properties["sender"]
        assertEquals("object", senderSchema?.type)
        assertEquals("string", senderSchema?.properties?.get("name")?.type)
        assertEquals(emptyList(), document.resources.single { it.id == "runtime" }.actions)
        assertEquals(listOf("player.move"), document.resources.single { it.id == "player" }.actions)
        assertEquals(listOf("entity.handle"), document.handles.map { it.id })
        val entityHandle = document.handles.single()
        assertEquals("entity", entityHandle.resource)
        assertEquals("object", entityHandle.schema.type)
        assertEquals(OpenApiActionAvailability.AVAILABLE, entityHandle.availability)
        assertEquals(listOf("player.chat.received"), document.events.map { it.id })
        assertEquals("/clients/{id}/player:move", document.paths.keys.single { it.endsWith("player:move") })
        assertEquals(
            "player.move",
            document.paths
                .getValue("/clients/{id}/player:move")
                .post
                ?.extensions
                ?.get("x-craftless-action"),
        )
    }

    @Test
    fun `openapi document projects navigation and task operations from runtime graph without backend leaks`() {
        val graph =
            RuntimeCapabilityGraph(
                clientId = "alice",
                resources =
                    listOf(
                        RuntimeResourceNode(
                            id = "navigation",
                            availability = RuntimeAvailability.available(),
                            sourceEvidence =
                                listOf(
                                    RuntimeSourceEvidence(
                                        kind = "mod",
                                        fingerprint = "baritone.api.BaritoneAPI",
                                    ),
                                ),
                        ),
                    ),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "navigation.plan",
                            resource = "navigation",
                            adapter = "navigation.default",
                            arguments = mapOf("goal" to RuntimeSchema("object", required = true)),
                            result = RuntimeSchema("object", required = true),
                            availability = RuntimeAvailability.available(),
                        ),
                        RuntimeOperationNode(
                            id = "navigation.follow",
                            resource = "navigation",
                            adapter = "navigation.default",
                            arguments = mapOf("plan" to RuntimeSchema("object", required = true)),
                            result = RuntimeSchema("object", required = true),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )

        val document = OpenApiDocument.fromRuntimeGraph(graph)
        val encoded = Json.encodeToString(OpenApiDocument.serializer(), document)

        assertEquals(
            listOf("navigation.follow", "navigation.plan"),
            document.actions.map { it.id },
        )
        assertEquals(listOf("navigation"), document.resources.map { it.id })
        assertEquals("/clients/{id}/navigation:plan", document.paths.keys.single { it.endsWith("navigation:plan") })
        assertFalse(document.paths.keys.any { it.contains("/task:") })
        assertFalse(document.events.any { it.id.startsWith("task.") })
        assertFalse(encoded.contains("baritone", ignoreCase = true))
        assertFalse(encoded.contains("swarmbot", ignoreCase = true))
    }

    @Test
    fun `runtime graph rejects navigation backend names before openapi projection`() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeOperationNode(
                id = "navigation.baritone",
                resource = "navigation",
                adapter = "navigation.default",
                availability = RuntimeAvailability.available(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeOperationNode(
                id = "task.swarmbot",
                resource = "task",
                adapter = "task.executor",
                availability = RuntimeAvailability.available(),
            )
        }
    }

    @Test
    fun `openapi document rejects duplicate action ids`() {
        val action =
            OpenApiAction(
                id = "player.chat",
                schemaVersion = "1",
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                OpenApiDocument.from(
                    catalog = ApiRouteCatalog.sessionDefaults(),
                    actions = listOf(action, action.copy(schemaVersion = "2")),
                )
            }

        assertEquals("duplicate action id player.chat", error.message)
    }

    @Test
    fun `openapi document rejects action alias routes without matching action metadata`() {
        val error =
            assertFailsWith<IllegalArgumentException> {
                OpenApiDocument.from(
                    catalog =
                        ApiRouteCatalog(
                            listOf(
                                ApiRoute(
                                    method = "POST",
                                    path = "/clients/{id}/world:scan",
                                    operationId = "runWorldScan",
                                    tag = "clients",
                                    owner = "clients",
                                    member = "run",
                                    target = "client",
                                    source = "action",
                                    actionId = "world.scan",
                                ),
                            ),
                        ),
                    actions =
                        listOf(
                            OpenApiAction(
                                id = "player.chat",
                                schemaVersion = "1",
                            ),
                        ),
                )
            }

        assertEquals("action route runWorldScan references unknown action world.scan", error.message)
    }

    @Test
    fun `openapi action metadata rejects invalid action ids`() {
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
                OpenApiAction(
                    id = actionId,
                    schemaVersion = "1",
                )
            }
        }
    }

    @Test
    fun `openapi action metadata carries discovery source and availability`() {
        val action =
            OpenApiAction(
                id = "player.raycast",
                schemaVersion = "1",
                source = OpenApiActionSource.RUNTIME_PROBE,
                availability = OpenApiActionAvailability.UNAVAILABLE,
                availabilityReason = "client-not-connected",
            )

        assertEquals(OpenApiActionSource.RUNTIME_PROBE, action.source)
        assertEquals(OpenApiActionAvailability.UNAVAILABLE, action.availability)
        assertEquals("client-not-connected", action.availabilityReason)
    }

    @Test
    fun `unavailable openapi actions require machine readable availability reason`() {
        assertFailsWith<IllegalArgumentException> {
            OpenApiAction(
                id = "player.raycast",
                schemaVersion = "1",
                source = OpenApiActionSource.RUNTIME_PROBE,
                availability = OpenApiActionAvailability.UNAVAILABLE,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OpenApiAction(
                id = "player.raycast",
                schemaVersion = "1",
                source = OpenApiActionSource.RUNTIME_PROBE,
                availability = OpenApiActionAvailability.UNAVAILABLE,
                availabilityReason = "client is not connected",
            )
        }
    }

    @Test
    fun `openapi action metadata rejects invalid argument metadata`() {
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
                OpenApiAction(
                    id = "player.move",
                    schemaVersion = "1",
                    arguments = mapOf(argumentName to OpenApiActionArgument("boolean")),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            OpenApiAction(
                id = "player.move",
                schemaVersion = "1",
                arguments = mapOf("forward" to OpenApiActionArgument("")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OpenApiActionArgument("minecraft-block-pos")
        }
    }

    @Test
    fun `stable lifecycle routes describe create and connect request bodies`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
        val versionOperation = document.paths["/version"]?.get
        assertNotNull(versionOperation)
        assertEquals("supervisor", versionOperation.extensions["x-craftless-target"])

        val createSchema =
            document.paths["/clients"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(createSchema)
        assertEquals("object", createSchema.type)
        assertEquals(listOf("id", "version", "loader"), createSchema.required)
        assertEquals("string", createSchema.properties["id"]?.type)
        assertEquals("string", createSchema.properties["version"]?.type)
        assertEquals("string", createSchema.properties["loader"]?.type)
        assertEquals("string", createSchema.properties["loaderVersion"]?.type)
        assertEquals(true, createSchema.properties["loaderVersion"]?.nullable)
        val presentationSchema = createSchema.properties["presentation"]
        assertNotNull(presentationSchema)
        assertEquals("object", presentationSchema.type)
        assertEquals(listOf("window", "audio"), presentationSchema.required)
        val windowSchema = presentationSchema.properties["window"]
        assertNotNull(windowSchema)
        assertEquals("string", windowSchema.type)
        assertEquals(listOf("NONE", "VISIBLE"), windowSchema.enumValues)
        assertEquals("NONE", windowSchema.default)
        val audioSchema = presentationSchema.properties["audio"]
        assertNotNull(audioSchema)
        assertEquals("string", audioSchema.type)
        assertEquals(listOf("MUTED", "DEFAULT"), audioSchema.enumValues)
        assertEquals("MUTED", audioSchema.default)
        val profileSchema = createSchema.properties["profile"]
        assertNotNull(profileSchema)
        assertEquals("object", profileSchema.type)
        assertEquals(listOf("kind", "name"), profileSchema.required)
        assertEquals("string", profileSchema.properties["kind"]?.type)
        assertEquals("string", profileSchema.properties["name"]?.type)

        val connectSchema =
            document.paths["/clients/{id}:connect"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(connectSchema)
        assertEquals("object", connectSchema.type)
        assertEquals(listOf("host", "port"), connectSchema.required)
        assertEquals("string", connectSchema.properties["host"]?.type)
        assertEquals("integer", connectSchema.properties["port"]?.type)

        val attachSchema =
            document.paths["/clients/{id}:attach"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(attachSchema)
        assertEquals("object", attachSchema.type)
        assertEquals(listOf("endpoint"), attachSchema.required)
        assertEquals("string", attachSchema.properties["endpoint"]?.type)
    }

    @Test
    fun `supervisor openapi exposes cli metadata for stable routes`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val create = requireNotNull(document.paths["/clients"]?.post?.cli)
        assertEquals(listOf("clients", "create", "{id}"), create.command)
        assertFalse(create.hidden)
        assertFalse(create.stream)
        val createBindings = requireNotNull(create.body).bindings
        assertEquals("/id", createBindings.single { it.flag == null && it.argument == "id" }.pointer)
        assertEquals("/version", createBindings.single { it.flag == "--version" }.pointer)
        assertEquals("/loader", createBindings.single { it.flag == "--loader" }.pointer)
        assertEquals("/loaderVersion", createBindings.single { it.flag == "--loader-version" }.pointer)
        assertEquals("/profile/name", createBindings.single { it.flag == "--offline-name" && it.fixed == null }.pointer)
        assertEquals("/profile/kind", createBindings.single { it.fixed == "OFFLINE" }.pointer)
        assertEquals("/presentation/window", createBindings.single { it.flag == "--visible" }.pointer)
        assertEquals("VISIBLE", createBindings.single { it.flag == "--visible" }.fixed)
        assertEquals("/presentation/audio", createBindings.single { it.flag == "--audio" }.pointer)

        val connect = requireNotNull(document.paths["/clients/{id}:connect"]?.post?.cli)
        assertEquals(listOf("clients", "{id}", "connect"), connect.command)
        val connectBindings = requireNotNull(connect.body).bindings
        assertEquals("/host", connectBindings.single { it.flag == "--host" }.pointer)
        assertEquals("/port", connectBindings.single { it.flag == "--port" }.pointer)

        val stream = requireNotNull(document.paths["/clients/{id}/events:stream"]?.get?.cli)
        assertEquals(listOf("clients", "{id}", "events"), stream.command)
        assertTrue(stream.stream)

        val eventList = requireNotNull(document.paths["/clients/{id}/events"]?.get?.cli)
        assertEquals(listOf("clients", "{id}", "events", "list"), eventList.command)

        val runtimes = requireNotNull(document.paths["/runtimes/java"]?.get?.cli)
        assertEquals(listOf("runtimes", "java", "list"), runtimes.command)
    }

    @Test
    fun `stable lifecycle routes describe client response bodies`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val listSchema = document.paths["/clients"]?.get?.okSchema()
        assertNotNull(listSchema)
        assertEquals("array", listSchema.type)
        val listItemSchema = listSchema.items
        assertNotNull(listItemSchema)
        assertClientSchema(listItemSchema)

        assertClientSchema(requireNotNull(document.paths["/clients"]?.post?.successSchema("201")))
        assertClientSchema(requireNotNull(document.paths["/clients/{id}"]?.get?.okSchema()))
        assertClientSchema(requireNotNull(document.paths["/clients/{id}:attach"]?.post?.okSchema()))
        assertClientSchema(requireNotNull(document.paths["/clients/{id}:connect"]?.post?.okSchema()))
        assertClientSchema(requireNotNull(document.paths["/clients/{id}:stop"]?.post?.okSchema()))
    }

    @Test
    fun `stable discovery routes describe metadata action and event response bodies`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        assertOpenApiDocumentSchema(requireNotNull(document.paths["/openapi.json"]?.get?.okSchema()))
        assertOpenApiDocumentSchema(requireNotNull(document.paths["/clients/{id}/openapi.json"]?.get?.okSchema()))

        val versionSchema = requireNotNull(document.paths["/version"]?.get?.okSchema())
        assertEquals("object", versionSchema.type)
        assertEquals(
            listOf(
                "minecraft",
                "loader",
                "loaderVersion",
                "driver",
                "driverVersion",
                "java",
                "mappingsFingerprint",
                "openapiGeneratedAt",
            ),
            versionSchema.required,
        )
        assertEquals("string", versionSchema.properties["minecraft"]?.type)
        assertEquals("string", versionSchema.properties["mappingsFingerprint"]?.type)
        assertEquals("string", versionSchema.properties["openapiGeneratedAt"]?.type)

        val eventsSchema = requireNotNull(document.paths["/events"]?.get?.okSchema())
        assertEventListSchema(eventsSchema)
        assertEventListSchema(requireNotNull(document.paths["/clients/{id}/events"]?.get?.okSchema()))

        val actionsSchema = requireNotNull(document.paths["/clients/{id}/actions"]?.get?.okSchema())
        assertEquals("array", actionsSchema.type)
        val actionSchema = requireNotNull(actionsSchema.items)
        assertEquals("object", actionSchema.type)
        assertEquals(listOf("id", "schemaVersion", "source", "availability"), actionSchema.required)
        assertEquals("string", actionSchema.properties["id"]?.type)
        assertEquals("string", actionSchema.properties["schemaVersion"]?.type)
        assertEquals("string", actionSchema.properties["source"]?.type)
        assertEquals("string", actionSchema.properties["availability"]?.type)
        assertEquals("string", actionSchema.properties["availabilityReason"]?.type)
        assertEquals(true, actionSchema.properties["availabilityReason"]?.nullable)
        assertEquals("object", actionSchema.properties["args"]?.type)
        assertEquals(true, actionSchema.properties["args"]?.additionalProperties)
        val resultSchema = actionSchema.properties["result"]
        assertNotNull(resultSchema)
        assertEquals("object", resultSchema.type)
        assertEquals(true, resultSchema.additionalProperties)
    }

    @Test
    fun `stable routes describe machine readable error responses`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        assertErrorSchema(requireNotNull(document.paths["/clients"]?.post?.errorSchema("400")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}"]?.get?.errorSchema("404")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}/actions"]?.get?.errorSchema("404")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:connect"]?.post?.errorSchema("400")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:connect"]?.post?.errorSchema("404")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:connect"]?.post?.errorSchema("409")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:attach"]?.post?.errorSchema("400")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:attach"]?.post?.errorSchema("404")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:run"]?.post?.errorSchema("400")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:run"]?.post?.errorSchema("404")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:run"]?.post?.errorSchema("409")))
        assertErrorSchema(requireNotNull(document.paths["/clients/{id}:stop"]?.post?.errorSchema("404")))
    }

    private fun OpenApiOperation.okSchema(): OpenApiSchema? = successSchema("200")

    private fun OpenApiOperation.successSchema(status: String): OpenApiSchema? = responses[status]?.content?.get("application/json")?.schema

    private fun OpenApiOperation.errorSchema(status: String): OpenApiSchema? = responses[status]?.content?.get("application/json")?.schema

    private fun assertErrorSchema(schema: OpenApiSchema) {
        assertEquals("object", schema.type)
        assertEquals(listOf("code", "message"), schema.required)
        assertEquals("string", schema.properties["code"]?.type)
        assertEquals("string", schema.properties["message"]?.type)
    }

    private fun assertEventListSchema(schema: OpenApiSchema) {
        assertEquals("array", schema.type)
        val eventSchema = requireNotNull(schema.items)
        assertEquals("object", eventSchema.type)
        assertEquals(listOf("type", "time"), eventSchema.required)
        assertEquals("string", eventSchema.properties["type"]?.type)
        assertEquals("string", eventSchema.properties["client"]?.type)
        assertEquals("string", eventSchema.properties["message"]?.type)
        assertEquals("string", eventSchema.properties["time"]?.type)
    }

    private fun assertOpenApiDocumentSchema(schema: OpenApiSchema) {
        assertEquals("object", schema.type)
        assertEquals(listOf("openapi", "info", "paths"), schema.required)
        assertEquals("string", schema.properties["openapi"]?.type)
        assertEquals("object", schema.properties["info"]?.type)
        assertEquals("object", schema.properties["paths"]?.type)
        assertEquals(true, schema.properties["paths"]?.additionalProperties)
        assertEquals("object", schema.properties["x-craftless"]?.type)
        assertEquals(true, schema.properties["x-craftless"]?.additionalProperties)
        assertEquals("array", schema.properties["x-craftless-actions"]?.type)
        assertEquals("array", schema.properties["x-craftless-resources"]?.type)
    }

    private fun assertClientSchema(schema: OpenApiSchema) {
        assertEquals("object", schema.type)
        assertEquals(listOf("id", "instance", "profile", "presentation", "state"), schema.required)
        assertEquals("string", schema.properties["id"]?.type)
        assertEquals("string", schema.properties["state"]?.type)

        val instanceSchema = schema.properties["instance"]
        assertNotNull(instanceSchema)
        assertEquals("object", instanceSchema.type)
        assertEquals(listOf("id", "version", "loader", "files"), instanceSchema.required)
        assertEquals("string", instanceSchema.properties["id"]?.type)
        assertEquals("string", instanceSchema.properties["loader"]?.type)
        val filesSchema = instanceSchema.properties["files"]
        assertNotNull(filesSchema)
        assertEquals("object", filesSchema.type)
        assertEquals(
            listOf(
                "root",
                "gameRoot",
                "runtimeRoot",
                "cache",
                "mods",
                "config",
                "saves",
                "resourcePacks",
                "shaderPacks",
                "screenshots",
                "logs",
                "artifacts",
            ),
            filesSchema.required,
        )
        assertTrue(filesSchema.properties.keys.none { it.contains("mmc", ignoreCase = true) })
        assertTrue(filesSchema.properties.keys.none { it.contains("prism", ignoreCase = true) })
        assertTrue(filesSchema.properties.keys.none { it.contains("cfg", ignoreCase = true) })
        val versionSchema = instanceSchema.properties["version"]
        assertNotNull(versionSchema)
        assertEquals("object", versionSchema.type)
        assertEquals(listOf("id"), versionSchema.required)
        assertEquals("string", versionSchema.properties["id"]?.type)

        val profileSchema = schema.properties["profile"]
        assertNotNull(profileSchema)
        assertEquals("object", profileSchema.type)
        assertEquals(listOf("kind", "name"), profileSchema.required)
        assertEquals("string", profileSchema.properties["kind"]?.type)
        assertEquals("string", profileSchema.properties["name"]?.type)
        val presentationSchema = schema.properties["presentation"]
        assertNotNull(presentationSchema)
        assertEquals("object", presentationSchema.type)
        assertEquals(listOf("window", "audio"), presentationSchema.required)
        val windowSchema = presentationSchema.properties["window"]
        assertNotNull(windowSchema)
        assertEquals("string", windowSchema.type)
        assertEquals(listOf("NONE", "VISIBLE"), windowSchema.enumValues)
        assertEquals("NONE", windowSchema.default)
        val audioSchema = presentationSchema.properties["audio"]
        assertNotNull(audioSchema)
        assertEquals("string", audioSchema.type)
        assertEquals(listOf("MUTED", "DEFAULT"), audioSchema.enumValues)
        assertEquals("MUTED", audioSchema.default)
    }
}
