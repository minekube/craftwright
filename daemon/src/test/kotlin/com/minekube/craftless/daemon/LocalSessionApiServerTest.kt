package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionResultDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverOperationAdapter
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.CacheCleanupResult
import com.minekube.craftless.protocol.CacheExportResult
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.JsonRpcResponse
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.Profile
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.testkit.FakeDriverSession
import com.minekube.craftless.testkit.fakeDriverRuntimeMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSessionApiServerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `server exposes session metadata and creates fake clients over http`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
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

                http
                    .post(server.url("/clients")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "id": "alice",
                              "version": "1.21.4",
                              "loader": "FABRIC",
                              "profile": { "kind": "OFFLINE", "name": "Alice" }
                            }
                            """.trimIndent(),
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
                    assertError(missingClient.bodyAsText(), "MISSING_CLIENT", "client missing not found")
                }

                http.get(server.url("/clients/alice/events")).let { clientEvents ->
                    assertEquals(HttpStatusCode.OK, clientEvents.status)
                    assertTrue(clientEvents.bodyAsText().contains("client.created"))
                }

                http.get(server.url("/clients/alice/openapi.json")).let { clientOpenapi ->
                    val body = clientOpenapi.bodyAsText()
                    assertEquals(HttpStatusCode.OK, clientOpenapi.status)
                    val document = json.decodeFromString<OpenApiDocument>(body)
                    val runtimeFingerprint =
                        requireNotNull(document.extensions["x-craftless-runtime-fingerprint"]) {
                            "missing runtime fingerprint"
                        }
                    assertEquals(runtimeFingerprint, clientOpenapi.headers["X-Craftless-Runtime-Fingerprint"])
                    assertEquals("\"$runtimeFingerprint\"", clientOpenapi.headers[HttpHeaders.ETag])
                    assertEquals("no-cache", clientOpenapi.headers[HttpHeaders.CacheControl])
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

                    http
                        .get(server.url("/clients/alice/openapi.json")) {
                            header(HttpHeaders.IfNoneMatch, "\"$runtimeFingerprint\"")
                        }.let { cachedOpenapi ->
                            assertEquals(HttpStatusCode.NotModified, cachedOpenapi.status)
                            assertEquals("", cachedOpenapi.bodyAsText())
                        }

                    http.get(server.url("/clients/alice/actions")).let { actions ->
                        assertEquals(HttpStatusCode.OK, actions.status)
                        assertEquals(runtimeFingerprint, actions.headers["X-Craftless-Runtime-Fingerprint"])
                        assertEquals("\"$runtimeFingerprint\"", actions.headers[HttpHeaders.ETag])
                        assertTrue(actions.bodyAsText().contains("\"id\":\"player.chat\""))
                    }
                    http
                        .get(server.url("/clients/alice/actions")) {
                            header(HttpHeaders.IfNoneMatch, "\"$runtimeFingerprint\"")
                        }.let { cachedActions ->
                            assertEquals(HttpStatusCode.NotModified, cachedActions.status)
                            assertEquals("", cachedActions.bodyAsText())
                        }

                    http.get(server.url("/clients/alice/resources")).let { resources ->
                        assertEquals(HttpStatusCode.OK, resources.status)
                        assertEquals(runtimeFingerprint, resources.headers["X-Craftless-Runtime-Fingerprint"])
                        assertEquals("\"$runtimeFingerprint\"", resources.headers[HttpHeaders.ETag])
                        assertTrue(resources.bodyAsText().contains("\"id\":\"player\""))
                    }
                    http
                        .get(server.url("/clients/alice/resources")) {
                            header(HttpHeaders.IfNoneMatch, "\"$runtimeFingerprint\"")
                        }.let { cachedResources ->
                            assertEquals(HttpStatusCode.NotModified, cachedResources.status)
                            assertEquals("", cachedResources.bodyAsText())
                        }
                }
            }
        }

    @Test
    fun `server prepares instance file directories under configured workspace`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-server-client-files")
            fakeLocalSessionApiServer(workspaceRoot = workspace).use { server ->
                server.start()

                val created =
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
                            """.trimIndent(),
                        )
                    }

                assertEquals(HttpStatusCode.Created, created.status)
                val client = json.decodeFromString<Client>(created.bodyAsText())
                client.instance.files.directoryHandles().forEach { handle ->
                    assertTrue(Files.isDirectory(workspace.resolve(handle)))
                }
            }
        }

    @Test
    fun `server prepares cache handles under configured workspace`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-server-cache")
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            fakeLocalSessionApiServer(
                workspaceRoot = workspace,
                cacheMetadataFetcher =
                    ServerStaticCacheMetadataFetcher(
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """
                                {
                                  "versions": [
                                    { "id": "1.21.6", "url": "https://metadata.test/1.21.6.json" }
                                  ]
                                }
                                """.trimIndent(),
                            "https://metadata.test/1.21.6.json" to
                                """{"id":"1.21.6","assetIndex":{"id":"1.21.6","url":"$assetIndexUrl"},"downloads":{"client":{"url":"$clientJarUrl"}}}""",
                            assetIndexUrl to """{"objects":{}}""",
                            loaderVersionsUrl to
                                """
                                [
                                  { "loader": { "version": "0.17.2", "stable": true } }
                                ]
                                """.trimIndent(),
                            loaderProfileUrl to """{"id":"fabric-loader-0.17.2-1.21.6"}""",
                        ),
                        binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                    ),
            ).use { server ->
                server.start()

                val response =
                    http.post(server.url("/cache:prepare")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "minecraftVersion": "1.21.6",
                              "loader": "FABRIC"
                            }
                            """.trimIndent(),
                        )
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val result = json.decodeFromString<CachePrepareResult>(response.bodyAsText())
                assertEquals("1.21.6", result.minecraftVersion)
                assertEquals(Loader.FABRIC, result.loader)
                assertEquals("0.17.2", result.loaderVersion)
                assertTrue(Files.isDirectory(workspace.resolve(result.cacheRoot)))
                assertTrue(Files.isDirectory(workspace.resolve(result.minecraftVersionRoot)))
                assertTrue(Files.isDirectory(workspace.resolve(result.loaderRoot)))
                assertTrue(Files.isDirectory(workspace.resolve(result.runtimeRoot)))
                assertTrue(Files.isRegularFile(workspace.resolve(result.manifest)))
                assertTrue(Files.isRegularFile(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
                assertTrue(Files.isRegularFile(workspace.resolve("cache/loaders/fabric/1.21.6/0.17.2/profile.json")))

                val exportResponse =
                    http.post(server.url("/cache:export")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "manifest": "${result.manifest}",
                              "archive": "exports/server-cache.zip"
                            }
                            """.trimIndent(),
                        )
                    }
                assertEquals(HttpStatusCode.OK, exportResponse.status)
                val export = json.decodeFromString<CacheExportResult>(exportResponse.bodyAsText())
                assertEquals("exports/server-cache.zip", export.archive)
                assertTrue(Files.isRegularFile(workspace.resolve(export.archive)))

                val cleanupResponse =
                    http.post(server.url("/cache:cleanup")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"manifest":"${result.manifest}"}""")
                    }
                assertEquals(HttpStatusCode.OK, cleanupResponse.status)
                val cleanup = json.decodeFromString<CacheCleanupResult>(cleanupResponse.bodyAsText())
                assertTrue(cleanup.deleted.contains(result.manifest))
                assertTrue(!Files.exists(workspace.resolve(result.manifest)))
                assertTrue(Files.exists(workspace.resolve(export.archive)))
            }
        }

    @Test
    fun `server rejects invalid client creation as bad request`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()

                http
                    .post(server.url("/clients")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                CreateClientRequest(
                                    id = "bad",
                                    version = "1.21.4",
                                    loader = Loader.FABRIC,
                                    profile = Profile.offline("NameThatIsTooLong"),
                                ),
                            ),
                        )
                    }.let { response ->
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertTrue(response.bodyAsText().contains("offline profile name must be 16 characters or fewer"))
                    }
            }
        }

    @Test
    fun `server reports missing client connect as not found`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()

                http
                    .post(server.url("/clients/missing:connect")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"host":"localhost","port":25565}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(body, "MISSING_CLIENT", "client missing not found")
                    }
            }
        }

    @Test
    fun `server returns structured action error codes`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                http
                    .post(server.url("/clients/missing:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"hello missing"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(response.bodyAsText(), "MISSING_CLIENT", "client missing not found")
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.fly","args":{}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(response.bodyAsText(), "UNSUPPORTED_ACTION", "action player.fly is not available for client alice")
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.move","args":{"forward":true,"ticks":"20"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(response.bodyAsText(), "INVALID_ACTION_INPUT", "action player.move argument ticks must be integer")
                    }

                http
                    .post(server.url("/clients/alice:stop")) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"after stop"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.Conflict, response.status)
                        assertError(response.bodyAsText(), "STOPPED_CLIENT", "client alice is stopped")
                    }
            }
        }

    @Test
    fun `server records action events from driver result metadata`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            EventMetadataDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
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
    fun `server streams filtered live client events as sse`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"hello stream"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                    }

                http.get(server.url("/clients/alice/events:stream?type=player.chat")).let { response ->
                    val body = response.bodyAsText()
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertTrue(response.headers[HttpHeaders.ContentType]?.contains("text/event-stream") == true)
                    assertTrue(body.contains("event: player.chat"))
                    assertTrue(body.contains("\"type\":\"player.chat\""))
                    assertTrue(body.contains("\"clientId\":\"alice\""))
                    assertTrue(body.contains("hello stream"))
                    assertTrue(!body.contains("client.created"))
                }
            }
        }

    @Test
    fun `server invokes actions through json rpc with correlation ids`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                http
                    .post(server.url("/clients/alice:rpc")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "jsonrpc": "2.0",
                              "id": "rpc:alice:chat-1",
                              "method": "invoke",
                              "params": {
                                "action": "player.chat",
                                "args": { "message": "hello rpc" }
                              }
                            }
                            """.trimIndent(),
                        )
                    }.let { response ->
                        val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                        val result = requireNotNull(body.result?.jsonObject)
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals("rpc:alice:chat-1", body.id)
                        assertEquals("player.chat", result["action"]?.jsonPrimitive?.content)
                        assertEquals("ACCEPTED", result["status"]?.jsonPrimitive?.content)
                    }

                http.get(server.url("/clients/alice/events:stream?correlationId=rpc:alice:chat-1")).let { response ->
                    val body = response.bodyAsText()
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertTrue(body.contains("event: player.chat"))
                    assertTrue(body.contains("\"correlationId\":\"rpc:alice:chat-1\""))
                    assertTrue(body.contains("hello rpc"))
                    assertTrue(!body.contains("client.created"))
                }
            }
        }

    @Test
    fun `server streams generic graph invocation results without legacy event metadata`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            GenericGraphQueryDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:rpc")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "jsonrpc": "2.0",
                                  "id": "rpc:alice:query-1",
                                  "method": "invoke",
                                  "params": {
                                    "action": "player.query"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }.let { response ->
                            val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                            val result = requireNotNull(body.result?.jsonObject)
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("rpc:alice:query-1", body.id)
                            assertEquals("player.query", result["action"]?.jsonPrimitive?.content)
                            assertEquals("ACCEPTED", result["status"]?.jsonPrimitive?.content)
                        }

                    http.get(server.url("/clients/alice/events:stream?type=player.query")).let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("event: player.query"))
                        assertTrue(body.contains("\"operationId\":\"player.query\""))
                        assertTrue(body.contains("\"correlationId\":\"rpc:alice:query-1\""))
                        assertTrue(body.contains("\"selected-slot\":2"), body)
                    }
                }
        }

    @Test
    fun `server rejects unavailable discovered actions before driver invocation`() =
        withHttpClient { http ->
            val driver = UnavailableActionDriverSession("alice")
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory {
                            driver
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http.get(server.url("/clients/alice/actions")).let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("\"id\":\"player.raycast\""))
                        assertTrue(body.contains("\"source\":\"runtime-probe\""))
                        assertTrue(body.contains("\"availability\":\"unavailable\""))
                        assertTrue(body.contains("\"availabilityReason\":\"client-not-connected\""))
                    }

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.raycast","args":{}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.NotFound, response.status)
                            assertError(response.bodyAsText(), "UNSUPPORTED_ACTION", "client-not-connected")
                        }

                    http
                        .post(server.url("/clients/alice/player:raycast")) {
                            contentType(ContentType.Application.Json)
                            setBody("{}")
                        }.let { response ->
                            assertEquals(HttpStatusCode.NotFound, response.status)
                            assertError(response.bodyAsText(), "UNSUPPORTED_ACTION", "client-not-connected")
                        }

                    assertEquals(0, driver.invokeCount)
                }
        }

    @Test
    fun `server validates generic invocation through generated openapi authority before driver invocation`() =
        withHttpClient { http ->
            val driver = DuplicateActionDriverSession("alice")
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory {
                            driver
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.chat","args":{"message":"hello"}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.BadRequest, response.status)
                            assertError(response.bodyAsText(), "INVALID_ACTION_INPUT", "duplicate action id player.chat")
                        }

                    assertEquals(0, driver.invokeCount)
                }
        }

    @Test
    fun `server dispatches graph operations through registered operation adapters`() =
        withHttpClient { http ->
            val driver = GraphOperationAdapterDriverSession("alice")
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory {
                            driver
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.chat","args":{"message":"hello from graph"}}""")
                        }.let { response ->
                            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("player.chat", body["action"]?.jsonPrimitive?.content)
                            assertEquals("ACCEPTED", body["status"]?.jsonPrimitive?.content)
                            assertEquals("adapter accepted hello from graph", body["message"]?.jsonPrimitive?.content)
                        }

                    assertEquals(1, driver.adapterInvokeCount)
                    assertEquals(0, driver.legacyInvokeCount)
                }
        }

    @Test
    fun `server rejects graph operation availability and schema before operation adapters`() =
        withHttpClient { http ->
            val driver =
                GraphOperationAdapterDriverSession(
                    clientId = "alice",
                    availability = RuntimeAvailability.unavailable("client-not-connected"),
                )
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory {
                            driver
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.chat","args":{"message":"hello unavailable"}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.NotFound, response.status)
                            assertError(response.bodyAsText(), "UNSUPPORTED_ACTION", "client-not-connected")
                        }

                    assertEquals(0, driver.adapterInvokeCount)
                    assertEquals(0, driver.legacyInvokeCount)
                }

            val schemaDriver = GraphOperationAdapterDriverSession("alice")
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory {
                            schemaDriver
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.chat","args":{"message":42}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.BadRequest, response.status)
                            assertError(response.bodyAsText(), "INVALID_ACTION_INPUT", "action player.chat argument message must be string")
                        }

                    assertEquals(0, schemaDriver.adapterInvokeCount)
                    assertEquals(0, schemaDriver.legacyInvokeCount)
                }
        }

    @Test
    fun `server returns generic action result data payload`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            DataActionDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.raycast","args":{}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.OK, response.status)
                            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                            val data = requireNotNull(body["data"]?.jsonObject)
                            assertEquals("player.raycast", body["action"]?.jsonPrimitive?.content)
                            assertEquals("ACCEPTED", body["status"]?.jsonPrimitive?.content)
                            assertEquals(true, data["hit"]?.jsonPrimitive?.boolean)
                            assertEquals("block", data["target-kind"]?.jsonPrimitive?.content)
                        }
                }
        }

    @Test
    fun `server rejects driver results that violate advertised result schema`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            MissingRequiredResultDataDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.raycast","args":{}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.BadGateway, response.status)
                            assertError(
                                response.bodyAsText(),
                                "DRIVER_RESULT_MISMATCH",
                                "action player.raycast result requires property data",
                            )
                        }

                    http
                        .post(server.url("/clients/alice/player:raycast")) {
                            contentType(ContentType.Application.Json)
                            setBody("{}")
                        }.let { response ->
                            assertEquals(HttpStatusCode.BadGateway, response.status)
                            assertError(
                                response.bodyAsText(),
                                "DRIVER_RESULT_MISMATCH",
                                "action player.raycast result requires property data",
                            )
                        }
                }
        }

    @Test
    fun `server dispatches nested generated action aliases`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            NestedActionDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http.get(server.url("/clients/alice/openapi.json")).let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(response.bodyAsText().contains("/clients/alice/world/block:break"))
                    }

                    http
                        .post(server.url("/clients/alice/world/block:break")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"max-distance":4.0}""")
                        }.let { response ->
                            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("world.block.break", body["action"]?.jsonPrimitive?.content)
                            assertEquals("ACCEPTED", body["status"]?.jsonPrimitive?.content)
                        }
                }
        }

    @Test
    fun `client actions endpoint is a projection of live per client openapi actions`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                val openApiActions =
                    http.get(server.url("/clients/alice/openapi.json")).let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        json.decodeFromString<OpenApiDocument>(response.bodyAsText()).actions
                    }
                val projectedActions =
                    http.get(server.url("/clients/alice/actions")).let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        json.decodeFromString<List<OpenApiAction>>(response.bodyAsText())
                    }

                assertEquals(openApiActions, projectedActions)
                assertEquals(listOf("player.chat", "player.move"), projectedActions.map { it.id })
            }
        }

    @Test
    fun `client openapi actions and resources share runtime graph fingerprint`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                val openApiResponse = http.get(server.url("/clients/alice/openapi.json"))
                assertEquals(HttpStatusCode.OK, openApiResponse.status)
                val document = json.decodeFromString<OpenApiDocument>(openApiResponse.bodyAsText())
                val graphFingerprint = requireNotNull(document.extensions["runtimeGraphFingerprint"])

                assertEquals(graphFingerprint, document.extensions["x-craftless-runtime-fingerprint"])
                assertEquals(graphFingerprint, openApiResponse.headers["X-Craftless-Runtime-Fingerprint"])

                http.get(server.url("/clients/alice/actions")).let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(graphFingerprint, response.headers["X-Craftless-Runtime-Fingerprint"])
                    assertEquals(document.actions, json.decodeFromString<List<OpenApiAction>>(response.bodyAsText()))
                }

                http.get(server.url("/clients/alice/resources")).let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(graphFingerprint, response.headers["X-Craftless-Runtime-Fingerprint"])
                    assertEquals(document.resources, json.decodeFromString(response.bodyAsText()))
                }
            }
        }

    @Test
    fun `server handles session routes for fake client actions`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                http
                    .post(server.url("/clients/alice:connect")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"host":"localhost","port":25565}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(response.bodyAsText().contains("\"state\":\"CONNECTED\""))
                    }

                assertEquals(
                    HttpStatusCode.NotFound,
                    http
                        .post(server.url("/clients/alice/connection/connect")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"host":"localhost","port":25565}""")
                        }.status,
                )

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"hello from route"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(response.bodyAsText().contains("\"action\":\"player.chat\""))
                        assertTrue(response.bodyAsText().contains("\"message\":\"hello from route\""))
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"minecraft.player.move","args":{}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "invalid action id minecraft.player.move")
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"hello","surprise":"value"}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "action player.chat does not declare argument surprise")
                    }

                http
                    .post(server.url("/clients/missing:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"hello missing"}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(body, "MISSING_CLIENT", "client missing not found")
                    }

                http
                    .post(server.url("/clients/alice/player:chat")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"message":"hello from alias"}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(response.bodyAsText().contains("\"action\":\"player.chat\""))
                        assertTrue(response.bodyAsText().contains("\"message\":\"hello from alias\""))
                    }

                http
                    .post(server.url("/clients/alice/player:chat")) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "action player.chat requires argument message")
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

                http.get(server.url("/clients/alice/resources")).let { response ->
                    val body = response.bodyAsText()
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertTrue(body.contains("\"id\":\"player\""))
                    assertTrue(body.contains("\"player.chat\""))
                    assertTrue(body.contains("\"player.move\""))
                }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.move","args":{"forward":true,"ticks":20}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("\"action\":\"player.move\""))
                        assertTrue(body.contains("\"status\":\"ACCEPTED\""))
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.move","args":{"forward":true,"ticks":"20"}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "action player.move argument ticks must be integer")
                    }

                http
                    .post(server.url("/clients/alice/player:move")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"forward":true,"ticks":20}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("\"action\":\"player.move\""))
                        assertTrue(body.contains("\"status\":\"ACCEPTED\""))
                    }

                http
                    .post(server.url("/clients/alice/player:move")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"forward":"true","ticks":20}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "action player.move argument forward must be boolean")
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.move","args":{"forward":true,"ticks":0}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "movement ticks must be positive")
                    }

                http
                    .post(server.url("/clients/alice/player:move")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"forward":true,"ticks":0}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertError(body, "INVALID_ACTION_INPUT", "movement ticks must be positive")
                    }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.fly","args":{}}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(body, "UNSUPPORTED_ACTION", "action player.fly is not available for client alice")
                    }

                http
                    .post(server.url("/clients/alice/player:fly")) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(body, "UNSUPPORTED_ACTION", "action player.fly is not available for client alice")
                    }

                http
                    .post(server.url("/clients/missing/player:chat")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"message":"hello"}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertError(body, "MISSING_CLIENT", "client missing not found")
                    }

                http
                    .post(server.url("/clients/alice:stop")) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(response.bodyAsText().contains("\"state\":\"STOPPED\""))
                    }

                assertEquals(
                    HttpStatusCode.NotFound,
                    http
                        .post(server.url("/clients/alice/stop")) {
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
                    assertFalse(body.contains("unsupported fake action player.fly"))
                    assertTrue(body.contains("client.stopped"))
                }
            }
        }

    private fun withHttpClient(block: suspend (HttpClient) -> Unit) {
        kotlinx.coroutines.runBlocking {
            HttpClient(CIO).use { client -> block(client) }
        }
    }

    private suspend fun createAlice(
        http: HttpClient,
        server: LocalSessionApiServer,
    ) {
        http
            .post(server.url("/clients")) {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "id": "alice",
                      "version": "1.21.4",
                      "loader": "FABRIC",
                      "profile": { "kind": "OFFLINE", "name": "Alice" }
                    }
                    """.trimIndent(),
                )
            }.let { created ->
                assertEquals(HttpStatusCode.Created, created.status)
            }
    }

    private fun assertError(
        body: String,
        code: String,
        message: String,
    ) {
        val error = json.decodeFromString<ErrorResponse>(body)
        assertEquals(code, error.code)
        assertTrue(error.message.contains(message), error.message)
    }
}

private fun fakeLocalSessionApiServer(
    workspaceRoot: java.nio.file.Path? = null,
    cacheMetadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
): LocalSessionApiServer =
    LocalSessionApiServer.inMemory(
        driverFactory =
            DriverSessionFactory { request ->
                FakeDriverSession(request.id)
            },
        workspaceRoot = workspaceRoot,
        cacheMetadataFetcher = cacheMetadataFetcher,
    )

private class ServerStaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url]) {
            "missing test binary response for $url"
        }
}

private class EventMetadataDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "world.scan",
                schemaVersion = "1",
                arguments = mapOf("radius" to DriverActionArgument("integer")),
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "scanned world radius 4",
            eventType = DriverEventType.MOVEMENT,
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class UnavailableActionDriverSession(
    override val clientId: String,
) : DriverSession {
    var invokeCount = 0

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
                source = DriverActionSource.RUNTIME_PROBE,
                availability = DriverActionAvailability.UNAVAILABLE,
                availabilityReason = "client-not-connected",
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        invokeCount += 1
        return DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED)
    }

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class DuplicateActionDriverSession(
    override val clientId: String,
) : DriverSession {
    var invokeCount = 0

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "player.chat",
                schemaVersion = "1",
                arguments = mapOf("message" to DriverActionArgument("string", required = true)),
            ),
            DriverActionDescriptor(
                id = "player.chat",
                schemaVersion = "2",
                arguments = mapOf("message" to DriverActionArgument("string", required = true)),
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        invokeCount += 1
        return DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED)
    }

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class DataActionDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            data =
                buildJsonObject {
                    put("hit", true)
                    put("target-kind", "block")
                },
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class GraphOperationAdapterDriverSession(
    override val clientId: String,
    private val availability: RuntimeAvailability = RuntimeAvailability.available(),
) : DriverSession {
    var adapterInvokeCount = 0
    var legacyInvokeCount = 0

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "fake.chat",
                        arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                        availability = availability,
                    ),
                ),
        )

    override fun operationAdapters(): DriverOperationAdapters =
        DriverOperationAdapters(
            mapOf(
                "fake.chat" to
                    DriverOperationAdapter { invocation ->
                        adapterInvokeCount += 1
                        DriverActionResult(
                            action = invocation.operation.id,
                            status = DriverActionStatus.ACCEPTED,
                            message = "adapter accepted ${invocation.arguments.getValue("message").jsonPrimitive.content}",
                            eventType = DriverEventType.CHAT,
                        )
                    },
            ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        legacyInvokeCount += 1
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "legacy invoke should not run",
        )
    }

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class GenericGraphQueryDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.query",
                        resource = "player",
                        adapter = "fake.player-query",
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    override fun operationAdapters(): DriverOperationAdapters =
        DriverOperationAdapters(
            mapOf(
                "fake.player-query" to
                    DriverOperationAdapter { invocation ->
                        DriverActionResult(
                            action = invocation.operation.id,
                            status = DriverActionStatus.ACCEPTED,
                            data =
                                buildJsonObject {
                                    put("selected-slot", 2)
                                },
                        )
                    },
            ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.UNSUPPORTED, message = "legacy invoke should not run")

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class MissingRequiredResultDataDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "player.raycast",
                schemaVersion = "1",
                result =
                    DriverActionResultDescriptor(
                        required = listOf("action", "status", "data"),
                        properties =
                            mapOf(
                                "action" to DriverActionResultProperty("string"),
                                "status" to DriverActionResultProperty("string"),
                                "data" to DriverActionResultProperty("object"),
                            ),
                    ),
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class NestedActionDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.CONNECTED)

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "world.block.break",
                schemaVersion = "1",
                arguments = mapOf("max-distance" to DriverActionArgument("number")),
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}
