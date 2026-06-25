package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.Profile
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
                val body = version.bodyAsText()
                assertEquals(HttpStatusCode.OK, version.status)
                assertTrue(body.contains("\"driver\":\"craftless-daemon\""))
                assertTrue(body.contains("\"mappingsFingerprint\":\"none\""))
                assertTrue(!body.contains("\"mappings\""))
            }

            http.get(server.url("/openapi.json")).let { openapi ->
                val body = openapi.bodyAsText()
                assertEquals(HttpStatusCode.OK, openapi.status)
                assertTrue(body.contains("/clients/{id}:run"))
                assertTrue(!body.contains("\"/client\""))
                assertTrue(!body.contains("\"/client/state\""))
                assertTrue(!body.contains("\"/connection\""))
                assertTrue(!body.contains("/o/{handle}"))
                assertTrue(!body.contains("/c/{className}"))
                assertTrue(!body.contains("/player/sendChat"))
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

            http.get(server.url("/clients")).let { clients ->
                val body = clients.bodyAsText()
                assertEquals(HttpStatusCode.OK, clients.status)
                assertTrue(body.contains("\"id\":\"alice\""))
                assertTrue(body.contains("\"state\":\"RUNNING\""))
            }

            http.get(server.url("/clients/alice")).let { clientResponse ->
                assertEquals(HttpStatusCode.OK, clientResponse.status)
                val client = json.decodeFromString<Client>(clientResponse.bodyAsText())
                assertEquals("alice", client.id)
                assertEquals(ClientState.RUNNING, client.state)
            }

            http.get(server.url("/clients/missing")).let { missingClient ->
                assertEquals(HttpStatusCode.NotFound, missingClient.status)
                assertTrue(missingClient.bodyAsText().contains("client missing not found"))
            }

            http.get(server.url("/clients/alice/events")).let { clientEvents ->
                assertEquals(HttpStatusCode.OK, clientEvents.status)
                assertTrue(clientEvents.bodyAsText().contains("client.created"))
            }

            http.get(server.url("/clients/alice/openapi.json")).let { clientOpenapi ->
                val body = clientOpenapi.bodyAsText()
                assertEquals(HttpStatusCode.OK, clientOpenapi.status)
                assertTrue(body.contains("\"x-craftless-client-id\":\"alice\""))
                assertTrue(body.contains("\"x-craftless-loader-version\":\"none\""))
                assertTrue(body.contains("\"x-craftless-driver-version\":\"0.1.0-SNAPSHOT\""))
                assertTrue(body.contains("\"x-craftless-mappings-fingerprint\":\"none\""))
                assertTrue(!body.contains("\"x-craftless-mappings\""))
                assertTrue(body.contains("\"x-craftless-installed-mods-fingerprint\":\"none\""))
                assertTrue(body.contains("\"x-craftless-registry-fingerprint\":\"none\""))
                assertTrue(body.contains("\"x-craftless-server-feature-fingerprint\":\"none\""))
                assertTrue(body.contains("\"x-craftless-permissions-fingerprint\":\"local-fake\""))
                assertTrue(body.contains("\"x-craftless-runtime-fingerprint\""))
                assertTrue(body.contains("\"x-craftless-actions\""))
                assertTrue(body.contains("/clients/alice/actions"))
                assertTrue(body.contains("/clients/alice:run"))
                assertTrue(body.contains("/clients/alice/player:chat"))
                assertTrue(body.contains("/clients/alice/player:move"))
                assertTrue(body.contains("\"id\":\"player.move\""))
                assertTrue(body.contains("\"id\":\"player.chat\""))
                assertTrue(body.contains("\"args\""))
                assertTrue(body.contains("\"requestBody\""))
                assertTrue(body.contains("\"responses\""))
                assertTrue(body.contains("\"required\":[\"message\"]"))
                assertTrue(body.contains("\"required\":[\"action\",\"status\"]"))
                assertTrue(body.contains("\"status\":{\"type\":\"string\""))
                assertTrue(body.contains("\"message\":{\"type\":\"string\""))
                assertTrue(body.contains("\"ticks\":{\"type\":\"integer\""))
                assertTrue(!body.contains("/player/sendChat"))
                assertTrue(!body.contains("/clients/alice/player\""))
                assertTrue(!body.contains("/clients/alice/player/position"))
                assertTrue(!body.contains("/actions/move"))
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
    fun `server reports missing client connect as not found`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory().use { server ->
            server.start()

            http.post(server.url("/clients/missing:connect")) {
                contentType(ContentType.Application.Json)
                setBody("""{"host":"localhost","port":25565}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("\"code\":\"NOT_FOUND\""))
                assertTrue(body.contains("client missing not found"))
            }
        }
    }

    @Test
    fun `server records action events from driver result metadata`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory(
            driverFactory = DriverSessionFactory { request ->
                EventMetadataDriverSession(request.id)
            },
        ).use { server ->
            server.start()
            createAlice(http, server)

            http.post(server.url("/clients/alice:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"world.scan","args":{"radius":4}}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"action\":\"world.scan\""))
            }

            http.get(server.url("/clients/alice/events")).let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"type\":\"movement\""))
                assertTrue(body.contains("scanned world radius 4"))
            }
        }
    }

    @Test
    fun `server handles session routes for fake client actions`() = withHttpClient { http ->
        LocalSessionApiServer.inMemory().use { server ->
            server.start()
            createAlice(http, server)

            http.post(server.url("/clients/alice:connect")) {
                contentType(ContentType.Application.Json)
                setBody("""{"host":"localhost","port":25565}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"state\":\"CONNECTED\""))
            }

            assertEquals(
                HttpStatusCode.NotFound,
                http.post(server.url("/clients/alice/connection/connect")) {
                    contentType(ContentType.Application.Json)
                    setBody("""{"host":"localhost","port":25565}""")
                }.status,
            )

            http.post(server.url("/clients/alice:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"player.chat","args":{"message":"hello from route"}}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"action\":\"player.chat\""))
                assertTrue(response.bodyAsText().contains("\"message\":\"hello from route\""))
            }

            http.post(server.url("/clients/missing:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"player.chat","args":{"message":"hello missing"}}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("\"code\":\"NOT_FOUND\""))
                assertTrue(body.contains("client missing not found"))
            }

            http.post(server.url("/clients/alice/player:chat")) {
                contentType(ContentType.Application.Json)
                setBody("""{"message":"hello from alias"}""")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"action\":\"player.chat\""))
                assertTrue(response.bodyAsText().contains("\"message\":\"hello from alias\""))
            }

            assertEquals(HttpStatusCode.NotFound, http.get(server.url("/clients/alice/player")).status)
            assertEquals(HttpStatusCode.NotFound, http.get(server.url("/clients/alice/player/position")).status)

            http.get(server.url("/clients/alice/actions")).let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"id\":\"player.move\""))
                assertTrue(body.contains("\"id\":\"player.chat\""))
                assertTrue(body.contains("\"args\""))
            }

            http.post(server.url("/clients/alice:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"player.move","args":{"forward":true,"ticks":20}}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"action\":\"player.move\""))
                assertTrue(body.contains("\"status\":\"ACCEPTED\""))
            }

            http.post(server.url("/clients/alice/player:move")) {
                contentType(ContentType.Application.Json)
                setBody("""{"forward":true,"ticks":20}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"action\":\"player.move\""))
                assertTrue(body.contains("\"status\":\"ACCEPTED\""))
            }

            http.post(server.url("/clients/alice:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"player.move","args":{"forward":true,"ticks":0}}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(body.contains("\"code\":\"BAD_REQUEST\""))
                assertTrue(body.contains("movement ticks must be positive"))
            }

            http.post(server.url("/clients/alice/player:move")) {
                contentType(ContentType.Application.Json)
                setBody("""{"forward":true,"ticks":0}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(body.contains("\"code\":\"BAD_REQUEST\""))
                assertTrue(body.contains("movement ticks must be positive"))
            }

            http.post(server.url("/clients/alice:run")) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"player.fly","args":{}}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("\"action\":\"player.fly\""))
                assertTrue(body.contains("\"status\":\"UNSUPPORTED\""))
            }

            http.post(server.url("/clients/alice/player:fly")) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("action player.fly is not available for client alice"))
            }

            http.post(server.url("/clients/missing/player:chat")) {
                contentType(ContentType.Application.Json)
                setBody("""{"message":"hello"}""")
            }.let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.NotFound, response.status)
                assertTrue(body.contains("client missing not found"))
            }

            http.post(server.url("/clients/alice:stop")) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.let { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("\"state\":\"STOPPED\""))
            }

            assertEquals(
                HttpStatusCode.NotFound,
                http.post(server.url("/clients/alice/stop")) {
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }.status,
            )

            http.get(server.url("/clients/alice/events")).let { response ->
                val body = response.bodyAsText()
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(body.contains("client.connected"))
                assertTrue(body.contains("chat"))
                assertTrue(body.contains("movement"))
                assertTrue(body.contains("accepted player.move for alice"))
                assertTrue(body.contains("error"))
                assertTrue(body.contains("unsupported fake action player.fly"))
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

private class EventMetadataDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot =
        DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot =
        DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "world.scan",
                schemaVersion = "1",
                arguments = mapOf("radius" to DriverActionArgument("integer")),
            )
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata.fake()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "scanned world radius 4",
            eventType = DriverEventType.MOVEMENT,
        )

    override fun stop(): DriverClientSnapshot =
        DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> =
        emptyList()
}
