package com.minekube.craftwright.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenApiGenerationTest {
    @Test
    fun `openapi document includes craftwright metadata for generic action route`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val operation = document.paths["/clients/{id}:run"]?.post
        assertNotNull(operation)
        assertEquals("runClientAction", operation.operationId)
        assertEquals("clients", operation.tags.single())
        assertEquals("com.minekube.craftwright.daemon.clients", operation.extensions["x-craftwright-java-class"])
        assertEquals("run", operation.extensions["x-craftwright-java-method"])
        assertEquals("client", operation.extensions["x-craftwright-thread"])
        assertEquals("action", operation.extensions["x-craftwright-source"])
        val schema = operation.requestBody?.content?.get("application/json")?.schema
        assertNotNull(schema)
        assertEquals("object", schema.type)
        assertEquals(listOf("action"), schema.required)
        assertEquals("string", schema.properties["action"]?.type)
        assertEquals("object", schema.properties["args"]?.type)
        val responseSchema = operation.responses["200"]
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(responseSchema)
        assertEquals("object", responseSchema.type)
        assertEquals(listOf("action", "status"), responseSchema.required)
        assertEquals("string", responseSchema.properties["action"]?.type)
        assertEquals("string", responseSchema.properties["status"]?.type)
        assertEquals("string", responseSchema.properties["message"]?.type)
    }

    @Test
    fun `stable lifecycle routes describe create and connect request bodies`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val createSchema = document.paths["/clients"]?.post
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

        val connectSchema = document.paths["/clients/{id}/connection/connect"]?.post
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
        assertClientSchema(requireNotNull(document.paths["/clients/{id}/connection/connect"]?.post?.okSchema()))
        assertClientSchema(requireNotNull(document.paths["/clients/{id}/stop"]?.post?.okSchema()))
    }

    private fun OpenApiOperation.okSchema(): OpenApiSchema? =
        successSchema("200")

    private fun OpenApiOperation.successSchema(status: String): OpenApiSchema? =
        responses[status]?.content?.get("application/json")?.schema

    private fun assertClientSchema(schema: OpenApiSchema) {
        assertEquals("object", schema.type)
        assertEquals(listOf("id", "instance", "profile", "state"), schema.required)
        assertEquals("string", schema.properties["id"]?.type)
        assertEquals("string", schema.properties["state"]?.type)

        val instanceSchema = schema.properties["instance"]
        assertNotNull(instanceSchema)
        assertEquals("object", instanceSchema.type)
        assertEquals(listOf("id", "version", "loader"), instanceSchema.required)
        assertEquals("string", instanceSchema.properties["id"]?.type)
        assertEquals("string", instanceSchema.properties["loader"]?.type)
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
