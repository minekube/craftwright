package com.minekube.craftwright.daemon

import com.minekube.craftwright.protocol.Client
import com.minekube.craftwright.protocol.ClientState
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.Loader
import com.minekube.craftwright.protocol.Profile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSessionApiServerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `server exposes session metadata and creates fake clients over http`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory().use { server ->
            server.start()

            http.get(server.url("/version")).let { version ->
                assertEquals(HttpStatusCode.OK, version.status)
                assertTrue(version.bodyAsText().contains("\"driver\":\"craftwright-daemon\""))
            }

            http.get(server.url("/openapi.json")).let { openapi ->
                assertEquals(HttpStatusCode.OK, openapi.status)
                assertTrue(openapi.bodyAsText().contains("/player/sendChat"))
            }

            http.get(server.url("/events")).let { events ->
                assertEquals(HttpStatusCode.OK, events.status)
                assertEquals("[]", events.bodyAsText())
            }

            http.post(server.url("/clients")) {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                {
                  "id": "alice",
                  "version": "1.21.4",
                  "loader": "FABRIC",
                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                }
                    """.trimIndent()
                )
            }.let { created ->
                assertEquals(HttpStatusCode.Created, created.status)
                val client = json.decodeFromString<Client>(created.bodyAsText())
                assertEquals("alice", client.id)
                assertEquals(ClientState.RUNNING, client.state)
            }

            http.get(server.url("/clients/alice/events")).let { clientEvents ->
                assertEquals(HttpStatusCode.OK, clientEvents.status)
                assertTrue(clientEvents.bodyAsText().contains("client.created"))
            }
        }
    }

    @Test
    fun `server rejects invalid client creation as bad request`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory().use { server ->
            server.start()

            http.post(server.url("/clients")) {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateClientRequest(
                            id = "bad",
                            version = "1.21.4",
                            loader = Loader.FABRIC,
                            profile = Profile.offline("NameThatIsTooLong"),
                        )
                    )
                )
            }.let { response ->
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("offline profile name must be 16 characters or fewer"))
            }
        }
    }

    @Test
    fun `server handles session routes for fake client actions`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory().use { server ->
            server.start()
            createAlice(http, server)

            http.post(server.url("/clients/alice/connection/connect")) {
                contentType(ContentType.Application.Json)
                setBody("""{"host":"localhost","port":25565}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"state\":\"CONNECTED\""))
            }

            http.post(server.url("/clients/alice/player/sendChat")) {
                contentType(ContentType.Application.Json)
                setBody("""{"message":"hello from route"}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"message\":\"hello from route\""))
            }

            http.get(server.url("/clients/alice/player")).let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"name\":\"Alice\""))
            }

            http.get(server.url("/clients/alice/player/position")).let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"x\":0.0"))
                assertTrue(body.contains("\"y\":0.0"))
                assertTrue(body.contains("\"z\":0.0"))
            }

            http.post(server.url("/clients/alice/capabilities/player.move")) {
                contentType(ContentType.Application.Json)
                setBody("""{"arguments":{"forward":"true","ticks":"20"}}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"capability\":\"player.move\""))
                assertTrue(body.contains("\"status\":\"ACCEPTED\""))
            }

            http.post(server.url("/clients/alice/stop")) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"state\":\"STOPPED\""))
            }

            http.get(server.url("/clients/alice/events")).let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("client.connected"))
                assertTrue(body.contains("chat"))
                assertTrue(body.contains("client.stopped"))
            }
        }
    }

    private fun withHttpClient(block: suspend (HttpClient) -> Unit) {
        kotlinx.coroutines.runBlocking {
            HttpClient(CIO).use { client -> block(client) }
        }
    }

    private suspend fun createAlice(http: HttpClient, server: LocalSessionApiServer) {
        http.post(server.url("/clients")) {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "id": "alice",
                  "version": "1.21.4",
                  "loader": "FABRIC",
                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                }
                """.trimIndent()
            )
        }.let { created ->
            assertEquals(HttpStatusCode.Created, created.status)
        }
    }
}
