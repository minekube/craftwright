package com.minekube.craftless.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiGenerationTest {
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
                            result =
                                OpenApiActionResult(
                                    properties =
                                        mapOf(
                                            "action" to OpenApiActionSchema("string"),
                                            "blocks" to OpenApiActionSchema("array"),
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
        assertEquals("array", aliasResponseSchema.properties["blocks"]?.type)
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
                        RuntimeResourceNode("entity", RuntimeAvailability.available()),
                    ),
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "player.move",
                            resource = "player",
                            adapter = "fabric.player-move",
                            arguments = mapOf("forward" to RuntimeSchema("boolean")),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                handles =
                    listOf(
                        RuntimeHandleNode(
                            id = "entity.handle",
                            resource = "entity",
                            schema = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                events =
                    listOf(
                        RuntimeEventNode(
                            id = "player.chat.received",
                            resource = "player",
                            payload = RuntimeSchema.objectSchema(),
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
        assertEquals(listOf("runtime", "player", "entity"), document.resources.map { it.id })
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
                        RuntimeResourceNode("task", RuntimeAvailability.available()),
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
                        RuntimeOperationNode(
                            id = "task.run",
                            resource = "task",
                            adapter = "task.executor",
                            arguments = mapOf("request" to RuntimeSchema("object", required = true)),
                            result = RuntimeSchema("object", required = true),
                            availability = RuntimeAvailability.available(),
                        ),
                        RuntimeOperationNode(
                            id = "task.status",
                            resource = "task",
                            adapter = "task.executor",
                            arguments = mapOf("task" to RuntimeSchema("string", required = true)),
                            result = RuntimeSchema("object", required = true),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
                events =
                    listOf(
                        RuntimeEventNode(
                            id = "task.progress",
                            resource = "task",
                            payload = RuntimeSchema.objectSchema(),
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            )

        val document = OpenApiDocument.fromRuntimeGraph(graph)
        val encoded = Json.encodeToString(OpenApiDocument.serializer(), document)

        assertEquals(
            listOf("navigation.follow", "navigation.plan", "task.run", "task.status"),
            document.actions.map { it.id },
        )
        assertEquals(listOf("navigation", "task"), document.resources.map { it.id })
        assertEquals("/clients/{id}/navigation:plan", document.paths.keys.single { it.endsWith("navigation:plan") })
        assertEquals("/clients/{id}/task:run", document.paths.keys.single { it.endsWith("task:run") })
        assertEquals(listOf("task.progress"), document.events.map { it.id })
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
        assertEquals(listOf("id", "version", "loader", "profile"), createSchema.required)
        assertEquals("string", createSchema.properties["id"]?.type)
        assertEquals("string", createSchema.properties["version"]?.type)
        assertEquals("string", createSchema.properties["loader"]?.type)
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
        assertEquals(listOf("id", "instance", "profile", "state"), schema.required)
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
    }
}
