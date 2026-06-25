package com.minekube.craftwright.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McwCliTest {
    @Test
    fun `cli registers first jvm command tree`() {
        val commands = McwCli.registeredCommandPaths()

        assertTrue(commands.contains("versions"))
        assertTrue(commands.contains("profiles"))
        assertTrue(commands.contains("clients create"))
        assertTrue(commands.contains("clients list"))
        assertTrue(commands.contains("clients <id> get"))
        assertTrue(commands.contains("clients <id> connect"))
        assertTrue(commands.contains("clients <id> stop"))
        assertTrue(commands.contains("clients api"))
        assertTrue(commands.contains("clients <id> openapi"))
        assertTrue(commands.contains("clients <id> actions"))
        assertTrue(commands.contains("clients <id> run <action>"))
        assertTrue(commands.contains("clients <id> <namespace> <action>"))
        assertTrue(commands.contains("server start"))
        assertTrue(commands.contains("test run"))
    }

    @Test
    fun `clients api once prints server metadata and keeps server reachable during callback`() {
        val output = StringBuilder()
        var versionStatus = 0

        val exit = McwCli.run(
            listOf("clients", "api", "--once"),
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
    fun `clients create posts an offline client request to daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            val exit = McwCli.run(
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

            val exit = McwCli.run(
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

            val exit = McwCli.run(
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
        assertEquals("alice", Json.parseToJsonElement(lines[0]).jsonObject["id"]?.jsonPrimitive?.content)
        assertEquals("bob", Json.parseToJsonElement(lines[1]).jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `clients get fetches one client from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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

            val exit = McwCli.run(
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
    fun `clients list keeps craftwright api environment as fallback`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
                listOf("clients", "list"),
                stdout = { output.appendLine(it) },
                env = mapOf("CRAFTWRIGHT" to server.url),
            )

            assertEquals(0, exit)
        }

        val clients = Json.parseToJsonElement(output.toString().trim()).jsonArray
        assertTrue(clients.any { it.jsonObject["id"]?.jsonPrimitive?.content == "alice" })
    }

    @Test
    fun `explicit api option wins over environment api variables`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
                listOf("clients", "list", "--api", server.url),
                stdout = { output.appendLine(it) },
                env = mapOf(
                    "CRAFTLESS" to "http://127.0.0.1:1",
                    "CRAFTWRIGHT" to "http://127.0.0.1:2",
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

            val exit = McwCli.run(
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

            val exit = McwCli.run(
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

            val exit = McwCli.run(
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
    fun `clients actions fetches discovered actions from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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
    fun `clients openapi fetches live per client spec from daemon`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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
        val extensions = document["x-craftwright"]?.jsonObject
        assertEquals("alice", extensions?.get("x-craftwright-client-id")?.jsonPrimitive?.content)
        assertTrue(document["paths"]?.jsonObject?.containsKey("/clients/alice:run") == true)
        assertTrue(document["x-craftwright-actions"]?.jsonArray?.any {
            it.jsonObject["id"]?.jsonPrimitive?.content == "player.chat"
        } == true)
    }

    @Test
    fun `clients run preserves boolean and integer action args`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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
    fun `generated client action alias dispatches from runtime action metadata`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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

            val exit = McwCli.run(
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
    fun `generated client action alias preserves typed args from action schema`() {
        val output = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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
    fun `generated client action alias rejects unavailable runtime action`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            server.createAlice()

            val exit = McwCli.run(
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
        assertTrue(errors.toString().contains("action player.fly is not available for client alice"))
    }

    @Test
    fun `clients run returns nonzero for daemon errors`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        LocalTestApiServer().use { server ->
            val exit = McwCli.run(
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
        assertTrue(errors.toString().contains("BAD_REQUEST"))
    }

    private class LocalTestApiServer : AutoCloseable {
        private val server = com.minekube.craftwright.daemon.LocalSessionApiServer.inMemory()
        val url: String

        init {
            server.start()
            url = server.url("")
        }

        fun createAlice() {
            createOfflineClient("alice", "Alice")
        }

        fun createOfflineClient(id: String, name: String) {
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
                            """.trimIndent()
                        )
                    }
                }
            }
        }

        override fun close() {
            server.close()
        }
    }
}
