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
    }
}
