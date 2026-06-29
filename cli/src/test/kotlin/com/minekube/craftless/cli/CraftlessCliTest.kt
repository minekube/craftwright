package com.minekube.craftless.cli

import com.minekube.craftless.daemon.CacheMetadataFetcher
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.CacheCleanupResult
import com.minekube.craftless.protocol.CacheExportResult
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.testkit.FakeDriverSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.ktor.server.cio.CIO as ServerCIO

class CraftlessCliTest {
    @Test
    fun `adaptive cli does not fetch actions projection as gameplay authority`() {
        val source = Files.readString(repositoryRoot().resolve("cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt"))

        assertFalse(source.contains("/clients/\$clientId/actions"))
        assertFalse(source.contains("/clients/\${clientId}/actions"))
        assertTrue(source.contains("/clients/\$clientId/openapi.json"))
    }

    @Test
    fun `cli does not hardcode supervisor api route dispatch branches`() {
        val source = Files.readString(repositoryRoot().resolve("cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt"))

        assertFalse(source.contains("args.take(2) == listOf(\"clients\", \"create\")"))
        assertFalse(source.contains("args.take(2) == listOf(\"clients\", \"list\")"))
        assertFalse(source.contains("args.take(3) == listOf(\"runtimes\", \"java\", \"list\")"))
        assertFalse(source.contains("args.take(2) == listOf(\"cache\", \"prepare\")"))
        assertFalse(source.contains("""http.post("${'$'}{api.trimEnd('/')}/clients")"""))
    }

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
        assertTrue(commands.contains("clients <id> query <target>"))
        assertTrue(commands.contains("clients <id> events"))
        assertTrue(commands.contains("clients <id> tools"))
        assertTrue(commands.contains("clients <id> run <action>"))
        assertTrue(commands.contains("clients <id> <resource...> <action>"))
        assertTrue(commands.contains("cache prepare"))
        assertTrue(commands.contains("cache export"))
        assertTrue(commands.contains("cache cleanup"))
        assertTrue(commands.contains("runtimes java list"))
        assertTrue(commands.contains("runtimes java resolve"))
        assertTrue(commands.contains("daemon start"))
        assertFalse(commands.contains("server start"))
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

    @Test
    fun `root help prints stable command overview`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("--help"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(0, exit)
        assertEquals("", errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Usage: craftless <command> [args]"))
        assertTrue(help.contains("daemon start"))
        assertFalse(help.contains("server start"))
        assertTrue(help.contains("clients <id> <resource...> <action>"))
        assertFalse(help.contains("player chat"))
        assertFalse(help.contains("world block break"))
    }

    @Test
    fun `clients help prints stable and adaptive command guidance`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("clients", "--help"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(0, exit)
        assertEquals("", errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Usage: craftless clients <command> [args]"))
        assertTrue(help.contains("clients create"))
        assertTrue(help.contains("clients <id> run <action>"))
        assertTrue(help.contains("Generated gameplay commands are loaded from each live client's OpenAPI document."))
        assertFalse(help.contains("player chat"))
        assertFalse(help.contains("world block break"))
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
    fun `daemon start once prints server metadata and keeps server reachable during callback`() {
        val output = StringBuilder()
        var versionStatus = 0

        val exit =
            CraftlessCli.run(
                listOf("daemon", "start", "--once"),
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
    fun `server start accepts host for container port publishing`() {
        val output = StringBuilder()
        var versionStatus = 0

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--host", "0.0.0.0"),
                stdout = { output.appendLine(it) },
                afterStart = { metadata ->
                    val port = metadata.url.substringAfterLast(":").toInt()
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            versionStatus = http.get("http://127.0.0.1:$port/version").status.value
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(200, versionStatus)

        val json = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertTrue(json["url"]?.jsonPrimitive?.content?.startsWith("http://0.0.0.0:") == true)
    }

    @Test
    fun `server start forwards configured fabric driver mod environment`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-driver-mod")
        val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
        Files.writeString(driverMod, "driver-mod")
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env =
                    mapOf(
                        "CRAFTLESS_FABRIC_DRIVER_MOD" to driverMod.toString(),
                    ),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(201, createStatus, createBody)
        val modCache = workspace.resolve("cache/mods/craftless")
        assertTrue(Files.isDirectory(modCache))
        Files.list(modCache).use { cachedMods ->
            assertTrue(
                cachedMods.anyMatch { cached -> Files.readString(cached) == "driver-mod" },
                "configured driver mod was not copied into the Craftless mod cache",
            )
        }
    }

    @Test
    fun `server start uses packaged fabric driver mod when env is absent`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-packaged-driver-mod")
        val distribution = Files.createTempDirectory("craftless-cli-distribution")
        val packagedDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
        Files.createDirectories(packagedDriverMod.parent)
        Files.writeString(packagedDriverMod, "packaged-driver-mod")
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env = emptyMap(),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                distributionRoot = distribution,
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(201, createStatus, createBody)
        val modCache = workspace.resolve("cache/mods/craftless")
        assertTrue(Files.isDirectory(modCache))
        Files.list(modCache).use { cachedMods ->
            assertTrue(
                cachedMods.anyMatch { cached -> Files.readString(cached) == "packaged-driver-mod" },
                "packaged driver mod was not copied into the Craftless mod cache",
            )
        }
    }

    @Test
    fun `server start uses packaged driver mod manifest when env is absent`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-packaged-driver-mod-manifest")
        val distribution = Files.createTempDirectory("craftless-cli-manifest-distribution")
        val manifestDriverMod = distribution.resolve("mods/manifest-driver.jar")
        val fallbackDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
        Files.createDirectories(manifestDriverMod.parent)
        Files.writeString(manifestDriverMod, "manifest-driver-mod")
        Files.writeString(fallbackDriverMod, "fallback-driver-mod")
        Files.writeString(
            distribution.resolve("driver-mods.json"),
            """
            {
              "entries": [
                {
                  "loader": "FABRIC",
                  "minecraftVersion": "1.21.6",
                  "loaderVersion": "0.17.2",
                  "path": "mods/manifest-driver.jar"
                }
              ]
            }
            """.trimIndent(),
        )
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env = emptyMap(),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                distributionRoot = distribution,
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(201, createStatus, createBody)
        val modCache = workspace.resolve("cache/mods/craftless")
        assertTrue(Files.isDirectory(modCache))
        Files.list(modCache).use { cachedMods ->
            val cachedContents = cachedMods.map { cached -> Files.readString(cached) }.toList()
            assertTrue("manifest-driver-mod" in cachedContents)
            assertTrue("fallback-driver-mod" !in cachedContents)
        }
    }

    @Test
    fun `server start defaults loader version from packaged driver mod manifest`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-packaged-driver-mod-manifest-default")
        val distribution = Files.createTempDirectory("craftless-cli-manifest-default-distribution")
        val manifestDriverMod = distribution.resolve("mods/manifest-driver.jar")
        val fallbackDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
        Files.createDirectories(manifestDriverMod.parent)
        Files.writeString(manifestDriverMod, "manifest-driver-mod")
        Files.writeString(fallbackDriverMod, "fallback-driver-mod")
        Files.writeString(
            distribution.resolve("driver-mods.json"),
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
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env = emptyMap(),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                distributionRoot = distribution,
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(201, createStatus, createBody)
        assertTrue(Files.exists(workspace.resolve("cache/prepared/1.21.6-fabric-0.16.14.json")))
        val modCache = workspace.resolve("cache/mods/craftless")
        assertTrue(Files.isDirectory(modCache))
        Files.list(modCache).use { cachedMods ->
            val cachedContents = cachedMods.map { cached -> Files.readString(cached) }.toList()
            assertTrue("manifest-driver-mod" in cachedContents)
            assertTrue("fallback-driver-mod" !in cachedContents)
        }
    }

    @Test
    fun `server start resolves aliases before packaged driver mod manifest defaults`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-packaged-driver-mod-manifest-alias")
        val distribution = Files.createTempDirectory("craftless-cli-manifest-alias-distribution")
        val manifestDriverMod = distribution.resolve("mods/manifest-driver.jar")
        val fallbackDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
        Files.createDirectories(manifestDriverMod.parent)
        Files.writeString(manifestDriverMod, "manifest-driver-mod")
        Files.writeString(fallbackDriverMod, "fallback-driver-mod")
        Files.writeString(
            distribution.resolve("driver-mods.json"),
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
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env = emptyMap(),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                distributionRoot = distribution,
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(201, createStatus, createBody)
        assertTrue(Files.exists(workspace.resolve("cache/prepared/1.21.6-fabric-0.16.14.json")))
        val modCache = workspace.resolve("cache/mods/craftless")
        assertTrue(Files.isDirectory(modCache))
        Files.list(modCache).use { cachedMods ->
            val cachedContents = cachedMods.map { cached -> Files.readString(cached) }.toList()
            assertTrue("manifest-driver-mod" in cachedContents)
            assertTrue("fallback-driver-mod" !in cachedContents)
        }
    }

    @Test
    fun `server start rejects packaged driver mod manifest misses`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-packaged-driver-mod-manifest-miss")
        val distribution = Files.createTempDirectory("craftless-cli-manifest-miss-distribution")
        val fallbackDriverMod = distribution.resolve("mods/craftless-driver-fabric.jar")
        Files.createDirectories(fallbackDriverMod.parent)
        Files.writeString(fallbackDriverMod, "fallback-driver-mod")
        Files.writeString(
            distribution.resolve("driver-mods.json"),
            """
            {
              "entries": [
                {
                  "loader": "FABRIC",
                  "minecraftVersion": "1.21.6",
                  "loaderVersion": "0.17.2",
                  "path": "mods/craftless-driver-fabric.jar"
                }
              ]
            }
            """.trimIndent(),
        )
        var createStatus = 0
        var createBody = ""

        val exit =
            CraftlessCli.run(
                listOf("server", "start", "--once", "--workspace", workspace.toString()),
                stdout = { output.appendLine(it) },
                env = emptyMap(),
                cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                distributionRoot = distribution,
                afterStart = { metadata ->
                    kotlinx.coroutines.runBlocking {
                        HttpClient(CIO).use { http ->
                            val response =
                                http.post("${metadata.url}/clients") {
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
                            createStatus = response.status.value
                            createBody = response.bodyAsText()
                        }
                    }
                },
            )

        assertEquals(0, exit)
        assertEquals(400, createStatus, createBody)
        assertTrue(createBody.contains("driver mod manifest"), createBody)
        assertTrue(!Files.exists(workspace.resolve("cache/mods/craftless")))
    }

    @Test
    fun `client create uses configured api request timeout`() {
        SlowCreateApiServer(delayMillis = 250).use { server ->
            val timeoutOutput = StringBuilder()
            val timeoutErrors = StringBuilder()
            val timeoutExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "alice",
                        "--version",
                        "1.21.6",
                        "--loader",
                        "fabric",
                        "--offline-name",
                        "Alice",
                        "--api",
                        server.url,
                    ),
                    stdout = { timeoutOutput.appendLine(it) },
                    stderr = { timeoutErrors.appendLine(it) },
                    env = mapOf("CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS" to "50"),
                )

            assertEquals(2, timeoutExit)
            assertEquals("", timeoutOutput.toString())
            assertTrue(timeoutErrors.toString().contains("timeout", ignoreCase = true))

            val output = StringBuilder()
            val errors = StringBuilder()
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "alice",
                        "--version",
                        "1.21.6",
                        "--loader",
                        "fabric",
                        "--offline-name",
                        "Alice",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                    env = mapOf("CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS" to "2000"),
                )

            assertEquals(0, exit, errors.toString())
            assertEquals("", errors.toString())
            assertTrue(output.toString().contains("\"id\":\"alice\""))
        }
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
    fun `runtimes java list returns supervisor runtime candidates`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-java-list")
        val java = workspace.resolve("cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java")
        writeFakeJava(java, "25.0.3")

        LocalTestApiServer(workspaceRoot = workspace).use { server ->
            val exit =
                CraftlessCli.run(
                    listOf("runtimes", "java", "list", "--api", server.url),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val runtimes = Json.parseToJsonElement(output.toString().trim()).jsonObject["runtimes"]!!.jsonArray
        assertTrue(
            runtimes.any { runtime ->
                runtime.jsonObject["executable"]?.jsonPrimitive?.content ==
                    "cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java"
            },
        )
    }

    @Test
    fun `runtimes java resolve resolves by minecraft version through supervisor api`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-java-resolve")
        val java = workspace.resolve("cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java")
        writeFakeJava(java, "25.0.3")

        LocalTestApiServer(
            workspaceRoot = workspace,
            cacheMetadataFetcher =
                StaticCacheMetadataFetcher(
                    mapOf(
                        MINECRAFT_VERSION_INDEX_URL to
                            """
                            {
                              "versions": [
                                { "id": "26.2", "url": "https://metadata.test/26.2.json" }
                              ]
                            }
                            """.trimIndent(),
                        "https://metadata.test/26.2.json" to
                            """
                            {
                              "id": "26.2",
                              "javaVersion": {
                                "component": "java-runtime-gamma",
                                "majorVersion": 25
                              }
                            }
                            """.trimIndent(),
                    ),
                ),
        ).use { server ->
            val exit =
                CraftlessCli.run(
                    listOf("runtimes", "java", "resolve", "--mc", "26.2", "--api", server.url),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val selection = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("SELECTED", selection["status"]?.jsonPrimitive?.content)
        assertEquals(
            25,
            selection["requirement"]
                ?.jsonObject
                ?.get("majorVersion")
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertEquals(
            "cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java",
            selection["selected"]
                ?.jsonObject
                ?.get("executable")
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun `runtimes java resolve resolves latest release alias through supervisor api`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-java-resolve-latest")
        val java = workspace.resolve("cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java")
        writeFakeJava(java, "25.0.3")

        LocalTestApiServer(
            workspaceRoot = workspace,
            cacheMetadataFetcher =
                StaticCacheMetadataFetcher(
                    mapOf(
                        MINECRAFT_VERSION_INDEX_URL to
                            """
                            {
                              "latest": {
                                "release": "26.2",
                                "snapshot": "26.3-snapshot-1"
                              },
                              "versions": [
                                { "id": "26.2", "url": "https://metadata.test/26.2.json" }
                              ]
                            }
                            """.trimIndent(),
                        "https://metadata.test/26.2.json" to
                            """
                            {
                              "id": "26.2",
                              "javaVersion": {
                                "component": "java-runtime-gamma",
                                "majorVersion": 25
                              }
                            }
                            """.trimIndent(),
                    ),
                ),
        ).use { server ->
            val exit =
                CraftlessCli.run(
                    listOf("runtimes", "java", "resolve", "--mc", "latest-release", "--api", server.url),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val selection = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("SELECTED", selection["status"]?.jsonPrimitive?.content)
        assertEquals(
            25,
            selection["requirement"]
                ?.jsonObject
                ?.get("majorVersion")
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertEquals(
            "cache/runtimes/mac-os-arm64/java-runtime-gamma/image/bin/java",
            selection["selected"]
                ?.jsonObject
                ?.get("executable")
                ?.jsonPrimitive
                ?.content,
        )
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
    fun `clients create sends requested loader version lane`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "alice",
                        "--api",
                        server.url,
                        "--version",
                        "1.21.6",
                        "--loader",
                        "FABRIC",
                        "--loader-version",
                        "0.16.14",
                        "--offline-name",
                        "Alice",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
            assertEquals("0.16.14", request["loaderVersion"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `clients create defaults to muted non visible request without offline name`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "bot",
                        "--api",
                        server.url,
                        "--version",
                        "latest-release",
                        "--loader",
                        "fabric",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
            assertFalse(request.containsKey("profile"))
            val presentation = requireNotNull(request["presentation"]).jsonObject
            assertEquals("NONE", presentation["window"]?.jsonPrimitive?.content)
            assertEquals("MUTED", presentation["audio"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `generated supervisor route creates client from openapi cli metadata`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "bot",
                        "--api",
                        server.url,
                        "--version",
                        "latest-release",
                        "--loader",
                        "fabric",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
            assertEquals("bot", request["id"]?.jsonPrimitive?.content)
            assertEquals("latest-release", request["version"]?.jsonPrimitive?.content)
            assertEquals("FABRIC", request["loader"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `generated supervisor route help is loaded from supervisor openapi`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf("clients", "create", "--help", "--api", server.url),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val help = output.toString()
            assertTrue(help.contains("Route: POST /clients"))
            assertTrue(help.contains("Usage: craftless clients create <id>"))
            assertTrue(help.contains("launches a new daemon-managed real Minecraft Java client process"))
            assertTrue(help.contains("is not a selector, retry, or reuse operation"))
            assertTrue(help.contains("Creating fresh timestamped ids for retries leaves multiple Minecraft clients running"))
            assertTrue(help.contains("--version string required"))
            assertTrue(help.contains("--loader string required"))
            assertTrue(help.contains("--audio string default=MUTED enum=MUTED|DEFAULT"))
        }
    }

    @Test
    fun `clients create sends explicit visible default audio request`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "robin",
                        "--api",
                        server.url,
                        "--version",
                        "latest-release",
                        "--loader",
                        "fabric",
                        "--offline-name",
                        "Robin",
                        "--visible",
                        "--audio",
                        "default",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
            val profile = requireNotNull(request["profile"]).jsonObject
            assertEquals("Robin", profile["name"]?.jsonPrimitive?.content)
            val presentation = requireNotNull(request["presentation"]).jsonObject
            assertEquals("VISIBLE", presentation["window"]?.jsonPrimitive?.content)
            assertEquals("DEFAULT", presentation["audio"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `clients create rejects unknown audio presentation`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "create",
                        "bot",
                        "--api",
                        server.url,
                        "--version",
                        "latest-release",
                        "--loader",
                        "fabric",
                        "--audio",
                        "loud",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(2, exit)
            assertEquals("", output.toString())
            assertTrue(errors.toString().contains("--audio must be muted or default"))
            assertEquals(emptyList(), server.createBodies)
        }
    }

    @Test
    fun `clients create usage includes loader version option`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf(
                    "clients",
                    "create",
                    "alice",
                    "--version",
                    "1.21.6",
                ),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(2, exit)
        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("[--loader-version <version>]"))
        assertTrue(errors.toString().contains("[--visible]"))
        assertTrue(errors.toString().contains("[--audio <muted|default>]"))
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
    fun `clients run revalidates durable live openapi cache by etag before invocation`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-run-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                        "--arg",
                        "message=hello",
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "run",
                        "player.chat",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                        "--arg",
                        "message=hello",
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
            assertEquals(2, server.runCallCount)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val response = Json.parseToJsonElement(secondOutput.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
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
    fun `generated client action alias revalidates durable live openapi cache by etag before invocation`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-alias-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                        "--message",
                        "hello",
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "chat",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                        "--message",
                        "hello",
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
            assertEquals(2, server.aliasCallCount)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val response = Json.parseToJsonElement(secondOutput.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
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
    fun `clients actions uses live openapi as action authority`() {
        val output = StringBuilder()

        StaleActionsProjectionServer().use { server ->
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
        assertEquals(listOf("player.chat"), actions.map { it.jsonObject["id"]?.jsonPrimitive?.content })
    }

    @Test
    fun `clients actions help is generated from live openapi actions`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "actions",
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
        assertTrue(help.contains("Actions for client alice"))
        assertTrue(help.contains("craftless clients alice player chat"))
        assertTrue(help.contains("--message string required"))
        assertTrue(help.contains("craftless clients alice player move"))
        assertTrue(help.contains("--forward boolean"))
        assertFalse(help.trimStart().startsWith("["))
    }

    @Test
    fun `clients actions revalidates durable live openapi cache by etag`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "actions",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "actions",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val actions = Json.parseToJsonElement(secondOutput.toString().trim()).jsonArray
        assertEquals(listOf("player.chat"), actions.map { it.jsonObject["id"]?.jsonPrimitive?.content })
    }

    @Test
    fun `clients openapi revalidates durable live openapi cache by etag`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-raw-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "openapi",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "openapi",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val openApi = Json.parseToJsonElement(secondOutput.toString().trim()).jsonObject
        val fingerprint =
            openApi["x-craftless"]
                ?.jsonObject
                ?.get("x-craftless-runtime-fingerprint")
                ?.jsonPrimitive
                ?.content
        assertEquals("3.1.0", openApi["openapi"]?.jsonPrimitive?.content)
        assertEquals("fingerprint-test", fingerprint)
    }

    @Test
    fun `clients tools exports agent tools from live openapi actions`() {
        val output = StringBuilder()

        StaleActionsProjectionServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "tools",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val manifest = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("alice", manifest["clientId"]?.jsonPrimitive?.content)
        assertEquals("fingerprint-test", manifest["runtimeFingerprint"]?.jsonPrimitive?.content)
        val tools = manifest["tools"]?.jsonArray.orEmpty()
        assertEquals(listOf("craftless_player_chat"), tools.map { it.jsonObject["name"]?.jsonPrimitive?.content })
        val tool = tools.single().jsonObject
        assertEquals("player.chat", tool["action"]?.jsonPrimitive?.content)
        assertEquals("POST /clients/alice/player:chat", tool["route"]?.jsonPrimitive?.content)
        assertEquals("available", tool["availability"]?.jsonPrimitive?.content)
        val input = requireNotNull(tool["inputSchema"]?.jsonObject)
        val message = requireNotNull(input["message"]?.jsonObject)
        assertEquals("string", message["type"]?.jsonPrimitive?.content)
        assertEquals("true", message["required"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients tools revalidates durable live openapi cache by etag`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-tools-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "tools",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "tools",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val manifest = Json.parseToJsonElement(secondOutput.toString().trim()).jsonObject
        val tools = manifest["tools"]?.jsonArray.orEmpty()
        assertEquals(listOf("craftless_player_chat"), tools.map { it.jsonObject["name"]?.jsonPrimitive?.content })
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
    fun `clients resources uses live openapi as resource authority`() {
        val output = StringBuilder()

        StaleActionsProjectionServer().use { server ->
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
        assertEquals(listOf("player"), resources.map { it.jsonObject["id"]?.jsonPrimitive?.content })
        val player = resources.single().jsonObject
        assertEquals(listOf("player.chat"), player["actions"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun `clients resources revalidates durable live openapi cache by etag`() {
        val cacheDir = Files.createTempDirectory("craftless-cli-resource-openapi-cache")
        val firstOutput = StringBuilder()
        val secondOutput = StringBuilder()

        RevalidatingOpenApiServer().use { server ->
            val firstExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "resources",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { firstOutput.appendLine(it) },
                )
            val secondExit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "resources",
                        "--api",
                        server.url,
                        "--openapi-cache",
                        cacheDir.toString(),
                    ),
                    stdout = { secondOutput.appendLine(it) },
                )

            assertEquals(0, firstExit)
            assertEquals(0, secondExit)
            assertEquals(listOf(null, "etag-v1"), server.ifNoneMatchValues)
        }

        assertEquals(firstOutput.toString(), secondOutput.toString())
        val resources = Json.parseToJsonElement(secondOutput.toString().trim()).jsonArray
        assertEquals(listOf("player"), resources.map { it.jsonObject["id"]?.jsonPrimitive?.content })
    }

    @Test
    fun `clients query fetches live projection through json rpc`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "query",
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
    fun `clients events watches live sse stream from daemon`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()
            CraftlessCli.run(
                listOf(
                    "clients",
                    "alice",
                    "run",
                    "player.chat",
                    "--api",
                    server.url,
                    "--arg",
                    "message=hello cli stream",
                ),
                stdout = {},
            )

            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "events",
                        "--api",
                        server.url,
                        "--type",
                        "player.chat",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
        }

        val body = output.toString()
        assertTrue(body.contains("event: player.chat"))
        assertTrue(body.contains("\"type\":\"player.chat\""))
        assertTrue(body.contains("hello cli stream"))
        assertTrue(!body.contains("client.created"))
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
    fun `generated client resource help is loaded from live openapi resource metadata`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        StaleActionsProjectionServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf(
                        "clients",
                        "alice",
                        "player",
                        "--help",
                        "--api",
                        server.url,
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit)
            assertFalse(server.runCalled)
            assertFalse(server.aliasCalled)
        }

        assertEquals("", errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Resource: player"))
        assertTrue(help.contains("Usage: craftless clients alice player <action>"))
        assertTrue(help.contains("Actions:"))
        assertTrue(help.contains("  chat available"))
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
        workspaceRoot: java.nio.file.Path? = null,
        cacheMetadataFetcher: CacheMetadataFetcher =
            com.minekube.craftless.daemon
                .KtorCacheMetadataFetcher(),
    ) : AutoCloseable {
        private val server =
            com.minekube.craftless.daemon.LocalSessionApiServer.inMemory(
                driverFactory = driverFactory,
                workspaceRoot = workspaceRoot,
                cacheMetadataFetcher = cacheMetadataFetcher,
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

    private class SlowCreateApiServer(
        private val delayMillis: Long,
    ) : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    post("/clients") {
                        Thread.sleep(delayMillis)
                        call.respondText(
                            """{"id":"alice","version":"1.21.6","loader":"FABRIC","state":"RUNNING","profile":{"kind":"OFFLINE","name":"Alice"}}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Created,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"

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

    private class RecordingCreateApiServer : AutoCloseable {
        val createBodies = mutableListOf<String>()
        private val port = allocateLoopbackPort()
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/openapi.json") {
                        call.respondText(
                            Json.encodeToString(OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())),
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients") {
                        createBodies += call.receiveText()
                        call.respondText(
                            """{"id":"alice","version":"1.21.6","loader":"FABRIC","state":"RUNNING","profile":{"kind":"OFFLINE","name":"Alice"}}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Created,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"

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

        override fun runtimeGraph(): RuntimeCapabilityGraph {
            val graph = delegate.runtimeGraph()
            return graph.copy(
                operations =
                    graph.operations +
                        RuntimeOperationNode(
                            id = "player.fail",
                            resource = "player",
                            adapter = "test.player-fail",
                            arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                            availability = RuntimeAvailability.available(),
                        ),
            )
        }

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

        override fun runtimeGraph(): RuntimeCapabilityGraph {
            val graph = delegate.runtimeGraph()
            return graph.copy(
                resources = graph.resources + RuntimeResourceNode("world.block", RuntimeAvailability.available()),
                operations =
                    graph.operations +
                        RuntimeOperationNode(
                            id = "world.block.break",
                            resource = "world.block",
                            adapter = "test.world-block-break",
                            arguments = mapOf("max-distance" to RuntimeSchema("number")),
                            availability = RuntimeAvailability.available(),
                        ),
            )
        }

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
                    get("/clients/alice/resources") {
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
                              "x-craftless": {
                                "x-craftless-runtime-fingerprint": "fingerprint-test"
                              },
                              "x-craftless-actions": [
                                {
                                  "id": "player.chat",
                                  "schemaVersion": "1",
                                  "args": { "message": { "type": "string", "required": true } }
                                }
                              ],
                              "x-craftless-resources": [
                                {
                                  "id": "player",
                                  "actions": ["player.chat"],
                                  "availability": "available",
                                  "availabilityReasons": [],
                                  "actionDescriptors": [
                                    {
                                      "id": "player.chat",
                                      "schemaVersion": "1",
                                      "args": { "message": { "type": "string", "required": true } }
                                    }
                                  ]
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

    private class RevalidatingOpenApiServer : AutoCloseable {
        private val port = allocateLoopbackPort()
        private val values = mutableListOf<String?>()
        private var runCalls = 0
        private var aliasCalls = 0
        val ifNoneMatchValues: List<String?>
            get() = values.toList()
        val runCallCount: Int
            get() = runCalls
        val aliasCallCount: Int
            get() = aliasCalls
        private val server =
            embeddedServer(ServerCIO, host = "127.0.0.1", port = port) {
                routing {
                    get("/clients/alice/openapi.json") {
                        val ifNoneMatch = call.request.header(HttpHeaders.IfNoneMatch)
                        values += ifNoneMatch
                        if (ifNoneMatch == "etag-v1") {
                            call.response.header(HttpHeaders.ETag, "etag-v1")
                            call.respondText("", ContentType.Application.Json, HttpStatusCode.NotModified)
                        } else {
                            call.response.header(HttpHeaders.ETag, "etag-v1")
                            call.respondText(
                                """
                                {
                                  "openapi": "3.1.0",
                                  "info": { "title": "Revalidating test API", "version": "1" },
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
                                  "x-craftless": {
                                    "x-craftless-runtime-fingerprint": "fingerprint-test"
                                  },
                                  "x-craftless-actions": [
                                    {
                                      "id": "player.chat",
                                      "schemaVersion": "1",
                                      "args": { "message": { "type": "string", "required": true } }
                                    }
                                  ],
                                  "x-craftless-resources": [
                                    {
                                      "id": "player",
                                      "actions": ["player.chat"],
                                      "availability": "available",
                                      "availabilityReasons": [],
                                      "actionDescriptors": [
                                        {
                                          "id": "player.chat",
                                          "schemaVersion": "1",
                                          "args": { "message": { "type": "string", "required": true } }
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """.trimIndent(),
                                ContentType.Application.Json,
                            )
                        }
                    }
                    post("/clients/alice:run") {
                        runCalls += 1
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"hello"}""",
                            ContentType.Application.Json,
                        )
                    }
                    post("/clients/alice/player:chat") {
                        aliasCalls += 1
                        call.respondText(
                            """{"action":"player.chat","status":"ACCEPTED","message":"hello"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        val url = "http://127.0.0.1:$port"

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
    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: cliDefaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url] ?: cliDefaultTestBinaryResponse(url)) {
            "missing test binary response for $url"
        }
}

private const val CLI_DEFAULT_TEST_FABRIC_API_VERSION = "0.129.0+1.21.6"
private const val CLI_DEFAULT_TEST_FABRIC_API_METADATA_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
private const val CLI_DEFAULT_TEST_FABRIC_API_JAR_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar"

private fun cliDefaultTestFabricApiMetadata(): String =
    """
    <metadata>
      <groupId>net.fabricmc.fabric-api</groupId>
      <artifactId>fabric-api</artifactId>
      <versioning>
        <versions>
          <version>$CLI_DEFAULT_TEST_FABRIC_API_VERSION</version>
        </versions>
      </versioning>
    </metadata>
    """.trimIndent()

private fun cliDefaultTestTextResponse(url: String): String? =
    if (url == CLI_DEFAULT_TEST_FABRIC_API_METADATA_URL) {
        cliDefaultTestFabricApiMetadata()
    } else {
        null
    }

private fun cliDefaultTestBinaryResponse(url: String): ByteArray? =
    if (url == CLI_DEFAULT_TEST_FABRIC_API_JAR_URL) {
        "fabric-api-jar".encodeToByteArray()
    } else {
        null
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
    return StaticCacheMetadataFetcher(
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
            MINECRAFT_JAVA_RUNTIME_INDEX_URL to cliJavaRuntimeIndexJson(javaRuntimeManifestUrl),
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
                javaExecutableUrl to cliFakeJavaBytes("21.0.11"),
                fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                pinnedFabricLoaderJarUrl to "pinned-fabric-loader-jar".encodeToByteArray(),
            ),
    )
}

private fun cliJavaRuntimeIndexJson(manifestUrl: String): String =
    """
    {
      "linux": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os-arm64": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "windows-x64": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      }
    }
    """.trimIndent()

private fun cliFakeJavaBytes(version: String): ByteArray =
    """
    #!/usr/bin/env sh
    echo 'openjdk version "$version" 2026-04-21 LTS' >&2
    """.trimIndent().encodeToByteArray()

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
