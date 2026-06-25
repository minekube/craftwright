package com.minekube.craftwright.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRouteCatalogTest {
    @Test
    fun `catalog exposes required stable session routes`() {
        val catalog = ApiRouteCatalog.sessionDefaults()

        assertEquals("GET", catalog.route("/openapi.json").method)
        assertEquals("GET", catalog.route("/version").method)
        assertEquals("GET", catalog.route("/events").method)
        assertEquals("GET", catalog.route("/client").method)
        assertEquals("GET", catalog.route("/client/state").method)
        assertEquals("GET", catalog.route("/player").method)
        assertEquals("GET", catalog.route("/player/name").method)
        assertEquals("GET", catalog.route("/player/position").method)
        assertEquals("POST", catalog.route("/player/sendChat").method)
        assertEquals("POST", catalog.route("/clients/{id}/connection/connect").method)
        assertEquals("POST", catalog.route("/clients/{id}/player/sendChat").method)
        assertEquals("GET", catalog.route("/clients/{id}/player").method)
        assertEquals("GET", catalog.route("/clients/{id}/player/position").method)
        assertEquals("POST", catalog.route("/clients/{id}/capabilities/{capability}").method)
        assertEquals("POST", catalog.route("/clients/{id}/stop").method)
        assertEquals("GET", catalog.route("/connection").method)
        assertTrue(catalog.routes.any { it.path == "/o/{handle}" })
        assertTrue(catalog.routes.any { it.path == "/c/{className}" })
    }
}
