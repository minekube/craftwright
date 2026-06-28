package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HttpDriverSessionTest {
    @Test
    fun `http driver session does not fetch actions endpoint as remote authority`() {
        val source = Files.readString(repositoryRoot().resolve("daemon/src/main/kotlin/com/minekube/craftless/daemon/HttpDriverSession.kt"))

        assertFalse(source.contains("get(\"actions\")"))
    }

    @Test
    fun `remote actions derive from runtime graph without calling actions endpoint`() {
        RuntimeGraphOnlyEndpoint("alice").use { endpoint ->
            endpoint.start()

            val remote = HttpDriverSession(clientId = "alice", endpoint = endpoint.url)
            val actions = remote.actions()

            assertEquals(listOf("player.chat"), actions.map { action -> action.id })
            assertEquals(0, endpoint.actionsRequests)
        }
    }

    private fun repositoryRoot(): Path {
        var current: Path? = Path.of("").toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("repository root not found")
    }
}

private class RuntimeGraphOnlyEndpoint(
    private val clientId: String,
) : AutoCloseable {
    private val json = Json { encodeDefaults = true }
    private val port = allocateLoopbackPort()
    private val engine =
        embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing {
                get("/runtime-graph") {
                    call.respondJson(runtimeGraph())
                }
                get("/actions") {
                    actionsRequests += 1
                    call.respondText(
                        "actions endpoint is a projection, not remote session authority",
                        ContentType.Text.Plain,
                        HttpStatusCode.InternalServerError,
                    )
                }
            }
        }

    var actionsRequests: Int = 0
        private set

    val url: String = "http://127.0.0.1:$port"

    fun start() {
        engine.start(wait = false)
    }

    override fun close() {
        engine.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
    }

    private fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "player.chat",
                        arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    private suspend inline fun <reified T> ApplicationCall.respondJson(value: T) {
        respondText(json.encodeToString(value), ContentType.Application.Json)
    }

    private fun allocateLoopbackPort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
}
