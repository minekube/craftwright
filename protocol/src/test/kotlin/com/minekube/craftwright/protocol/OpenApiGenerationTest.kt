package com.minekube.craftwright.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenApiGenerationTest {
    @Test
    fun `openapi document includes craftwright metadata for fake player routes`() {
        val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

        val operation = document.paths["/player/sendChat"]?.post
        assertNotNull(operation)
        assertEquals("playerSendChat", operation.operationId)
        assertEquals("player", operation.tags.single())
        assertEquals("com.minekube.craftwright.player", operation.extensions["x-craftwright-java-class"])
        assertEquals("sendChat", operation.extensions["x-craftwright-java-method"])
        assertEquals("client", operation.extensions["x-craftwright-thread"])
        assertEquals("method", operation.extensions["x-craftwright-source"])
    }
}
