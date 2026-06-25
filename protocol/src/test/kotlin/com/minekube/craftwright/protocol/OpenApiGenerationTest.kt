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
}
