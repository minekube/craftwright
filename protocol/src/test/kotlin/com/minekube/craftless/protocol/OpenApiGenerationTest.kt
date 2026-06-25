package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(listOf("id", "schemaVersion"), actionSchema.required)
        assertEquals("string", actionSchema.properties["id"]?.type)
        assertEquals("string", actionSchema.properties["schemaVersion"]?.type)
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
            listOf("root", "gameRoot", "mods", "config", "saves", "resourcePacks", "shaderPacks"),
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
