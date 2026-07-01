package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiRouteCatalogTest {
    @Test
    fun `catalog exposes required stable session routes`() {
        val catalog = ApiRouteCatalog.sessionDefaults()

        assertEquals("GET", catalog.route("/openapi.json").method)
        assertEquals("GET", catalog.route("/version").method)
        assertEquals("GET", catalog.route("/events").method)
        assertEquals("GET", catalog.route("/events:stream").method)
        assertEquals("GET", catalog.route("/versions/runtime-targets").method)
        assertEquals("GET", catalog.route("/versions/loader-targets").method)
        assertEquals("GET", catalog.route("/versions/loaders").method)
        assertEquals("GET", catalog.route("/versions/driver-mods").method)
        assertEquals("GET", catalog.route("/versions/support-targets").method)
        assertEquals("POST", catalog.route("/cache:prepare").method)
        assertEquals("POST", catalog.route("/cache:export").method)
        assertEquals("POST", catalog.route("/cache:cleanup").method)
        assertEquals("GET", catalog.route("/runtimes/java").method)
        assertEquals("POST", catalog.route("/runtimes/java:resolve").method)
        assertEquals("GET", catalog.route("GET", "/clients").method)
        assertEquals("POST", catalog.route("POST", "/clients").method)
        assertEquals("GET", catalog.route("GET", "/clients/{id}").method)
        assertEquals("POST", catalog.route("/clients/{id}:connect").method)
        assertEquals("GET", catalog.route("/clients/{id}/openapi.json").method)
        assertEquals("GET", catalog.route("/clients/{id}/actions").method)
        assertEquals("POST", catalog.route("/clients/{id}:run").method)
        assertEquals("POST", catalog.route("/clients/{id}:stop").method)
        assertEquals("GET", catalog.route("/clients/{id}/events").method)
        assertEquals("GET", catalog.route("/clients/{id}/events:stream").method)
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/connection/connect" })
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/stop" })
        assertTrue(catalog.routes.none { it.path == "/client" })
        assertTrue(catalog.routes.none { it.path == "/client/state" })
        assertTrue(catalog.routes.none { it.path == "/connection" })
        assertTrue(catalog.routes.none { it.path == "/player" })
        assertTrue(catalog.routes.none { it.path == "/player/position" })
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/player" })
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/player/position" })
        assertTrue(catalog.routes.none { it.path.startsWith("/o/") })
        assertTrue(catalog.routes.none { it.path.startsWith("/c/") })
    }

    @Test
    fun `stable session route catalog keeps gameplay out of supervisor routes`() {
        val catalog = ApiRouteCatalog.sessionDefaults()
        val generatedAliasPattern = Regex("""^/clients/\{id}/[^/]+:[^/]+$""")

        assertTrue(catalog.routes.none { it.path.matches(generatedAliasPattern) && it.path != "/clients/{id}/events:stream" })
        assertTrue(catalog.routes.none { it.actionId != null })
        assertTrue(catalog.routes.none { it.operationId == "runPlayerChat" })
        assertTrue(catalog.routes.none { it.operationId == "runPlayerMove" })
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/player:chat" })
        assertTrue(catalog.routes.none { it.path == "/clients/{id}/player:move" })
    }

    @Test
    fun `catalog rejects path only lookup for ambiguous route paths`() {
        val catalog = ApiRouteCatalog.sessionDefaults()

        val error =
            assertFailsWith<IllegalStateException> {
                catalog.route("/clients")
            }

        assertTrue(error.message!!.contains("ambiguous route path /clients"))
        assertEquals("GET", catalog.route("GET", "/clients").method)
        assertEquals("POST", catalog.route("POST", "/clients").method)
    }

    @Test
    fun `catalog rejects duplicate method and path routes`() {
        val route =
            ApiRoute(
                method = "GET",
                path = "/clients",
                operationId = "listClients",
                tag = "clients",
                owner = "clients",
                member = "list",
                source = "route",
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                ApiRouteCatalog(listOf(route, route.copy(operationId = "listClientsDuplicate")))
            }

        assertTrue(error.message!!.contains("duplicate route GET /clients"))
    }

    @Test
    fun `route metadata rejects invalid protocol fields`() {
        val route =
            ApiRoute(
                method = "GET",
                path = "/clients/{id}:run",
                operationId = "runClientAction",
                tag = "clients",
                owner = "clients",
                member = "run",
                target = "client",
                source = "action",
                actionId = "player.move",
            )

        assertFailsWith<IllegalArgumentException> { route.copy(method = "PUT") }
        assertFailsWith<IllegalArgumentException> { route.copy(method = "get") }
        assertFailsWith<IllegalArgumentException> { route.copy(path = "clients/{id}:run") }
        assertFailsWith<IllegalArgumentException> { route.copy(operationId = "") }
        assertFailsWith<IllegalArgumentException> { route.copy(target = "bridge") }
        assertFailsWith<IllegalArgumentException> { route.copy(source = "bridge") }
        assertFailsWith<IllegalArgumentException> { route.copy(returnKind = "thread") }
        assertFailsWith<IllegalArgumentException> { route.copy(actionId = "player:move") }
    }
}
