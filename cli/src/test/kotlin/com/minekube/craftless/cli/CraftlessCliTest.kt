package com.minekube.craftless.cli

import com.minekube.craftless.daemon.CacheMetadataFetcher
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.CacheCleanupResult
import com.minekube.craftless.protocol.CacheExportResult
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import com.minekube.craftless.testkit.FakeDriverSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO

class CraftlessCliTest {
    @Test
    fun `cli registers first jvm command tree`() {
        val commands = CraftlessCli.registeredCommandPaths()

        assertTrue(commands.contains("clients create"))
        assertTrue(commands.contains("clients list"))
        assertTrue(commands.contains("clients <id> get"))
        assertTrue(commands.contains("clients <id> connect"))
        assertTrue(commands.contains("clients <id> stop"))
        assertTrue(commands.contains("clients <id> openapi"))
        assertTrue(commands.contains("clients <id> actions"))
        assertTrue(commands.contains("clients <id> resources"))
        assertTrue(commands.contains("clients <id> run <action>"))
        assertTrue(commands.contains("clients <id> <resource...> <action>"))
        assertTrue(commands.contains("cache prepare"))
        assertTrue(commands.contains("cache export"))
        assertTrue(commands.contains("cache cleanup"))
        assertTrue(commands.contains("server start"))
        assertTrue("clients api" !in commands)
        assertTrue("versions" !in commands)
        assertTrue("profiles" !in commands)
        assertTrue("test run" !in commands)
        assertFalse(
            commands.any { path ->
                path.contains("sendChat") ||
                    path.contains("player chat") ||
                    path.contains("player move") ||
                    path.contains("inventory") ||
                    path.contains("world") ||
                    path.contains("entity") ||
                    path.contains("raycast")
            },
        )
    }

    @Test
    fun `inactive static commands return explicit usage errors`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("versions"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(2, exit)
        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("unknown command versions"))
    }

    @Test
    fun `removed clients api command returns explicit usage error`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("clients", "api", "--once"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(2, exit)
        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("unknown command clients api"))
    }

    @Test
    fun `server start once prints server metadata and keeps server reachable during callback`() {
        val output = StringBuilder()
        var versionStatus = 0

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once"),
                stdout = { output.appendLine(it) },
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            versionStatus = http.get("${metadata.url}/version").status.value
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(200, versionStatus)

        val json = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals(true.toString(), json["ok"]?.jsonPrimitive?.content)
        assertTrue(json["url"]?.jsonPrimitive?.content?.startsWith("http://127.0.0.1:") == true)
        assertEquals("/openapi.json", json["openapi"]?.jsonPrimitive?.content)
        assertEquals("/events", json["events"]?.jsonPrimitive?.content)
    }

    @Test
    fun `server start once reports configured workspace`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-client-files")
        var reportedWorkspace: String? = null

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                afterStart = { metadata ->
                    reportedWorkspace = metadata.workspace
                },
            )

        assertEquals(0, exit)
        assertEquals(workspace.toString(), reportedWorkspace)

        val json = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals(workspace.toString(), json["workspace"]?.jsonPrimitive?.content)
    }

    @Test
    fun `cache prepare creates cache handles in workspace`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-cache")
        val clientJarUrl = "https://metadata.test/client.jar"
        val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
        val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
        val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"

        val exit =
            CraftlessCli.run(
                listOf(
                    "cache",
                    "prepare",
                    "--mc",
                    "1.21.6",
                    "--loader",
                    "fabric",
                    "--workspace",
                    workspace.toString(),
                ),
                stdout = { output.appendLine(it) },
                cacheMetadataFetcher =
                    StaticCacheMetadataFetcher(
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
            )

        assertEquals(0, exit)
        val result = Json.decodeFromString<CachePrepareResult>(output.toString().trim())
        assertEquals("1.21.6", result.minecraftVersion)
        assertTrue(Files.isDirectory(workspace.resolve(result.cacheRoot)))
        assertTrue(Files.isDirectory(workspace.resolve(result.minecraftVersionRoot)))
        assertTrue(Files.isDirectory(workspace.resolve(result.loaderRoot)))
        assertTrue(Files.isDirectory(workspace.resolve(result.runtimeRoot)))
        assertTrue(Files.isRegularFile(workspace.resolve(result.manifest)))
        assertEquals("0.17.2", result.loaderVersion)
        assertTrue(Files.isRegularFile(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
        assertTrue(Files.isRegularFile(workspace.resolve("cache/loaders/fabric/1.21.6/0.17.2/profile.json")))
    }

    @Test
    fun `cache prepare accepts pinned loader version`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-cache-loader-pin")
        val clientJarUrl = "https://metadata.test/client.jar"
        val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
        val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
        val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.16.14/profile/json"

        val exit =
            CraftlessCli.run(
                listOf(
                    "cache",
                    "prepare",
                    "--mc",
                    "1.21.6",
                    "--loader",
                    "fabric",
                    "--loader-version",
                    "0.16.14",
                    "--workspace",
                    workspace.toString(),
                ),
                stdout = { output.appendLine(it) },
                cacheMetadataFetcher =
                    StaticCacheMetadataFetcher(
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
                                  { "loader": { "version": "0.17.2", "stable": true } },
                                  { "loader": { "version": "0.16.14", "stable": true } }
                                ]
                                """.trimIndent(),
                            loaderProfileUrl to """{"id":"fabric-loader-0.16.14-1.21.6"}""",
                        ),
                        binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                    ),
            )

        assertEquals(0, exit)
        val result = Json.decodeFromString<CachePrepareResult>(output.toString().trim())
        assertEquals("0.16.14", result.loaderVersion)
        assertEquals("cache/loaders/fabric/1.21.6/0.16.14", result.loaderRoot)
        assertTrue(Files.isRegularFile(workspace.resolve("cache/loaders/fabric/1.21.6/0.16.14/profile.json")))
    }

    @Test
    fun `cache export and cleanup operate on a prepared manifest`() {
        val prepareOutput = StringBuilder()
        val exportOutput = StringBuilder()
        val cleanupOutput = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-cache-export")
        val clientJarUrl = "https://metadata.test/client.jar"
        val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
        val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
        val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"

        val prepareExit =
            CraftlessCli.run(
                listOf(
                    "cache",
                    "prepare",
                    "--mc",
                    "1.21.6",
                    "--loader",
                    "fabric",
                    "--workspace",
                    workspace.toString(),
                ),
                stdout = { prepareOutput.appendLine(it) },
                cacheMetadataFetcher =
                    StaticCacheMetadataFetcher(
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """{"versions":[{"id":"1.21.6","url":"https://metadata.test/1.21.6.json"}]}""",
                            "https://metadata.test/1.21.6.json" to
                                """{"id":"1.21.6","assetIndex":{"id":"1.21.6","url":"$assetIndexUrl"},"downloads":{"client":{"url":"$clientJarUrl"}}}""",
                            assetIndexUrl to """{"objects":{}}""",
                            loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                            loaderProfileUrl to """{"id":"fabric-loader-0.17.2-1.21.6"}""",
                        ),
                        binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                    ),
            )
        val prepared = Json.decodeFromString<CachePrepareResult>(prepareOutput.toString().trim())

        val exportExit =
            CraftlessCli.run(
                listOf(
                    "cache",
                    "export",
                    "--manifest",
                    prepared.manifest,
                    "--archive",
                    "exports/prepared-cache.zip",
                    "--workspace",
                    workspace.toString(),
                ),
                stdout = { exportOutput.appendLine(it) },
            )

        val cleanupExit =
            CraftlessCli.run(
                listOf(
                    "cache",
                    "cleanup",
                    "--manifest",
                    prepared.manifest,
                    "--workspace",
                    workspace.toString(),
                ),
                stdout = { cleanupOutput.appendLine(it) },
            )

        assertEquals(0, prepareExit)
        assertEquals(0, exportExit)
        assertEquals(0, cleanupExit)
        val exported = Json.decodeFromString<CacheExportResult>(exportOutput.toString().trim())
        val cleaned = Json.decodeFromString<CacheCleanupResult>(cleanupOutput.toString().trim())
        assertEquals("exports/prepared-cache.zip", exported.archive)
        assertTrue(Files.isRegularFile(workspace.resolve(exported.archive)))
        assertTrue(cleaned.deleted.contains(prepared.manifest))
        assertTrue(!Files.exists(workspace.resolve(prepared.manifest)))
    }

    @Test
    fun `clients create posts an offline client request to daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "alice",
                        "--api",
                        server.url,
                        "--version",
                        "1.21.4",
                        "--loader",
                        "FABRIC",
                        "--offline-name",
                        "Alice",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val client = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("alice", client["id"]?.jsonPrimitive?.content)
        assertEquals("RUNNING", client["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients list fetches clients from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "list",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val clients = Json.parseToJsonElement(output.toString().trim()).jsonArray
        assertTrue(clients.any { it.jsonObject["id"]?.jsonPrimitive?.content == "alice" })
    }

    @Test
    fun `clients list jsonl prints one client per line`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()
            server.createOfflineClient("bob", "Bob")

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "list",
                        "--api",
                        server.url,
                        "--jsonl",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val lines = output.lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(2, lines.size)
        assertEquals(
            "alice",
            Json
                .parseToJsonElement(lines[0])
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "bob",
            Json
                .parseToJsonElement(lines[1])
                .jsonObject["id"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `clients get fetches one client from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "get",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val client = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("alice", client["id"]?.jsonPrimitive?.content)
        assertEquals("RUNNING", client["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients list uses craftless api environment variable`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf("clients", "list"),
                    stdout = { output.appendLine(it) },
                    env = mapOf("CRAFTLESS" to server.url),
                )

            assertEquals(0, exit)
        }

        val clients = Json.parseToJsonElement(output.toString().trim()).jsonArray
        assertTrue(clients.any { it.jsonObject["id"]?.jsonPrimitive?.content == "alice" })
    }

    @Test
    fun `explicit api option wins over craftless api environment variable`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf("clients", "list", "--api", server.url),
                    stdout = { output.appendLine(it) },
                    env =
                        mapOf(
                            "CRAFTLESS" to "http://127.0.0.1:1",
                        ),
                )

            assertEquals(0, exit)
        }

        val clients = Json.parseToJsonElement(output.toString().trim()).jsonArray
        assertTrue(clients.any { it.jsonObject["id"]?.jsonPrimitive?.content == "alice" })
    }

    @Test
    fun `clients connect posts connection target to daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "connect",
                        "--api",
                        server.url,
                        "--host",
                        "localhost",
                        "--port",
                        "25565",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val client = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("alice", client["id"]?.jsonPrimitive?.content)
        assertEquals("CONNECTED", client["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients stop posts stop lifecycle method to daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "stop",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val client = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("alice", client["id"]?.jsonPrimitive?.content)
        assertEquals("STOPPED", client["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients run posts typed action args to daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--arg",
                        "message=hello from cli",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
        assertEquals("hello from cli", response["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients run rejects actions missing from live openapi action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.fly",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.fly is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `clients run rejects actions missing from live openapi`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        InconsistentOpenApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--arg",
                        "message=hello",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
            assertFalse(server.runCalled)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `clients run uses live openapi action metadata as argument schema`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        StaleActionsProjectionServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
            assertFalse(server.runCalled)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat requires argument message"))
    }

    @Test
    fun `generated client action alias rejects actions missing from live openapi action list`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        InconsistentAliasOpenApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--message",
                        "hello",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
            assertFalse(server.aliasCalled)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `generated client action alias rejects live openapi route mapped to another action`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        MismatchedAliasOpenApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--message",
                        "hello",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
            assertFalse(server.aliasCalled)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `generated client action alias uses live openapi action metadata as argument schema`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        StaleActionsProjectionServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
            assertFalse(server.aliasCalled)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat requires argument message"))
    }

    @Test
    fun `clients actions fetches discovered actions from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "actions",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val actions = Json.parseToJsonElement(output.toString().trim()).jsonArray
        assertTrue(actions.any { it.jsonObject["id"]?.jsonPrimitive?.content == "player.chat" })
        assertTrue(actions.any { it.jsonObject["id"]?.jsonPrimitive?.content == "player.move" })
    }

    @Test
    fun `clients resources fetches live resource projection from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "resources",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val resources = Json.parseToJsonElement(output.toString().trim()).jsonArray
        val player = resources.single { it.jsonObject["id"]?.jsonPrimitive?.content == "player" }.jsonObject
        assertTrue(
            player["actions"]
                ?.jsonArray
                ?.any { it.jsonPrimitive.content == "player.chat" } == true,
        )
        assertEquals("available", player["availability"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients openapi fetches live per client spec from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "openapi",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val document = Json.parseToJsonElement(output.toString().trim()).jsonObject
        val extensions = document["x-craftless"]?.jsonObject
        assertEquals("alice", extensions?.get("x-craftless-client-id")?.jsonPrimitive?.content)
        assertTrue(document["paths"]?.jsonObject?.containsKey("/clients/alice:run") == true)
        assertTrue(
            document["x-craftless-actions"]?.jsonArray?.any {
                it.jsonObject["id"]?.jsonPrimitive?.content == "player.chat"
            } == true,
        )
    }

    @Test
    fun `clients run preserves boolean and integer action args`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.move",
                        "--api",
                        server.url,
                        "--arg",
                        "forward=true",
                        "--arg",
                        "ticks=20",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.move", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients run rejects args missing from runtime action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--arg",
                        "message=hello",
                        "--arg",
                        "surprise=value",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat does not declare argument surprise"))
    }

    @Test
    fun `clients run rejects missing required runtime action args`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat requires argument message"))
    }

    @Test
    fun `generated client action alias dispatches from runtime action metadata`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--message",
                        "hello from alias cli",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
        assertEquals("hello from alias cli", response["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `generated client action alias maps single positional arg to required action argument`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "hello from positional alias",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
        assertEquals("hello from positional alias", response["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `generated client action alias rejects missing required runtime action args`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat requires argument message"))
    }

    @Test
    fun `generated client action alias rejects arg pairs missing from runtime action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--arg",
                        "message=hello",
                        "--arg",
                        "surprise=value",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.chat does not declare argument surprise"))
    }

    @Test
    fun `generated client action alias preserves typed args from action schema`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "move",
                        "--api",
                        server.url,
                        "--forward",
                        "--ticks",
                        "20",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.move", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `generated nested client action alias dispatches from runtime action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer(
            driverFactory =
                DriverSessionFactory { request ->
                    WorldBlockBreakDriver(FakeDriverSession(request.id))
                },
        ).use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "world",
                        "block",
                        "break",
                        "--api",
                        server.url,
                        "--max-distance",
                        "4.0",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("world.block.break", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `generated client action alias help is loaded from runtime action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "move",
                        "--help",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        assertEquals("", errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Action: player.move"))
        assertTrue(help.contains("Route: POST /clients/alice/player:move"))
        assertTrue(help.contains("Usage: craftless clients alice player move"))
        assertTrue(help.contains("--forward boolean"))
        assertTrue(help.contains("--ticks integer"))
    }

    @Test
    fun `generated client action alias help rejects actions missing from live openapi action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "fly",
                        "--help",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.fly is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `generated client action alias rejects actions missing from live openapi action metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "fly",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("action player.fly is not described by live OpenAPI for client alice"))
    }

    @Test
    fun `clients run returns nonzero for daemon errors`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "missing",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--arg",
                        "message=hello",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("MISSING_CLIENT"))
    }

    @Test
    fun `clients run returns nonzero when runtime action result fails`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer(
            driverFactory =
                DriverSessionFactory { request ->
                    FailingActionDriver(FakeDriverSession(request.id))
                },
        ).use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.fail",
                        "--api",
                        server.url,
                        "--arg",
                        "message=boom",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(1, exit)
        }

        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("\"status\":\"FAILED\""))
        assertTrue(errors.toString().contains("driver rejected player.fail"))
    }

    private class LocalTestApiServer(
        driverFactory: DriverSessionFactory =
            DriverSessionFactory { request ->
                FakeDriverSession(request.id)
            },
    ) : AutoCloseable {
        private val server =
            com.minekube.craftless.daemon.LocalSessionApiServer.inMemory(
                driverFactory = driverFactory,
            )
        val url: String

        init {
            server.start()
            url = server.url("")
        }

        fun createAlice() {
            createOfflineClient("alice", "Alice")
        }

        fun createOfflineClient(
            id: String,
            name: String,
        ) {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.post("$url/clients") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "id": "$id",
                              "version": "1.21.4",
                              "loader": "FABRIC",
                              "profile": { "kind": "OFFLINE", "name": "$name" }
                            }
                            """.trimIndent(),
                        )
                    }
                }
            }
        }

        override fun close() {
            server.close()
        }
    }

    private class FailingActionDriver(
        private val delegate: DriverSession,
    ) : DriverSession by delegate {
        override fun actions(): List<DriverActionDescriptor> =
            delegate.actions() +
                DriverActionDescriptor(
                    id = "player.fail",
                    schemaVersion = "1",
                    arguments =
                        mapOf(
                            "message" to DriverActionArgument("string", required = true),
                        ),
                )

        override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
            if (invocation.action == "player.fail") {
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.FAILED,
                    message = "driver rejected ${invocation.action}",
                )
            } else {
                delegate.invoke(invocation)
            }
    }

    private class WorldBlockBreakDriver(
        private val delegate: DriverSession,
    ) : DriverSession by delegate {
        override fun actions(): List<DriverActionDescriptor> =
            delegate.actions() +
                DriverActionDescriptor(
                    id = "world.block.break",
                    schemaVersion = "1",
                    arguments =
                        mapOf(
                            "max-distance" to DriverActionArgument("number"),
                        ),
                )

        override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
            if (invocation.action == "world.block.break") {
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.ACCEPTED,
                    message = "block break accepted",
                )
            } else {
                delegate.invoke(invocation)
            }
    }

    private class InconsistentOpenApiServer : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/clients/alice/actions") {
                        call.respondText(
                            """
                            [
                              {
                                "id": "player.chat",
                                "schemaVersion": "1",
                                "args": { "message": { "type": "string", "required": true } }
                              }
                            ]
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    get("/clients/alice/openapi.json") {
                        call.respondText(
                            """
                            {
                              "openapi": "3.1.0",
                              "info": { "title": "Inconsistent test API", "version": "1" },
                              "paths": {},
                              "x-craftless": {},
                              "x-craftless-actions": []
                            }
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice:run") {
                        runCalled = true
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"should not run"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"
        var runCalled: Boolean = false
            private set

        init {
            server.start()
        }

        override fun close() {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }

        private fun allocateLoopbackPort(): Int =
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                socket.localPort
            }
    }

    private class InconsistentAliasOpenApiServer : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/clients/alice/actions") {
                        call.respondText(
                            """
                            [
                              {
                                "id": "player.chat",
                                "schemaVersion": "1",
                                "args": { "message": { "type": "string", "required": true } }
                              }
                            ]
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    get("/clients/alice/openapi.json") {
                        call.respondText(
                            """
                            {
                              "openapi": "3.1.0",
                              "info": { "title": "Inconsistent alias API", "version": "1" },
                              "paths": {
                                "/clients/alice/player:chat": {
                                  "post": {
                                    "operationId": "runPlayerChat",
                                    "tags": ["clients"],
                                    "responses": { "200": { "description": "OK" } },
                                    "x-craftless": {
                                      "x-craftless-owner": "clients",
                                      "x-craftless-target": "client",
                                      "x-craftless-return": "value",
                                      "x-craftless-source": "action",
                                      "x-craftless-member": "run",
                                      "x-craftless-action": "player.chat"
                                    }
                                  }
                                }
                              },
                              "x-craftless": {},
                              "x-craftless-actions": []
                            }
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice/player:chat") {
                        aliasCalled = true
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"should not run"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"
        var aliasCalled: Boolean = false
            private set

        init {
            server.start()
        }

        override fun close() {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }

        private fun allocateLoopbackPort(): Int =
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                socket.localPort
            }
    }

    private class StaleActionsProjectionServer : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/clients/alice/actions") {
                        call.respondText(
                            """
                            []
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    get("/clients/alice/openapi.json") {
                        call.respondText(
                            """
                            {
                              "openapi": "3.1.0",
                              "info": { "title": "Stale projection test API", "version": "1" },
                              "paths": {
                                "/clients/alice:run": {
                                  "post": {
                                    "operationId": "runClientAction",
                                    "tags": ["clients"],
                                    "responses": { "200": { "description": "OK" } },
                                    "x-craftless": {
                                      "x-craftless-owner": "clients",
                                      "x-craftless-target": "client",
                                      "x-craftless-return": "value",
                                      "x-craftless-source": "action",
                                      "x-craftless-member": "run"
                                    }
                                  }
                                },
                                "/clients/alice/player:chat": {
                                  "post": {
                                    "operationId": "runPlayerChat",
                                    "tags": ["clients"],
                                    "responses": { "200": { "description": "OK" } },
                                    "x-craftless": {
                                      "x-craftless-owner": "clients",
                                      "x-craftless-target": "client",
                                      "x-craftless-return": "value",
                                      "x-craftless-source": "action",
                                      "x-craftless-member": "run",
                                      "x-craftless-action": "player.chat"
                                    }
                                  }
                                }
                              },
                              "x-craftless": {},
                              "x-craftless-actions": [
                                {
                                  "id": "player.chat",
                                  "schemaVersion": "1",
                                  "args": { "message": { "type": "string", "required": true } }
                                }
                              ]
                            }
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice:run") {
                        runCalled = true
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"should not run"}""",
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice/player:chat") {
                        aliasCalled = true
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"should not run"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"
        var runCalled: Boolean = false
            private set
        var aliasCalled: Boolean = false
            private set

        init {
            server.start()
        }

        override fun close() {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }

        private fun allocateLoopbackPort(): Int =
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                socket.localPort
            }
    }

    private class MismatchedAliasOpenApiServer : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/clients/alice/actions") {
                        call.respondText(
                            """
                            [
                              {
                                "id": "player.chat",
                                "schemaVersion": "1",
                                "args": { "message": { "type": "string", "required": true } }
                              }
                            ]
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    get("/clients/alice/openapi.json") {
                        call.respondText(
                            """
                            {
                              "openapi": "3.1.0",
                              "info": { "title": "Mismatched alias API", "version": "1" },
                              "paths": {
                                "/clients/alice/player:chat": {
                                  "post": {
                                    "operationId": "runPlayerChat",
                                    "tags": ["clients"],
                                    "responses": { "200": { "description": "OK" } },
                                    "x-craftless": {
                                      "x-craftless-owner": "clients",
                                      "x-craftless-target": "client",
                                      "x-craftless-return": "value",
                                      "x-craftless-source": "action",
                                      "x-craftless-member": "run",
                                      "x-craftless-action": "player.move"
                                    }
                                  }
                                }
                              },
                              "x-craftless": {},
                              "x-craftless-actions": [
                                {
                                  "id": "player.chat",
                                  "schemaVersion": "1",
                                  "args": { "message": { "type": "string", "required": true } }
                                }
                              ]
                            }
                            """.trimIndent(),
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice/player:chat") {
                        aliasCalled = true
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"should not run"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"
        var aliasCalled: Boolean = false
            private set

        init {
            server.start()
        }

        override fun close() {
            server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
        }

        private fun allocateLoopbackPort(): Int =
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                socket.localPort
            }
    }
}

private class StaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url]) {
            "missing test binary response for $url"
        }
}
