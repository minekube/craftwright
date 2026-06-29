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
import com.minekube.craftless.driver.api.DriverOperationAdapter
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.CacheCleanupResult
import com.minekube.craftless.protocol.CacheExportResult
import com.minekube.craftless.protocol.CacheLaunchPlan
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.CachePrepareStatus
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.InstanceFiles
import com.minekube.craftless.protocol.JavaRuntimeListResult
import com.minekube.craftless.protocol.JavaRuntimeProviderKind
import com.minekube.craftless.protocol.JavaRuntimeSelection
import com.minekube.craftless.protocol.JavaRuntimeSelectionStatus
import com.minekube.craftless.protocol.JsonRpcResponse
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
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
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO

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
    fun `server serves client artifacts with traversal guards`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-server-artifacts")
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
                val artifact = workspace.resolve(client.instance.files.artifacts).resolve("screenshot-1.png")
                Files.writeString(artifact, "png-bytes")
                Files.writeString(workspace.resolve(client.instance.files.runtimeRoot).resolve("secret.txt"), "secret")

                http.get(server.url("/clients/alice/artifacts/screenshot-1.png")).let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("png-bytes", response.bodyAsText())
                }

                http.get(server.url("/clients/alice/artifacts/%2e%2e/secret.txt")).let { response ->
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    assertError(response.bodyAsText(), "INVALID_ARTIFACT_ID", "artifact id must stay under client artifacts")
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
    fun `server prepares and launches workspace client runtime without injected driver factory`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-server-workspace-client")
            val launcher = RecordingClientRuntimeLauncher()
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-gamma/manifest.json"
            val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-gamma/bin/java"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher =
                        ServerStaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "versions": [
                                        { "id": "1.21.6", "url": "$versionUrl" }
                                      ]
                                    }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": { "id": "1.21.6", "url": "$assetIndexUrl" },
                                      "javaVersion": {
                                        "component": "java-runtime-gamma",
                                        "majorVersion": 21
                                      },
                                      "mainClass": "test.minecraft.Main",
                                      "arguments": {
                                        "jvm": ["-cp", "${'$'}{classpath}"],
                                        "game": ["--gameDir", "${'$'}{game_directory}"]
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                MINECRAFT_JAVA_RUNTIME_INDEX_URL to serverJavaRuntimeIndexJson(javaRuntimeManifestUrl),
                                javaRuntimeManifestUrl to
                                    """
                                    {
                                      "files": {
                                        "bin/java": {
                                          "type": "file",
                                          "downloads": {
                                            "raw": { "url": "$javaExecutableUrl" }
                                          }
                                        }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.6",
                                      "mainClass": "test.fabric.Main",
                                      "libraries": [
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    javaExecutableUrl to serverFakeJavaBytes("21.0.11"),
                                    fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                                ),
                        ),
                    clientRuntimeLauncher = launcher,
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.21.6",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    val client = json.decodeFromString<Client>(response.bodyAsText())
                    assertEquals(ClientState.RUNNING, client.state)
                    assertEquals(listOf("alice"), launcher.launches.map { it.clientId })
                    val launch = launcher.launches.single()
                    assertEquals("alice", launch.attachEnvironment?.clientId)
                    assertEquals(server.url(""), launch.attachEnvironment?.daemonUrl)
                    assertEquals("cache/prepared/1.21.6-fabric-0.17.2.json", launch.prepared.manifest)
                    assertEquals("cache/prepared/1.21.6-fabric-0.17.2.launch.json", launch.launch.arguments)
                    assertTrue(launch.launch.classpath.any { it.endsWith("client.jar") })
                    assertTrue(launch.launch.javaExecutable?.endsWith("/java-runtime-gamma/image/bin/java") == true)
                    assertTrue(Files.isRegularFile(workspace.resolve(launch.prepared.manifest)))
                    assertTrue(Files.isRegularFile(workspace.resolve(requireNotNull(launch.launch.arguments))))
                }
        }

    @Test
    fun `prepared runtime launch plan includes configured craftless fabric driver mod`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-launch")
            val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
            Files.writeString(driverMod, "craftless-driver-mod")
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_FABRIC_DRIVER_MOD to
                                        driverMod.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.21.6",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    val launch = launcher.launches.single()
                    val driverHandle =
                        launch.prepared.artifacts
                            .single { artifact ->
                                artifact.kind == CachePreparedArtifactKind.FABRIC_MOD &&
                                    artifact.source == driverMod.toUri().toString()
                            }.handle
                    assertTrue(launch.launch.mods.contains(driverHandle))
                    assertEquals("craftless-driver-mod", Files.readString(workspace.resolve(driverHandle)))
                }
        }

    @Test
    fun `prepared runtime asks driver mod provider for requested runtime lane`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-lane")
            val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
            Files.writeString(driverMod, "craftless-driver-mod")
            val launcher = RecordingClientRuntimeLauncher()
            val requests = mutableListOf<ClientRuntimeDriverModRequest>()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ClientRuntimeDriverModProvider { request ->
                            requests += request
                            driverMod
                        },
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.21.6",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    assertEquals(
                        listOf(
                            ClientRuntimeDriverModRequest(
                                loader = Loader.FABRIC,
                                minecraftVersion = "1.21.6",
                                loaderVersion = "0.17.2",
                                fabricApiVersion = SERVER_DEFAULT_TEST_FABRIC_API_VERSION,
                                javaMajorVersion = 21,
                            ),
                        ),
                        requests,
                    )
                    assertEquals(listOf("alice"), launcher.launches.map { it.clientId })
                }
        }

    @Test
    fun `prepared runtime passes requested loader version lane to cache and driver mod provider`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-loader-lane")
            val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
            Files.writeString(driverMod, "craftless-driver-mod")
            val launcher = RecordingClientRuntimeLauncher()
            val requests = mutableListOf<ClientRuntimeDriverModRequest>()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ClientRuntimeDriverModProvider { request ->
                            requests += request
                            driverMod
                        },
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.21.6",
                                  "loader": "FABRIC",
                                  "loaderVersion": "0.16.14",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    assertEquals(
                        listOf(
                            ClientRuntimeDriverModRequest(
                                loader = Loader.FABRIC,
                                minecraftVersion = "1.21.6",
                                loaderVersion = "0.16.14",
                                fabricApiVersion = SERVER_DEFAULT_TEST_FABRIC_API_VERSION,
                                javaMajorVersion = 21,
                            ),
                        ),
                        requests,
                    )
                    assertEquals(
                        "cache/prepared/1.21.6-fabric-0.16.14.json",
                        launcher.launches
                            .single()
                            .prepared
                            .manifest,
                    )
                }
        }

    @Test
    fun `prepared runtime defaults loader version from driver mod provider preference`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-preferred-loader-lane")
            val distribution = Files.createTempDirectory("craftless-driver-mod-preferred-distribution")
            val driverMod = distribution.resolve("mods/manifest-driver.jar")
            Files.createDirectories(driverMod.parent)
            Files.writeString(driverMod, "manifest-driver-mod")
            val manifest = distribution.resolve("driver-mods.json")
            Files.writeString(
                manifest,
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.21.6",
                      "loaderVersion": "0.16.14",
                      "path": "mods/manifest-driver.jar"
                    }
                  ]
                }
                """.trimIndent(),
            )
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST to
                                        manifest.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.21.6",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    assertEquals(
                        "cache/prepared/1.21.6-fabric-0.16.14.json",
                        launcher.launches
                            .single()
                            .prepared
                            .manifest,
                    )
                    val modHandles =
                        launcher.launches
                            .single()
                            .prepared
                            .launch
                            .mods
                    assertTrue(modHandles.any { handle -> Files.readString(workspace.resolve(handle)) == "manifest-driver-mod" })
                }
        }

    @Test
    fun `prepared runtime resolves aliases before driver mod provider preference`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-preferred-alias-lane")
            val distribution = Files.createTempDirectory("craftless-driver-mod-preferred-alias-distribution")
            val driverMod = distribution.resolve("mods/manifest-driver.jar")
            Files.createDirectories(driverMod.parent)
            Files.writeString(driverMod, "manifest-driver-mod")
            val manifest = distribution.resolve("driver-mods.json")
            Files.writeString(
                manifest,
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.21.6",
                      "loaderVersion": "0.16.14",
                      "path": "mods/manifest-driver.jar"
                    }
                  ]
                }
                """.trimIndent(),
            )
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST to
                                        manifest.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "latest-release",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    assertEquals(
                        "cache/prepared/1.21.6-fabric-0.16.14.json",
                        launcher.launches
                            .single()
                            .prepared
                            .manifest,
                    )
                }
        }

    @Test
    fun `prepared runtime asks driver mod provider for resolved runtime lane`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-driver-mod-resolved-lane")
            val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
            Files.writeString(driverMod, "craftless-driver-mod")
            val launcher = RecordingClientRuntimeLauncher()
            val requests = mutableListOf<ClientRuntimeDriverModRequest>()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ClientRuntimeDriverModProvider { request ->
                            requests += request
                            driverMod
                        },
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "latest-release",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    assertEquals(
                        listOf(
                            ClientRuntimeDriverModRequest(
                                loader = Loader.FABRIC,
                                minecraftVersion = "1.21.6",
                                loaderVersion = "0.17.2",
                                fabricApiVersion = SERVER_DEFAULT_TEST_FABRIC_API_VERSION,
                                javaMajorVersion = 21,
                            ),
                        ),
                        requests,
                    )
                    assertEquals(
                        "cache/prepared/1.21.6-fabric-0.17.2.json",
                        launcher.launches
                            .single()
                            .prepared
                            .manifest,
                    )
                }
        }

    @Test
    fun `prepared runtime selects packaged older fabric lane from manifest`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-packaged-older-lane-workspace")
            val distribution = Files.createTempDirectory("craftless-packaged-older-lane-selection")
            val currentDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
            val olderDriverMod = distribution.resolve("mods/fabric-1.20.6/craftless-driver-fabric.jar")
            Files.createDirectories(olderDriverMod.parent)
            Files.writeString(currentDriverMod, "current-driver-mod")
            Files.writeString(olderDriverMod, "older-driver-mod")
            val manifest = distribution.resolve("driver-mods.json")
            Files.writeString(
                manifest,
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.21.6",
                      "loaderVersion": "0.19.3",
                      "fabricApiVersion": "0.128.2+1.21.6",
                      "javaMajorVersion": 21,
                      "mappingsFingerprint": "craftless-fabric-bindings",
                      "path": "mods/craftless-driver-fabric.jar"
                    },
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.20.6",
                      "loaderVersion": "0.19.3",
                      "fabricApiVersion": "0.100.8+1.20.6",
                      "javaMajorVersion": 21,
                      "mappingsFingerprint": "craftless-fabric-bindings-1-20-6",
                      "path": "mods/fabric-1.20.6/craftless-driver-fabric.jar"
                    }
                  ]
                }
                """.trimIndent(),
            )
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcherWithOlderLane(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST to
                                        manifest.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.20.6",
                                  "loader": "FABRIC",
                                  "loaderVersion": "0.19.3",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    val launch = launcher.launches.single()
                    assertEquals("cache/prepared/1.20.6-fabric-0.19.3.json", launch.prepared.manifest)
                    val stagedModContents =
                        launch
                            .launch
                            .mods
                            .map { handle -> Files.readString(workspace.resolve(handle)) }
                    assertTrue(stagedModContents.contains("older-driver-mod"))
                    assertFalse(stagedModContents.contains("current-driver-mod"))
                }
        }

    @Test
    fun `prepared runtime includes packaged fabric lane runtime mods`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-packaged-runtime-mods-workspace")
            val distribution = Files.createTempDirectory("craftless-packaged-runtime-mods-distribution")
            val olderDriverMod = distribution.resolve("mods/fabric-1.20.6/craftless-driver-fabric.jar")
            val navigationRuntime = distribution.resolve("mods/fabric-1.20.6/runtime/navigation-runtime.jar")
            val navigationNestedRuntime = distribution.resolve("mods/fabric-1.20.6/runtime/navigation-nested-runtime.jar")
            Files.createDirectories(navigationRuntime.parent)
            Files.writeString(olderDriverMod, "older-driver-mod")
            Files.writeString(navigationRuntime, "navigation-runtime")
            Files.writeString(navigationNestedRuntime, "navigation-nested-runtime")
            val manifest = distribution.resolve("driver-mods.json")
            Files.writeString(
                manifest,
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.20.6",
                      "loaderVersion": "0.19.3",
                      "fabricApiVersion": "0.100.8+1.20.6",
                      "javaMajorVersion": 21,
                      "mappingsFingerprint": "craftless-fabric-bindings-1-20-6",
                      "path": "mods/fabric-1.20.6/craftless-driver-fabric.jar",
                      "runtimeMods": [
                        "mods/fabric-1.20.6/runtime/navigation-runtime.jar",
                        "mods/fabric-1.20.6/runtime/navigation-nested-runtime.jar"
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            )
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = preparedRuntimeMetadataFetcherWithOlderLane(),
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST to
                                        manifest.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "alice",
                                  "version": "1.20.6",
                                  "loader": "FABRIC",
                                  "loaderVersion": "0.19.3",
                                  "profile": { "kind": "OFFLINE", "name": "Alice" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    val stagedModContents =
                        launcher
                            .launches
                            .single()
                            .launch
                            .mods
                            .map { handle -> Files.readString(workspace.resolve(handle)) }
                    assertEquals(
                        listOf("older-driver-mod", "navigation-runtime", "navigation-nested-runtime"),
                        stagedModContents.filter {
                            it == "older-driver-mod" ||
                                it == "navigation-runtime" ||
                                it == "navigation-nested-runtime"
                        },
                    )
                }
        }

    @Test
    fun `client creation rejects missing latest fabric driver lane before binary downloads`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-latest-lane-preflight")
            val distribution = Files.createTempDirectory("craftless-latest-lane-preflight-distribution")
            val currentDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
            Files.createDirectories(currentDriverMod.parent)
            Files.writeString(currentDriverMod, "current-driver-mod")
            val manifest = distribution.resolve("driver-mods.json")
            Files.writeString(
                manifest,
                """
                {
                  "entries": [
                    {
                      "loader": "FABRIC",
                      "minecraftVersion": "1.21.6",
                      "loaderVersion": "0.19.3",
                      "fabricApiVersion": "0.128.2+1.21.6",
                      "javaMajorVersion": 21,
                      "path": "mods/craftless-driver-fabric.jar"
                    }
                  ]
                }
                """.trimIndent(),
            )
            val metadataFetcher = preparedRuntimeMetadataFetcherWithLatestRelease26()
            val launcher = RecordingClientRuntimeLauncher()

            LocalSessionApiServer
                .inMemory(
                    workspaceRoot = workspace,
                    cacheMetadataFetcher = metadataFetcher,
                    clientRuntimeLauncher = launcher,
                    clientRuntimeDriverModProvider =
                        ConfiguredClientRuntimeDriverModProvider(
                            environment =
                                mapOf(
                                    ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST to
                                        manifest.toString(),
                                ),
                        ),
                ).use { server ->
                    server.start()

                    val response =
                        http.post(server.url("/clients")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "id": "latest",
                                  "version": "latest-release",
                                  "loader": "FABRIC",
                                  "profile": { "kind": "OFFLINE", "name": "Latest" }
                                }
                                """.trimIndent(),
                            )
                        }

                    assertEquals(HttpStatusCode.BadRequest, response.status)
                    val body = response.bodyAsText()
                    assertTrue(body.contains("driver mod manifest has no Fabric entry for 26.2 0.19.3"))
                    assertTrue(body.contains("fabricApiVersion=0.153.0+26.2"))
                    assertTrue(body.contains("javaMajorVersion=25"))
                    assertEquals(emptyList(), launcher.launches)
                    assertEquals(emptyList(), metadataFetcher.binaryFetches)
                    assertFalse(Files.exists(workspace.resolve("cache/assets/objects")))
                }
        }

    @Test
    fun `process client runtime launcher starts prepared command`() {
        val workspace = Files.createTempDirectory("craftless-process-client-runtime")
        val marker = workspace.resolve("launched.txt")
        val attachClientIdMarker = workspace.resolve("attach-client-id.txt")
        val attachDaemonUrlMarker = workspace.resolve("attach-daemon-url.txt")
        val javaExecutable = workspace.resolve("cache/runtimes/java-runtime-gamma/image/bin/java")
        Files.createDirectories(javaExecutable.parent)
        Files.writeString(
            javaExecutable,
            """
            #!/usr/bin/env sh
            echo "${'$'}@" > "$marker"
            echo "${'$'}CRAFTLESS_CLIENT_ID" > "$attachClientIdMarker"
            echo "${'$'}CRAFTLESS_DAEMON_URL" > "$attachDaemonUrlMarker"
            """.trimIndent(),
        )
        javaExecutable.toFile().setExecutable(true, true)
        val fabricApiHandle = "cache/mods/fabric/fabric-api.jar"
        Files.createDirectories(workspace.resolve("cache/mods/fabric"))
        Files.writeString(workspace.resolve(fabricApiHandle), "fabric-api-jar")
        val arguments = workspace.resolve("cache/prepared/1.21.6-fabric-0.17.2.launch.json")
        Files.createDirectories(arguments.parent)
        Files.writeString(
            arguments,
            """
            {
              "mainClass": "test.minecraft.Main",
              "jvm": ["-Dcraftless.gameRoot={{gameRoot}}"],
              "game": [
                "--gameDir", "{{gameRoot}}",
                "--username", "{{auth_player_name}}",
                "--uuid", "{{auth_uuid}}",
                "--accessToken", "{{auth_access_token}}",
                "--userType", "{{user_type}}",
                "--assetIndex", "{{assets_index_name}}",
                "--versionType", "{{version_type}}",
                "--launcherName", "{{launcher_name}}",
                "--launcherVersion", "{{launcher_version}}",
                "--quickPlayPath", "{{quickPlayPath}}",
                "--quickPlaySingleplayer", "{{quickPlaySingleplayer}}",
                "--quickPlayMultiplayer", "{{quickPlayMultiplayer}}",
                "--quickPlayRealms", "{{quickPlayRealms}}",
                "--xuid", "{{auth_xuid}}",
                "--clientId", "{{clientid}}",
                "--width", "{{resolution_width}}",
                "--height", "{{resolution_height}}"
              ]
            }
            """.trimIndent(),
        )
        val launch =
            ProcessClientRuntimeLauncher().launch(
                request =
                    CreateClientRequest(
                        id = "alice",
                        version = "1.21.6",
                        loader = Loader.FABRIC,
                        profile = Profile.offline("Alice"),
                    ),
                prepared =
                    CachePrepareResult(
                        minecraftVersion = "1.21.6",
                        loader = Loader.FABRIC,
                        loaderVersion = "0.17.2",
                        cacheRoot = "cache",
                        minecraftVersionRoot = "cache/minecraft/versions/1.21.6",
                        loaderRoot = "cache/loaders/fabric/1.21.6/0.17.2",
                        runtimeRoot = "cache/runtimes",
                        manifest = "cache/prepared/1.21.6-fabric-0.17.2.json",
                        status = CachePrepareStatus.PREPARED,
                        artifacts = emptyList(),
                        launch =
                            CacheLaunchPlan(
                                classpath = listOf("cache/minecraft/versions/1.21.6/client.jar"),
                                mods = listOf(fabricApiHandle),
                                javaExecutable = "cache/runtimes/java-runtime-gamma/image/bin/java",
                                arguments = "cache/prepared/1.21.6-fabric-0.17.2.launch.json",
                            ),
                    ),
                files = InstanceFiles.forInstance("alice-1.21.6-fabric"),
                workspaceRoot = workspace,
                attachEnvironment =
                    ClientDriverAttachEnvironment(
                        clientId = "alice",
                        daemonUrl = "http://127.0.0.1:12345",
                    ),
            )

        assertEquals(ClientRuntimeLaunchStatus.LAUNCHED, launch.status)
        assertTrue(launch.pid != null)
        val process = requireNotNull(launch.process)
        assertTrue(process.waitFor(2, TimeUnit.SECONDS))
        assertTrue(launch.command.first().endsWith("/java-runtime-gamma/image/bin/java"))
        assertTrue(waitForRegularFile(marker))
        val invoked = Files.readString(marker)
        assertTrue(invoked.contains("test.minecraft.Main"))
        assertTrue(invoked.contains("--gameDir instances/alice-1.21.6-fabric/minecraft"))
        assertTrue(invoked.contains("--username Alice"))
        assertTrue(invoked.contains("--uuid 10920508-d5d8-3eed-93d2-92f193afe7d7"))
        assertTrue(invoked.contains("--accessToken 0"))
        assertTrue(invoked.contains("--userType legacy"))
        assertTrue(invoked.contains("--assetIndex 1.21.6"))
        assertTrue(invoked.contains("--versionType release"))
        assertTrue(invoked.contains("--launcherName craftless"))
        assertTrue(invoked.contains("--launcherVersion 0"))
        assertTrue(invoked.contains("--quickPlayPath instances/alice-1.21.6-fabric/minecraft/quickplay"))
        assertTrue(waitForRegularFile(attachClientIdMarker))
        assertTrue(waitForRegularFile(attachDaemonUrlMarker))
        assertEquals("alice", Files.readString(attachClientIdMarker).trim())
        assertEquals("http://127.0.0.1:12345", Files.readString(attachDaemonUrlMarker).trim())
        val materializedFabricApi =
            workspace.resolve("instances/alice-1.21.6-fabric/minecraft/mods/fabric-api.jar")
        assertEquals("fabric-api-jar", Files.readString(materializedFabricApi))
        val options = Files.readString(workspace.resolve("instances/alice-1.21.6-fabric/minecraft/options.txt"))
        assertTrue(options.contains("soundCategory_master:0.0"))
        assertTrue(options.contains("soundCategory_ui:0.0"))
        assertFalse(invoked.contains("--quickPlaySingleplayer"))
        assertFalse(invoked.contains("--quickPlayMultiplayer"))
        assertFalse(invoked.contains("--quickPlayRealms"))
        assertFalse(invoked.contains("--xuid"))
        assertFalse(invoked.contains("--clientId"))
        assertFalse(invoked.contains("--width"))
        assertFalse(invoked.contains("--height"))
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
    fun `server records action events from operation ids`() =
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
                        assertTrue(body.contains("\"type\":\"world.scan\""))
                        assertTrue(body.contains("scanned world radius 4"))
                    }
                }
        }

    @Test
    fun `server attach proxies generated run calls to remote driver endpoint`() =
        withHttpClient { http ->
            RemoteDriverEndpoint("alice").use { remote ->
                fakeLocalSessionApiServer().use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/missing:attach")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"endpoint":"${remote.url}/driver"}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.NotFound, response.status)
                            assertError(response.bodyAsText(), "MISSING_CLIENT", "client missing not found")
                        }

                    http
                        .post(server.url("/clients/alice:attach")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"endpoint":"${remote.url}/driver"}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.OK, response.status)
                        }

                    http.get(server.url("/clients/alice/openapi.json")).let { response ->
                        val document = json.decodeFromString<OpenApiDocument>(response.bodyAsText())
                        assertEquals("craftless-driver-fabric", document.extensions["x-craftless-driver"])
                        assertTrue(document.actions.any { action -> action.id == "player.chat" })
                    }

                    http
                        .post(server.url("/clients/alice:run")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"action":"player.chat","args":{"message":"hello attached"}}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertTrue(response.bodyAsText().contains("hello attached"))
                        }

                    assertEquals(listOf("player.chat:hello attached"), remote.invocations)
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
    fun `server persists json rpc subscriptions as sse filters`() =
        withHttpClient { http ->
            fakeLocalSessionApiServer().use { server ->
                server.start()
                createAlice(http, server)

                val subscriptionId =
                    http
                        .post(server.url("/clients/alice:rpc")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "jsonrpc": "2.0",
                                  "id": "rpc:alice:subscribe-chat",
                                  "method": "subscribe",
                                  "params": {
                                    "type": "player.chat"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }.let { response ->
                            val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                            val result = requireNotNull(body.result?.jsonObject)
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("rpc:alice:subscribe-chat", body.id)
                            val filter = requireNotNull(result["filter"]?.jsonObject)
                            assertEquals(listOf("player.chat"), filter["types"]?.jsonArray?.map { it.jsonPrimitive.content })
                            requireNotNull(result["subscriptionId"]?.jsonPrimitive?.content)
                        }

                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.chat","args":{"message":"subscribed chat"}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                    }
                http
                    .post(server.url("/clients/alice:run")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"action":"player.move","args":{"forward":true,"ticks":1}}""")
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                    }

                http.get(server.url("/clients/alice/events:stream?subscriptionId=$subscriptionId")).let { response ->
                    val body = response.bodyAsText()
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertTrue(body.contains("event: player.chat"), body)
                    assertTrue(body.contains("subscribed chat"), body)
                    assertTrue(!body.contains("event: player.move"), body)
                }

                http
                    .post(server.url("/clients/alice:rpc")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "jsonrpc": "2.0",
                              "id": "rpc:alice:query-subscriptions",
                              "method": "query",
                              "params": {
                                "target": "subscriptions"
                              }
                            }
                            """.trimIndent(),
                        )
                    }.let { response ->
                        val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                        val subscriptions = requireNotNull(body.result?.jsonArray)
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(listOf(subscriptionId), subscriptions.map { it.jsonObject["id"]?.jsonPrimitive?.content })
                    }

                http
                    .post(server.url("/clients/alice:rpc")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "jsonrpc": "2.0",
                              "id": "rpc:alice:unsubscribe-chat",
                              "method": "unsubscribe",
                              "params": {
                                "subscriptionId": "$subscriptionId"
                              }
                            }
                            """.trimIndent(),
                        )
                    }.let { response ->
                        val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                        val result = requireNotNull(body.result?.jsonObject)
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(subscriptionId, result["subscriptionId"]?.jsonPrimitive?.content)
                        assertEquals(true, result["unsubscribed"]?.jsonPrimitive?.boolean)
                    }

                http
                    .post(server.url("/clients/alice:rpc")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "jsonrpc": "2.0",
                              "id": "rpc:alice:query-subscriptions-after-unsubscribe",
                              "method": "query",
                              "params": {
                                "target": "subscriptions"
                              }
                            }
                            """.trimIndent(),
                        )
                    }.let { response ->
                        val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                        val subscriptions = requireNotNull(body.result?.jsonArray)
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(emptyList(), subscriptions.map { it.jsonObject["id"]?.jsonPrimitive?.content })
                    }
            }
        }

    @Test
    fun `server answers json rpc query from live per client openapi projections`() =
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
                                  "id": "rpc:alice:openapi-1",
                                  "method": "query",
                                  "params": {
                                    "target": "openapi"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }.let { response ->
                            val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                            val document = requireNotNull(body.result?.jsonObject)
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("rpc:alice:openapi-1", body.id)
                            assertTrue(
                                document["x-craftless-actions"]?.jsonArray?.any {
                                    it.jsonObject["id"]?.jsonPrimitive?.content == "player.query"
                                } == true,
                            )
                            assertTrue(
                                document["x-craftless-resources"]?.jsonArray?.any {
                                    it.jsonObject["id"]?.jsonPrimitive?.content == "player"
                                } == true,
                            )
                        }

                    http
                        .post(server.url("/clients/alice:rpc")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "jsonrpc": "2.0",
                                  "id": "rpc:alice:actions-1",
                                  "method": "query",
                                  "params": {
                                    "target": "actions"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }.let { response ->
                            val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                            val actions = requireNotNull(body.result?.jsonArray)
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("rpc:alice:actions-1", body.id)
                            assertEquals(listOf("player.query"), actions.map { it.jsonObject["id"]?.jsonPrimitive?.content })
                        }

                    http
                        .post(server.url("/clients/alice:rpc")) {
                            contentType(ContentType.Application.Json)
                            setBody(
                                """
                                {
                                  "jsonrpc": "2.0",
                                  "id": "rpc:alice:resources-1",
                                  "method": "query",
                                  "params": {
                                    "target": "resources"
                                  }
                                }
                                """.trimIndent(),
                            )
                        }.let { response ->
                            val body = json.decodeFromString<JsonRpcResponse>(response.bodyAsText())
                            val resources = requireNotNull(body.result?.jsonArray)
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertEquals("rpc:alice:resources-1", body.id)
                            assertEquals(listOf("player"), resources.map { it.jsonObject["id"]?.jsonPrimitive?.content })
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
                            assertError(response.bodyAsText(), "INVALID_ACTION_INPUT", "duplicate runtime operation id player.chat")
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
                    assertEquals(0, driver.fallbackInvokeCount)
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
                    assertEquals(0, driver.fallbackInvokeCount)
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
                    assertEquals(0, schemaDriver.fallbackInvokeCount)
                }
        }

    @Test
    fun `server maps graph unavailable reasons to machine readable invocation errors`() =
        withHttpClient { http ->
            data class Case(
                val reason: String,
                val status: HttpStatusCode,
                val code: String,
            )

            listOf(
                Case("permission-denied", HttpStatusCode.Forbidden, "PERMISSION_DENIED"),
                Case("stale-handle", HttpStatusCode.Conflict, "STALE_HANDLE"),
                Case("runtime-mismatch", HttpStatusCode.Conflict, "RUNTIME_MISMATCH"),
            ).forEach { case ->
                val driver =
                    GraphOperationAdapterDriverSession(
                        clientId = "alice",
                        availability = RuntimeAvailability.unavailable(case.reason),
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
                                assertEquals(case.status, response.status)
                                assertError(response.bodyAsText(), case.code, case.reason)
                            }

                        assertEquals(0, driver.adapterInvokeCount)
                        assertEquals(0, driver.fallbackInvokeCount)
                    }
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
                assertEquals(listOf("media.screenshot.capture", "player.chat", "player.move"), projectedActions.map { it.id })
            }
        }

    @Test
    fun `server lists and resolves Java runtimes under configured workspace`() =
        withHttpClient { http ->
            val workspace = Files.createTempDirectory("craftless-server-java-runtimes")
            val java = workspace.resolve("cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java")
            writeFakeJava(java, "25.0.3")
            fakeLocalSessionApiServer(workspaceRoot = workspace).use { server ->
                server.start()

                http.get(server.url("/runtimes/java")).let { response ->
                    assertEquals(HttpStatusCode.OK, response.status)
                    val result = json.decodeFromString<JavaRuntimeListResult>(response.bodyAsText())
                    val runtime =
                        result.runtimes.single {
                            it.executable == "cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java"
                        }
                    assertEquals(JavaRuntimeProviderKind.MANAGED, runtime.provider)
                    assertEquals(25, runtime.majorVersion)
                }

                http
                    .post(server.url("/runtimes/java:resolve")) {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "requirement": {
                                "majorVersion": 25,
                                "component": "java-runtime-gamma",
                                "reason": "minecraft-version-metadata"
                              }
                            }
                            """.trimIndent(),
                        )
                    }.let { response ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        val selection = json.decodeFromString<JavaRuntimeSelection>(response.bodyAsText())
                        assertEquals(JavaRuntimeSelectionStatus.SELECTED, selection.status)
                        assertEquals(JavaRuntimeProviderKind.MANAGED, selection.selected?.provider)
                        assertEquals("cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java", selection.selected?.executable)
                    }
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
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("\"action\":\"player.move\""))
                        assertTrue(body.contains("\"status\":\"FAILED\""))
                        assertTrue(body.contains("\"message\":\"invalid-ticks\""))
                        assertTrue(body.contains("\"moved\":false"))
                        assertTrue(body.contains("\"reason\":\"invalid-ticks\""))
                    }

                http
                    .post(server.url("/clients/alice/player:move")) {
                        contentType(ContentType.Application.Json)
                        setBody("""{"forward":true,"ticks":0}""")
                    }.let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertTrue(body.contains("\"action\":\"player.move\""))
                        assertTrue(body.contains("\"status\":\"FAILED\""))
                        assertTrue(body.contains("\"message\":\"invalid-ticks\""))
                        assertTrue(body.contains("\"moved\":false"))
                        assertTrue(body.contains("\"reason\":\"invalid-ticks\""))
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
                    assertTrue(body.contains("player.move"))
                    assertTrue(body.contains("accepted player.move for alice"))
                    assertFalse(body.contains("unsupported fake action player.fly"))
                    assertTrue(body.contains("client.stopped"))
                }
            }
        }

    @Test
    fun `server does not emit connected event for unobserved connect`() =
        withHttpClient { http ->
            LocalSessionApiServer
                .inMemory(
                    driverFactory =
                        DriverSessionFactory { request ->
                            UnobservedConnectDriverSession(request.id)
                        },
                ).use { server ->
                    server.start()
                    createAlice(http, server)

                    http
                        .post(server.url("/clients/alice:connect")) {
                            contentType(ContentType.Application.Json)
                            setBody("""{"host":"localhost","port":25565}""")
                        }.let { response ->
                            assertEquals(HttpStatusCode.OK, response.status)
                            assertTrue(response.bodyAsText().contains("\"state\":\"RUNNING\""))
                        }

                    http.get(server.url("/clients/alice/events")).let { response ->
                        val body = response.bodyAsText()
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertFalse(body.contains("client.connected"))
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
    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: serverDefaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url] ?: serverDefaultTestBinaryResponse(url)) {
            "missing test binary response for $url"
        }
}

private class RecordingServerStaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    val binaryFetches = mutableListOf<String>()

    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: serverDefaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray {
        binaryFetches += url
        return requireNotNull(binaryResponses[url] ?: serverDefaultTestBinaryResponse(url)) {
            "missing test binary response for $url"
        }
    }
}

private const val SERVER_DEFAULT_TEST_FABRIC_API_VERSION = "0.129.0+1.21.6"
private const val SERVER_OLDER_TEST_FABRIC_API_VERSION = "0.100.8+1.20.6"
private const val SERVER_LATEST_TEST_FABRIC_API_VERSION = "0.153.0+26.2"
private const val SERVER_DEFAULT_TEST_FABRIC_API_METADATA_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
private const val SERVER_DEFAULT_TEST_FABRIC_API_JAR_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar"
private const val SERVER_OLDER_TEST_FABRIC_API_JAR_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.100.8+1.20.6/fabric-api-0.100.8+1.20.6.jar"
private const val SERVER_LATEST_TEST_FABRIC_API_JAR_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.153.0+26.2/fabric-api-0.153.0+26.2.jar"

private fun serverDefaultTestFabricApiMetadata(): String =
    """
    <metadata>
      <groupId>net.fabricmc.fabric-api</groupId>
      <artifactId>fabric-api</artifactId>
      <versioning>
        <versions>
          <version>$SERVER_DEFAULT_TEST_FABRIC_API_VERSION</version>
        </versions>
      </versioning>
    </metadata>
    """.trimIndent()

private fun serverDefaultTestTextResponse(url: String): String? =
    if (url == SERVER_DEFAULT_TEST_FABRIC_API_METADATA_URL) {
        serverDefaultTestFabricApiMetadata()
    } else {
        null
    }

private fun serverDefaultTestBinaryResponse(url: String): ByteArray? =
    if (url == SERVER_DEFAULT_TEST_FABRIC_API_JAR_URL) {
        "fabric-api-jar".encodeToByteArray()
    } else {
        null
    }

private data class RecordedClientRuntimeLaunch(
    val clientId: String,
    val prepared: CachePrepareResult,
    val launch: CacheLaunchPlan,
    val files: InstanceFiles,
    val attachEnvironment: ClientDriverAttachEnvironment?,
)

private class RemoteDriverEndpoint(
    private val clientId: String,
) : AutoCloseable {
    private val port = allocateLoopbackPort()
    private val engine =
        embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
            routing {
                get("/driver/snapshot") {
                    call.respondJson(DriverClientSnapshot(clientId, ClientState.RUNNING))
                }
                post("/driver/connect") {
                    call.respondJson(DriverClientSnapshot(clientId, ClientState.CONNECTED))
                }
                get("/driver/actions") {
                    call.respondJson(listOf(remoteChatActionDescriptor()))
                }
                get("/driver/runtime-metadata") {
                    call.respondJson(remoteDriverRuntimeMetadata())
                }
                get("/driver/runtime-graph") {
                    call.respondJson(remoteRuntimeGraph(clientId))
                }
                post("/driver/invoke") {
                    val invocation = remoteJson.decodeFromString<DriverActionInvocation>(call.receiveText())
                    val message =
                        invocation.arguments["message"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                    invocations += "${invocation.action}:$message"
                    call.respondJson(
                        DriverActionResult(
                            action = invocation.action,
                            status = DriverActionStatus.ACCEPTED,
                            message = message,
                        ),
                    )
                }
                post("/driver/stop") {
                    call.respondJson(DriverClientSnapshot(clientId, ClientState.STOPPED))
                }
                get("/driver/events") {
                    call.respondJson(emptyList<DriverEvent>())
                }
            }
        }
    val invocations = mutableListOf<String>()
    val url: String = "http://127.0.0.1:$port"

    init {
        engine.start()
    }

    override fun close() {
        engine.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
    }
}

private fun remoteChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments = mapOf("message" to DriverActionArgument("string", required = true)),
        source = DriverActionSource.RUNTIME_PROBE,
    )

private fun remoteDriverRuntimeMetadata(): DriverRuntimeMetadata =
    DriverRuntimeMetadata(
        loaderVersion = "0.19.3",
        driver = "craftless-driver-fabric",
        mappings = "craftless-fabric-bindings",
        installedModsFingerprint = "mods:remote",
        registryFingerprint = "registries:remote",
        serverFeatureFingerprint = "server-features:remote",
        permissionsFingerprint = "permissions:remote",
    )

private fun remoteRuntimeGraph(clientId: String): RuntimeCapabilityGraph =
    RuntimeCapabilityGraph(
        clientId = clientId,
        resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
        operations =
            listOf(
                RuntimeOperationNode(
                    id = "player.chat",
                    resource = "player",
                    adapter = "remote.chat",
                    arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                    availability = RuntimeAvailability.available(),
                ),
            ),
    )

private suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.respondJson(value: T) {
    respondText(remoteJson.encodeToString(value), ContentType.Application.Json)
}

private fun allocateLoopbackPort(): Int =
    ServerSocket(0).use { socket ->
        socket.reuseAddress = true
        socket.localPort
    }

private val remoteJson = Json { encodeDefaults = true }

private class RecordingClientRuntimeLauncher : ClientRuntimeLauncher {
    val launches = mutableListOf<RecordedClientRuntimeLaunch>()

    override fun launch(
        request: CreateClientRequest,
        prepared: CachePrepareResult,
        files: InstanceFiles,
        workspaceRoot: Path,
        attachEnvironment: ClientDriverAttachEnvironment?,
    ): ClientRuntimeLaunch {
        launches +=
            RecordedClientRuntimeLaunch(
                clientId = request.id,
                prepared = prepared,
                launch = prepared.launch,
                files = files,
                attachEnvironment = attachEnvironment,
            )
        return ClientRuntimeLaunch(status = ClientRuntimeLaunchStatus.LAUNCHED, pid = 1234)
    }
}

private fun preparedRuntimeMetadataFetcher(): CacheMetadataFetcher {
    val versionUrl = "https://metadata.test/1.21.6.json"
    val clientJarUrl = "https://metadata.test/client.jar"
    val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
    val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-gamma/manifest.json"
    val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-gamma/bin/java"
    val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
    val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
    val pinnedLoaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.16.14/profile/json"
    val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
    val pinnedFabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.14/fabric-loader-0.16.14.jar"
    return ServerStaticCacheMetadataFetcher(
        mapOf(
            MINECRAFT_VERSION_INDEX_URL to
                """
                {
                  "latest": {
                    "release": "1.21.6",
                    "snapshot": "26.3-snapshot-1"
                  },
                  "versions": [
                    { "id": "1.21.6", "url": "$versionUrl" }
                  ]
                }
                """.trimIndent(),
            versionUrl to
                """
                {
                  "id": "1.21.6",
                  "assetIndex": { "id": "1.21.6", "url": "$assetIndexUrl" },
                  "javaVersion": {
                    "component": "java-runtime-gamma",
                    "majorVersion": 21
                  },
                  "mainClass": "test.minecraft.Main",
                  "arguments": {
                    "jvm": ["-cp", "${'$'}{classpath}"],
                    "game": ["--gameDir", "${'$'}{game_directory}"]
                  },
                  "downloads": {
                    "client": { "url": "$clientJarUrl" }
                  }
                }
                """.trimIndent(),
            MINECRAFT_JAVA_RUNTIME_INDEX_URL to serverJavaRuntimeIndexJson(javaRuntimeManifestUrl),
            javaRuntimeManifestUrl to
                """
                {
                  "files": {
                    "bin/java": {
                      "type": "file",
                      "downloads": {
                        "raw": { "url": "$javaExecutableUrl" }
                      }
                    }
                  }
                }
                """.trimIndent(),
            assetIndexUrl to """{"objects":{}}""",
            loaderVersionsUrl to
                """
                [
                  { "loader": { "version": "0.17.2", "stable": true } },
                  { "loader": { "version": "0.16.14", "stable": true } }
                ]
                """.trimIndent(),
            loaderProfileUrl to
                """
                {
                  "id": "fabric-loader-0.17.2-1.21.6",
                  "mainClass": "test.fabric.Main",
                  "libraries": [
                    {
                      "name": "net.fabricmc:fabric-loader:0.17.2",
                      "url": "https://maven.fabricmc.net/"
                    }
                  ]
                }
                """.trimIndent(),
            pinnedLoaderProfileUrl to
                """
                {
                  "id": "fabric-loader-0.16.14-1.21.6",
                  "mainClass": "test.fabric.Main",
                  "libraries": [
                    {
                      "name": "net.fabricmc:fabric-loader:0.16.14",
                      "url": "https://maven.fabricmc.net/"
                    }
                  ]
                }
                """.trimIndent(),
        ),
        binaryResponses =
            mapOf(
                clientJarUrl to "client-jar".encodeToByteArray(),
                javaExecutableUrl to serverFakeJavaBytes("21.0.11"),
                fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                pinnedFabricLoaderJarUrl to "pinned-fabric-loader-jar".encodeToByteArray(),
            ),
    )
}

private fun preparedRuntimeMetadataFetcherWithOlderLane(): CacheMetadataFetcher {
    val currentVersionUrl = "https://metadata.test/1.21.6.json"
    val olderVersionUrl = "https://metadata.test/1.20.6.json"
    val olderClientJarUrl = "https://metadata.test/client-1.20.6.jar"
    val olderAssetIndexUrl = "https://metadata.test/assets/1.20.6.json"
    val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-gamma/manifest.json"
    val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-gamma/bin/java"
    val olderLoaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.20.6"
    val olderLoaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.20.6/0.19.3/profile/json"
    val olderFabricLoaderJarUrl =
        "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
    return ServerStaticCacheMetadataFetcher(
        mapOf(
            MINECRAFT_VERSION_INDEX_URL to
                """
                {
                  "latest": {
                    "release": "1.21.6",
                    "snapshot": "26.3-snapshot-1"
                  },
                  "versions": [
                    { "id": "1.21.6", "url": "$currentVersionUrl" },
                    { "id": "1.20.6", "url": "$olderVersionUrl" }
                  ]
                }
                """.trimIndent(),
            olderVersionUrl to
                """
                {
                  "id": "1.20.6",
                  "assetIndex": { "id": "1.20.6", "url": "$olderAssetIndexUrl" },
                  "javaVersion": {
                    "component": "java-runtime-gamma",
                    "majorVersion": 21
                  },
                  "mainClass": "test.minecraft.Main",
                  "arguments": {
                    "jvm": ["-cp", "${'$'}{classpath}"],
                    "game": ["--gameDir", "${'$'}{game_directory}"]
                  },
                  "downloads": {
                    "client": { "url": "$olderClientJarUrl" }
                  }
                }
                """.trimIndent(),
            MINECRAFT_JAVA_RUNTIME_INDEX_URL to serverJavaRuntimeIndexJson(javaRuntimeManifestUrl),
            javaRuntimeManifestUrl to
                """
                {
                  "files": {
                    "bin/java": {
                      "type": "file",
                      "downloads": {
                        "raw": { "url": "$javaExecutableUrl" }
                      }
                    }
                  }
                }
                """.trimIndent(),
            olderAssetIndexUrl to """{"objects":{}}""",
            olderLoaderVersionsUrl to """[{ "loader": { "version": "0.19.3", "stable": true } }]""",
            olderLoaderProfileUrl to
                """
                {
                  "id": "fabric-loader-0.19.3-1.20.6",
                  "mainClass": "test.fabric.Main",
                  "libraries": [
                    {
                      "name": "net.fabricmc:fabric-loader:0.19.3",
                      "url": "https://maven.fabricmc.net/"
                    }
                  ]
                }
                """.trimIndent(),
            SERVER_DEFAULT_TEST_FABRIC_API_METADATA_URL to
                """
                <metadata>
                  <groupId>net.fabricmc.fabric-api</groupId>
                  <artifactId>fabric-api</artifactId>
                  <versioning>
                    <versions>
                      <version>$SERVER_DEFAULT_TEST_FABRIC_API_VERSION</version>
                      <version>$SERVER_OLDER_TEST_FABRIC_API_VERSION</version>
                    </versions>
                  </versioning>
                </metadata>
                """.trimIndent(),
        ),
        binaryResponses =
            mapOf(
                olderClientJarUrl to "older-client-jar".encodeToByteArray(),
                javaExecutableUrl to serverFakeJavaBytes("21.0.11"),
                olderFabricLoaderJarUrl to "older-fabric-loader-jar".encodeToByteArray(),
                SERVER_OLDER_TEST_FABRIC_API_JAR_URL to "older-fabric-api-jar".encodeToByteArray(),
            ),
    )
}

private fun preparedRuntimeMetadataFetcherWithLatestRelease26(): RecordingServerStaticCacheMetadataFetcher {
    val versionUrl = "https://metadata.test/26.2.json"
    val clientJarUrl = "https://metadata.test/client-26.2.jar"
    val assetIndexUrl = "https://metadata.test/assets/26.2.json"
    val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-epsilon/manifest.json"
    val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-epsilon/bin/java"
    val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/26.2"
    val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/26.2/0.19.3/profile/json"
    val latestFabricLoaderJarUrl =
        "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar"
    return RecordingServerStaticCacheMetadataFetcher(
        mapOf(
            MINECRAFT_VERSION_INDEX_URL to
                """
                {
                  "latest": {
                    "release": "26.2",
                    "snapshot": "26.3-snapshot-1"
                  },
                  "versions": [
                    { "id": "26.2", "url": "$versionUrl" }
                  ]
                }
                """.trimIndent(),
            versionUrl to
                """
                {
                  "id": "26.2",
                  "assetIndex": { "id": "32", "url": "$assetIndexUrl" },
                  "javaVersion": {
                    "component": "java-runtime-epsilon",
                    "majorVersion": 25
                  },
                  "mainClass": "test.minecraft.Main",
                  "arguments": {
                    "jvm": ["-cp", "${'$'}{classpath}"],
                    "game": ["--gameDir", "${'$'}{game_directory}"]
                  },
                  "downloads": {
                    "client": { "url": "$clientJarUrl" }
                  }
                }
                """.trimIndent(),
            MINECRAFT_JAVA_RUNTIME_INDEX_URL to serverJavaRuntimeIndexJson(javaRuntimeManifestUrl, "java-runtime-epsilon"),
            javaRuntimeManifestUrl to
                """
                {
                  "files": {
                    "bin/java": {
                      "type": "file",
                      "downloads": {
                        "raw": { "url": "$javaExecutableUrl" }
                      }
                    }
                  }
                }
                """.trimIndent(),
            assetIndexUrl to
                """
                {
                  "objects": {
                    "minecraft/sounds/example.ogg": {
                      "hash": "0123456789abcdef0123456789abcdef01234567",
                      "size": 1
                    }
                  }
                }
                """.trimIndent(),
            loaderVersionsUrl to """[{ "loader": { "version": "0.19.3", "stable": true } }]""",
            loaderProfileUrl to
                """
                {
                  "id": "fabric-loader-0.19.3-26.2",
                  "mainClass": "test.fabric.Main",
                  "libraries": [
                    {
                      "name": "net.fabricmc:fabric-loader:0.19.3",
                      "url": "https://maven.fabricmc.net/"
                    }
                  ]
                }
                """.trimIndent(),
            SERVER_DEFAULT_TEST_FABRIC_API_METADATA_URL to
                """
                <metadata>
                  <groupId>net.fabricmc.fabric-api</groupId>
                  <artifactId>fabric-api</artifactId>
                  <versioning>
                    <versions>
                      <version>$SERVER_DEFAULT_TEST_FABRIC_API_VERSION</version>
                      <version>$SERVER_LATEST_TEST_FABRIC_API_VERSION</version>
                    </versions>
                  </versioning>
                </metadata>
                """.trimIndent(),
        ),
        binaryResponses =
            mapOf(
                clientJarUrl to "latest-client-jar".encodeToByteArray(),
                javaExecutableUrl to serverFakeJavaBytes("25.0.3"),
                latestFabricLoaderJarUrl to "latest-fabric-loader-jar".encodeToByteArray(),
                SERVER_LATEST_TEST_FABRIC_API_JAR_URL to "latest-fabric-api-jar".encodeToByteArray(),
                "https://resources.download.minecraft.net/01/0123456789abcdef0123456789abcdef01234567" to
                    "a".encodeToByteArray(),
            ),
    )
}

private fun serverFakeJavaBytes(version: String): ByteArray =
    """
    #!/usr/bin/env sh
    echo 'openjdk version "$version" 2026-04-21 LTS' >&2
    echo 'Eclipse Temurin Runtime Environment' >&2
    echo '    os.arch = aarch64' >&2
    """.trimIndent().encodeToByteArray()

private fun serverJavaRuntimeIndexJson(
    manifestUrl: String,
    component: String = "java-runtime-gamma",
): String =
    """
    {
      "linux": {
        "$component": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os": {
        "$component": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os-arm64": {
        "$component": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "windows-x64": {
        "$component": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      }
    }
    """.trimIndent()

private fun waitForRegularFile(path: Path): Boolean {
    repeat(40) {
        if (Files.isRegularFile(path)) {
            return true
        }
        Thread.sleep(50)
    }
    return Files.isRegularFile(path)
}

private fun writeFakeJava(
    path: java.nio.file.Path,
    version: String,
) {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        #!/usr/bin/env sh
        echo 'openjdk version "$version" 2026-04-21 LTS' >&2
        echo 'Eclipse Temurin Runtime Environment' >&2
        echo '    os.arch = aarch64' >&2
        """.trimIndent() + "\n",
    )
    path.toFile().setExecutable(true, true)
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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "scanned world radius 4",
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private class UnobservedConnectDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.UNSUPPORTED)

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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

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
    var fallbackInvokeCount = 0

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
                        )
                    },
            ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        fallbackInvokeCount += 1
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "fallback invoke should not run",
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
        DriverActionResult(invocation.action, DriverActionStatus.UNSUPPORTED, message = "fallback invoke should not run")

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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

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

    override fun runtimeGraph(): RuntimeCapabilityGraph = actions().toRuntimeGraph(clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private fun List<DriverActionDescriptor>.toRuntimeGraph(clientId: String): RuntimeCapabilityGraph {
    val operations = map { action -> action.toRuntimeOperationNode() }
    return RuntimeCapabilityGraph(
        clientId = clientId,
        resources =
            operations
                .map { operation -> operation.resource }
                .distinct()
                .sorted()
                .map { resource -> RuntimeResourceNode(resource, RuntimeAvailability.available()) },
        operations = operations,
    )
}

private fun DriverActionDescriptor.toRuntimeOperationNode(): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = id.substringBeforeLast("."),
        adapter = "test.${id.replace('.', '-')}",
        arguments = arguments.mapValues { (_, argument) -> argument.toRuntimeSchema() },
        result = result.toRuntimeSchema(),
        availability =
            when (availability) {
                DriverActionAvailability.AVAILABLE -> RuntimeAvailability.available()
                DriverActionAvailability.UNAVAILABLE -> RuntimeAvailability.unavailable(requireNotNull(availabilityReason))
            },
    )

private fun DriverActionArgument.toRuntimeSchema(): RuntimeSchema =
    RuntimeSchema(
        type = type,
        required = required,
        properties = properties.mapValues { (_, argument) -> argument.toRuntimeSchema() },
        items = items?.toRuntimeSchema(),
    )

private fun DriverActionResultDescriptor.toRuntimeSchema(): RuntimeSchema =
    properties["data"]?.toRuntimeSchema(required = "data" in required)
        ?: RuntimeSchema(
            type = "object",
            properties =
                properties
                    .filterKeys { name -> name !in setOf("action", "status", "message") }
                    .mapValues { (name, property) ->
                        property.toRuntimeSchema(required = name in required)
                    },
        )

private fun DriverActionResultProperty.toRuntimeSchema(required: Boolean = false): RuntimeSchema =
    RuntimeSchema(
        type = type,
        required = required,
        properties = properties.mapValues { (_, property) -> property.toRuntimeSchema() },
        items = items?.toRuntimeSchema(),
    )
