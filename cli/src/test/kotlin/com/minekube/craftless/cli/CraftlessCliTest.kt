package com.minekube.craftless.cli

import com.minekube.craftless.daemon.CacheMetadataFetcher
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.testkit.FakeDriverSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
    fun `api cli does not fetch actions projection as gameplay authority`() {
        val source = Files.readString(repositoryRoot().resolve("cli/src/main/kotlin/com/minekube/craftless/cli/ApiCli.kt"))

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
    fun `cli registers api first command tree`() {
        val commands = CraftlessCli.registeredCommandPaths()

        assertTrue(commands.contains("api <endpoint>"))
        assertTrue(commands.contains("daemon start"))
        assertFalse(commands.contains("clients create"))
        assertFalse(commands.contains("clients list"))
        assertFalse(commands.contains("clients <id> get"))
        assertFalse(commands.contains("clients <id> connect"))
        assertFalse(commands.contains("clients <id> stop"))
        assertFalse(commands.contains("clients <id> openapi"))
        assertFalse(commands.contains("clients <id> actions"))
        assertFalse(commands.contains("clients <id> resources"))
        assertFalse(commands.contains("clients <id> query <target>"))
        assertFalse(commands.contains("clients <id> events"))
        assertFalse(commands.contains("clients <id> tools"))
        assertFalse(commands.contains("clients <id> run <action>"))
        assertFalse(commands.contains("clients <id> <resource...> <action>"))
        assertFalse(commands.contains("cache prepare"))
        assertFalse(commands.contains("cache export"))
        assertFalse(commands.contains("cache cleanup"))
        assertFalse(commands.contains("runtimes java list"))
        assertFalse(commands.contains("runtimes java resolve"))
        assertFalse(commands.contains("server start"))
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

    private fun fakeWindowlessWrapperEnv(
        workspace: Path,
        env: Map<String, String> = emptyMap(),
    ): Map<String, String> = env + ("CRAFTLESS_WINDOWLESS_WRAPPER" to fakeWindowlessWrapper(workspace).toString())

    private fun fakeWindowlessWrapper(workspace: Path): Path {
        val wrapper = workspace.resolve("bin/craftless-test-windowless")
        Files.createDirectories(wrapper.parent)
        Files.writeString(
            wrapper,
            """
            #!/usr/bin/env sh
            exec "${'$'}@"
            """.trimIndent(),
        )
        wrapper.toFile().setExecutable(true, true)
        return wrapper
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
        assertTrue(help.contains("api <endpoint>"))
        assertTrue(help.contains("daemon start"))
        assertFalse(help.contains("server start"))
        assertFalse(help.contains("clients <id> <resource...> <action>"))
        assertFalse(help.contains("player chat"))
        assertFalse(help.contains("world block break"))
    }

    @Test
    fun `api help prints generic route command guidance`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("api", "--help"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(0, exit)
        assertEquals("", errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Usage: craftless api <endpoint> [flags]"))
        assertTrue(help.contains("-X, --method <method>"))
        assertTrue(help.contains("-F, --field key=value"))
        assertTrue(help.contains("OpenAPI"))
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
    fun `removed clients create command returns explicit usage error`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("clients", "create", "bot", "--version", "latest-release", "--loader", "FABRIC"),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(2, exit)
        assertEquals("", output.toString())
        assertTrue(errors.toString().contains("unknown command clients create bot"))
    }

    @Test
    fun `api gets supervisor route by endpoint`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            val exit =
                CraftlessCli.run(
                    listOf("api", "/version", "--api", server.url),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val version = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("fake", version["minecraft"]?.jsonPrimitive?.content)
    }

    @Test
    fun `api posts typed fields to supervisor route`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "api",
                        "/clients",
                        "--api",
                        server.url,
                        "-F",
                        "id=bot",
                        "-F",
                        "version=latest-release",
                        "-F",
                        "loader=FABRIC",
                        "-F",
                        "profile[kind]=OFFLINE",
                        "-F",
                        "profile[name]=Bot",
                        "-F",
                        "presentation[window]=NONE",
                        "-F",
                        "presentation[audio]=MUTED",
                    ),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
            assertEquals("bot", request["id"]?.jsonPrimitive?.content)
            assertEquals("latest-release", request["version"]?.jsonPrimitive?.content)
            assertEquals("FABRIC", request["loader"]?.jsonPrimitive?.content)
            assertEquals(
                "Bot",
                request["profile"]
                    ?.jsonObject
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                "NONE",
                request["presentation"]
                    ?.jsonObject
                    ?.get("window")
                    ?.jsonPrimitive
                    ?.content,
            )
        }
    }

    @Test
    fun `api help is inferred from supervisor openapi schema`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf("api", "/clients", "--method", "POST", "--help", "--api", server.url),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val help = output.toString()
            assertTrue(help.contains("Route: POST /clients"))
            assertTrue(help.contains("launches a new daemon-managed real Minecraft Java client process"))
            assertTrue(help.contains("id string required"))
            assertTrue(help.contains("presentation.audio string required default=MUTED enum=MUTED|DEFAULT"))
            assertTrue(help.contains("presentation.window string required default=NONE enum=NONE|VISIBLE"))
        }
    }

    @Test
    fun `api help shows every matching method when route is ambiguous`() {
        RecordingCreateApiServer().use { server ->
            val output = StringBuilder()
            val errors = StringBuilder()

            val exit =
                CraftlessCli.run(
                    listOf("api", "/clients", "--help", "--api", server.url),
                    stdout = { output.appendLine(it) },
                    stderr = { errors.appendLine(it) },
                )

            assertEquals(0, exit, errors.toString())
            val help = output.toString()
            assertTrue(help.contains("Route: GET /clients"))
            assertTrue(help.contains("Route: POST /clients"))
            assertTrue(help.contains("Lists daemon-managed client processes"))
            assertTrue(help.contains("launches a new daemon-managed real Minecraft Java client process"))
            assertTrue(help.contains("Usage: craftless api /clients --method GET [flags]"))
            assertTrue(help.contains("Usage: craftless api /clients --method POST [flags]"))
            assertTrue(help.contains("id string required"))
            assertTrue(help.contains("loader string required enum=FABRIC|VANILLA|NEOFORGE|FORGE|QUILT"))
        }
    }

    @Test
    fun `api posts generic run body with nested action args`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "api",
                        "/clients/alice:run",
                        "--api",
                        server.url,
                        "-F",
                        "action=player.chat",
                        "-F",
                        "args[message]=hello api",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `api posts per client generated route from live openapi`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit =
                CraftlessCli.run(
                    listOf(
                        "api",
                        "/clients/alice/player:chat",
                        "--api",
                        server.url,
                        "-F",
                        "message=hello generated api",
                    ),
                    stdout = { output.appendLine(it) },
                )

            assertEquals(0, exit)
        }

        val response = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals("player.chat", response["action"]?.jsonPrimitive?.content)
        assertEquals("ACCEPTED", response["status"]?.jsonPrimitive?.content)
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
    fun `daemon start uses environment workspace when workspace flag is omitted`() {
        val output = StringBuilder()
        val workspace = Files.createTempDirectory("craftless-cli-default-workspace")
        var reportedWorkspace: String? = null

        val exit =
            CraftlessCli.run(
                listOf("daemon", "start", "--once"),
                stdout = { output.appendLine(it) },
                env = mapOf("CRAFTLESS_WORKSPACE" to workspace.toString()),
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
                    fakeWindowlessWrapperEnv(
                        workspace,
                        mapOf(
                            "CRAFTLESS_FABRIC_DRIVER_MOD" to driverMod.toString(),
                        ),
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
                env = fakeWindowlessWrapperEnv(workspace),
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
                env = fakeWindowlessWrapperEnv(workspace),
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
                env = fakeWindowlessWrapperEnv(workspace),
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
                env = fakeWindowlessWrapperEnv(workspace),
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
        assertTrue(createBody.contains("UNSUPPORTED_RUNTIME_TARGET"), createBody)
        assertTrue(createBody.contains("NO_COMPATIBLE_DRIVER_MOD"), createBody)
        assertTrue(!Files.exists(workspace.resolve("cache/mods/craftless")))
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
