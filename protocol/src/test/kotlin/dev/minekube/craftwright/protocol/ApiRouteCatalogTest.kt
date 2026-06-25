package dev.minekube.craftwright.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRouteCatalogTest {
    @Test
    fun `catalog exposes required stable session routes`() {
        val catalog = ApiRouteCatalog.sessionDefaults()

        assertEquals(HttpMethod.GET, catalog.route("/openapi.json").method)
        assertEquals(HttpMethod.GET, catalog.route("/version").method)
        assertEquals(HttpMethod.GET, catalog.route("/events").method)
        assertEquals(HttpMethod.GET, catalog.route("/client").method)
        assertEquals(HttpMethod.GET, catalog.route("/client/state").method)
        assertEquals(HttpMethod.GET, catalog.route("/player").method)
        assertEquals(HttpMethod.GET, catalog.route("/player/name").method)
        assertEquals(HttpMethod.POST, catalog.route("/player/sendChat").method)
        assertEquals(HttpMethod.GET, catalog.route("/connection").method)
        assertTrue(catalog.routes.any { it.path == "/o/{handle}" })
        assertTrue(catalog.routes.any { it.path == "/c/{className}" })
    }
}
