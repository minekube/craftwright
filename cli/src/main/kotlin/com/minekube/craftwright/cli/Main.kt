package com.minekube.craftwright.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.minekube.craftwright.daemon.LocalSessionApiServer
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.Loader
import com.minekube.craftwright.protocol.OpenApiAction
import com.minekube.craftwright.protocol.Profile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val exit = McwCli.run(args.toList(), stdout = ::println, stderr = System.err::println)
    if (exit != 0) {
        kotlin.system.exitProcess(exit)
    }
}

object McwCli {
    private val json = Json { encodeDefaults = true }

    fun root(): CoreCliktCommand = RootCommand().subcommands(
        LeafCommand("versions"),
        LeafCommand("profiles"),
        GroupCommand("clients").subcommands(
            LeafCommand("create"),
            LeafCommand("list"),
            LeafCommand("api"),
        ),
        GroupCommand("server").subcommands(
            LeafCommand("start"),
        ),
        GroupCommand("test").subcommands(
            LeafCommand("run"),
        ),
    )

    fun registeredCommandPaths(): Set<String> = setOf(
        "versions",
        "profiles",
        "clients create",
        "clients list",
        "clients <id> get",
        "clients <id> connect",
        "clients <id> stop",
        "clients api",
        "clients <id> openapi",
        "clients <id> actions",
        "clients <id> run <action>",
        "clients <id> <namespace> <action>",
        "server start",
        "test run",
    )

    fun run(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit = {},
        afterStart: (ApiServerMetadata) -> Unit = {},
        env: Map<String, String> = System.getenv(),
    ): Int {
        if (args.take(2) == listOf("clients", "api")) {
            return runClientsApi(args.drop(2), stdout, stderr, afterStart)
        }
        if (args.take(2) == listOf("clients", "create")) {
            return createClient(args.drop(2), stdout, stderr, env)
        }
        if (args.take(2) == listOf("clients", "list")) {
            return listClients(args.drop(2), stdout, stderr, env)
        }
        if (args.size >= 3 && args[0] == "clients" && args[2] == "get") {
            return getClient(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 3 && args[0] == "clients" && args[2] == "connect") {
            return connectClient(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 3 && args[0] == "clients" && args[2] == "stop") {
            return stopClient(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 3 && args[0] == "clients" && args[2] == "openapi") {
            return getClientOpenApi(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 3 && args[0] == "clients" && args[2] == "actions") {
            return getClientActions(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 4 && args[0] == "clients" && args[2] == "run") {
            return runClientAction(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 4 && args[0] == "clients") {
            return runGeneratedClientAction(args.drop(1), stdout, stderr, env)
        }
        root().main(args)
        return 0
    }

    private fun createClient(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.firstOrNull { !it.startsWith("--") }.orEmpty()
        val version = args.optionValue("--version")
        val loader = args.optionValue("--loader")?.let { value ->
            runCatching { Loader.valueOf(value.uppercase()) }.getOrNull()
        }
        val profileName = args.optionValue("--offline-name")
        if (clientId.isBlank() || version.isNullOrBlank() || loader == null || profileName.isNullOrBlank()) {
            stderr("error: usage is clients create <id> --version <version> --loader <loader> --offline-name <name> [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        val request = CreateClientRequest(
            id = clientId,
            version = version,
            loader = loader,
            profile = Profile.offline(profileName),
        )

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val response = http.post("${api.trimEnd('/')}/clients") {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(request))
                    }
                    response.forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to create client"}")
            2
        }
    }

    private fun listClients(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val response = http.get("${api.trimEnd('/')}/clients")
                    if (args.contains("--jsonl")) {
                        response.forwardJsonLines(stdout, stderr)
                    } else {
                        response.forwardBody(stdout, stderr)
                    }
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to list clients"}")
            2
        }
    }

    private fun connectClient(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        val host = args.optionValue("--host")
        val port = args.optionValue("--port")?.toIntOrNull()
        if (clientId.isBlank() || host.isNullOrBlank() || port == null) {
            stderr("error: usage is clients <id> connect --host <host> --port <port> [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        val request = ConnectClientRequest(host = host, port = port)

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val response = http.post("${api.trimEnd('/')}/clients/$clientId:connect") {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(request))
                    }
                    response.forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to connect client"}")
            2
        }
    }

    private fun getClient(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        if (clientId.isBlank()) {
            stderr("error: usage is clients <id> get [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.get("${api.trimEnd('/')}/clients/$clientId").forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to fetch client"}")
            2
        }
    }

    private fun stopClient(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        if (clientId.isBlank()) {
            stderr("error: usage is clients <id> stop [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.post("${api.trimEnd('/')}/clients/$clientId:stop").forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to stop client"}")
            2
        }
    }

    private fun runClientAction(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        val action = args.getOrNull(2).orEmpty()
        if (clientId.isBlank() || action.isBlank()) {
            stderr("error: usage is clients <id> run <action> [--api <url>] [--arg key=value]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        val payload = ActionRunRequest(
            action = action,
            args = args.optionValues("--arg").associate { argument ->
                val parts = argument.split("=", limit = 2)
                require(parts.size == 2 && parts[0].isNotBlank()) { "--arg must use key=value syntax" }
                parts[0] to parts[1].toJsonArgument()
            },
        )

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val response = http.post("${api.trimEnd('/')}/clients/$clientId:run") {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(payload))
                    }
                    response.forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to run action"}")
            2
        }
    }

    private fun runGeneratedClientAction(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        val namespace = args.getOrNull(1).orEmpty()
        val actionName = args.getOrNull(2).orEmpty()
        if (clientId.isBlank() || namespace.isBlank() || actionName.isBlank()) {
            stderr("error: usage is clients <id> <namespace> <action> [--api <url>] [--arg key=value] [--<arg> value]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        val actionId = "$namespace.$actionName"

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val actionsResponse = http.get("${api.trimEnd('/')}/clients/$clientId/actions")
                    val actionsBody = actionsResponse.bodyAsText()
                    if (!actionsResponse.status.isSuccess()) {
                        stderr(actionsBody)
                        return@runBlocking 1
                    }
                    val action = json.decodeFromString<List<OpenApiAction>>(actionsBody)
                        .firstOrNull { it.id == actionId }
                    if (action == null) {
                        stderr("error: action $actionId is not available for client $clientId")
                        return@runBlocking 1
                    }
                    val response = http.post("${api.trimEnd('/')}/clients/$clientId/$namespace:$actionName") {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(args.actionAliasArguments(action)))
                    }
                    response.forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to run generated action"}")
            2
        }
    }

    private fun getClientActions(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        if (clientId.isBlank()) {
            stderr("error: usage is clients <id> actions [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.get("${api.trimEnd('/')}/clients/$clientId/actions").forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to fetch actions"}")
            2
        }
    }

    private fun getClientOpenApi(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        if (clientId.isBlank()) {
            stderr("error: usage is clients <id> openapi [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.get("${api.trimEnd('/')}/clients/$clientId/openapi.json").forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to fetch openapi"}")
            2
        }
    }

    private fun runClientsApi(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        afterStart: (ApiServerMetadata) -> Unit,
    ): Int {
        val once = args.contains("--once")
        val port = args.optionValue("--port")?.toIntOrNull() ?: 0
        if (args.optionValue("--port") != null && port < 0) {
            stderr("error: --port must be a non-negative integer")
            return 2
        }

        LocalSessionApiServer.inMemory(port = port).use { server ->
            server.start()
            val metadata = ApiServerMetadata(
                ok = true,
                url = server.url(""),
                openapi = "/openapi.json",
                events = "/events",
            )
            stdout(json.encodeToString(metadata))
            afterStart(metadata)
            if (!once) {
                CountDownLatch(1).await()
            }
        }
        return 0
    }

    private fun List<String>.optionValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index + 1 < size) this[index + 1] else null
    }

    private fun List<String>.apiBaseUrl(env: Map<String, String>): String =
        optionValue("--api")
            ?: env["CRAFTLESS"]
            ?: env["CRAFTWRIGHT"]
            ?: "http://127.0.0.1:8080"

    private fun List<String>.optionValues(name: String): List<String> =
        mapIndexedNotNull { index, value ->
            if (value == name && index + 1 < size) this[index + 1] else null
        }

    private fun List<String>.actionAliasArguments(action: OpenApiAction): Map<String, JsonElement> {
        val values = linkedMapOf<String, JsonElement>()
        optionValues("--arg").forEach { argument ->
            val parts = argument.split("=", limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank()) { "--arg must use key=value syntax" }
            values[parts[0]] = parts[1].toJsonArgument(action.arguments[parts[0]]?.type)
        }

        val positional = mutableListOf<String>()
        var index = 3
        while (index < size) {
            val token = this[index]
            when {
                token == "--api" || token == "--arg" -> index += 2
                token.startsWith("--") -> {
                    val name = token.removePrefix("--")
                    require(name.isNotBlank()) { "action argument flag is required" }
                    val argument = requireNotNull(action.arguments[name]) {
                        "action ${action.id} does not declare argument $name"
                    }
                    val next = getOrNull(index + 1)
                    if (argument.type == "boolean" && (next == null || next.startsWith("--"))) {
                        values[name] = JsonPrimitive(true)
                        index += 1
                    } else {
                        require(next != null && !next.startsWith("--")) { "--$name requires a value" }
                        values[name] = next.toJsonArgument(argument.type)
                        index += 2
                    }
                }
                else -> {
                    positional += token
                    index += 1
                }
            }
        }

        if (positional.isNotEmpty()) {
            val missingRequired = action.arguments
                .filterValues { it.required }
                .keys
                .filterNot { it in values }
            require(positional.size == 1 && missingRequired.size == 1) {
                "positional action args require exactly one missing required argument"
            }
            val name = missingRequired.single()
            values[name] = positional.single().toJsonArgument(action.arguments[name]?.type)
        }

        return values
    }

    private fun String.toJsonArgument(): JsonElement =
        when {
            equals("true", ignoreCase = true) -> JsonPrimitive(true)
            equals("false", ignoreCase = true) -> JsonPrimitive(false)
            toIntOrNull() != null -> JsonPrimitive(toInt())
            else -> JsonPrimitive(this)
        }

    private fun String.toJsonArgument(type: String?): JsonElement =
        when (type) {
            "boolean" -> JsonPrimitive(
                when {
                    equals("true", ignoreCase = true) -> true
                    equals("false", ignoreCase = true) -> false
                    else -> error("boolean argument must be true or false")
                }
            )
            "integer" -> JsonPrimitive(requireNotNull(toIntOrNull()) { "integer argument is required" })
            "number" -> JsonPrimitive(requireNotNull(toDoubleOrNull()) { "number argument is required" })
            "string" -> JsonPrimitive(this)
            else -> toJsonArgument()
        }

    private suspend fun io.ktor.client.statement.HttpResponse.forwardBody(
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
    ): Int {
        val body = bodyAsText()
        return if (status.isSuccess()) {
            stdout(body)
            0
        } else {
            stderr(body)
            1
        }
    }

    private suspend fun io.ktor.client.statement.HttpResponse.forwardJsonLines(
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
    ): Int {
        val body = bodyAsText()
        return if (status.isSuccess()) {
            Json.parseToJsonElement(body).jsonArray.forEach { element ->
                stdout(json.encodeToString(element))
            }
            0
        } else {
            stderr(body)
            1
        }
    }
}

@Serializable
data class ApiServerMetadata(
    val ok: Boolean,
    val url: String,
    val openapi: String,
    val events: String,
)

@Serializable
data class ActionRunRequest(
    val action: String,
    val args: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ConnectClientRequest(
    val host: String,
    val port: Int,
)

private class RootCommand : CoreCliktCommand(
    name = "mcw",
) {
    override fun help(context: Context): String =
        "Automate real Minecraft Java clients for tests, agents, and CI."

    override fun run() = Unit
}

private class GroupCommand(name: String) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}

private class LeafCommand(name: String) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}
