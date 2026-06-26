package com.minekube.craftless.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.minekube.craftless.daemon.CacheMetadataFetcher
import com.minekube.craftless.daemon.CachePreparationService
import com.minekube.craftless.daemon.KtorCacheMetadataFetcher
import com.minekube.craftless.daemon.LocalSessionApiServer
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.Profile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val exit = CraftlessCli.run(args.toList(), stdout = ::println, stderr = System.err::println)
    if (exit != 0) {
        kotlin.system.exitProcess(exit)
    }
}

object CraftlessCli {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun root(): CoreCliktCommand =
        RootCommand().subcommands(
            GroupCommand("clients").subcommands(
                LeafCommand("create"),
                LeafCommand("list"),
            ),
            GroupCommand("cache").subcommands(
                LeafCommand("prepare"),
            ),
            GroupCommand("server").subcommands(
                LeafCommand("start"),
            ),
        )

    fun registeredCommandPaths(): Set<String> =
        setOf(
            "clients create",
            "clients list",
            "clients <id> get",
            "clients <id> connect",
            "clients <id> stop",
            "clients <id> openapi",
            "clients <id> actions",
            "clients <id> resources",
            "clients <id> run <action>",
            "clients <id> <resource...> <action>",
            "cache prepare",
            "server start",
        )

    fun run(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit = {},
        afterStart: (ApiServerMetadata) -> Unit = {},
        env: Map<String, String> = System.getenv(),
        cacheMetadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
    ): Int {
        if (args.take(2) == listOf("server", "start")) {
            return runServerStart(args.drop(2), stdout, stderr, afterStart)
        }
        if (args.take(2) == listOf("cache", "prepare")) {
            return prepareCache(args.drop(2), stdout, stderr, cacheMetadataFetcher)
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
        if (args.size >= 3 && args[0] == "clients" && args[2] == "resources") {
            return getClientResources(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 4 && args[0] == "clients" && args[2] == "run") {
            return runClientAction(args.drop(1), stdout, stderr, env)
        }
        if (args.size >= 4 && args[0] == "clients") {
            return runGeneratedClientAction(args.drop(1), stdout, stderr, env)
        }
        if (args.isNotEmpty()) {
            stderr("error: unknown command ${args.joinToString(" ")}")
            return 2
        }
        root().main(args)
        return 0
    }

    private fun prepareCache(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        metadataFetcher: CacheMetadataFetcher,
    ): Int {
        val minecraftVersion = args.optionValue("--mc")
        val loader =
            args.optionValue("--loader")?.let { value ->
                runCatching { Loader.valueOf(value.uppercase()) }.getOrNull()
            }
        val loaderVersion = args.optionValue("--loader-version")
        val workspace = args.optionValue("--workspace")?.let(Path::of)
        if (minecraftVersion.isNullOrBlank() || loader == null || workspace == null) {
            stderr("error: usage is cache prepare --mc <version> --loader <loader> [--loader-version <version>] --workspace <path>")
            return 2
        }
        return runCatching {
            val result =
                kotlinx.coroutines.runBlocking {
                    CachePreparationService(workspace, metadataFetcher).prepare(
                        CachePrepareRequest(
                            minecraftVersion = minecraftVersion,
                            loader = loader,
                            loaderVersion = loaderVersion,
                        ),
                    )
                }
            stdout(json.encodeToString(result))
            0
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to prepare cache"}")
            2
        }
    }

    private fun createClient(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.firstOrNull { !it.startsWith("--") }.orEmpty()
        val version = args.optionValue("--version")
        val loader =
            args.optionValue("--loader")?.let { value ->
                runCatching { Loader.valueOf(value.uppercase()) }.getOrNull()
            }
        val profileName = args.optionValue("--offline-name")
        if (clientId.isBlank() || version.isNullOrBlank() || loader == null || profileName.isNullOrBlank()) {
            stderr("error: usage is clients create <id> --version <version> --loader <loader> --offline-name <name> [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        val request =
            CreateClientRequest(
                id = clientId,
                version = version,
                loader = loader,
                profile = Profile.offline(profileName),
            )

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val response =
                        http.post("${api.trimEnd('/')}/clients") {
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
                    val response =
                        http.post("${api.trimEnd('/')}/clients/$clientId:connect") {
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

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val openApiResponse = http.get("${api.trimEnd('/')}/clients/$clientId/openapi.json")
                    val openApiBody = openApiResponse.bodyAsText()
                    if (!openApiResponse.status.isSuccess()) {
                        stderr(openApiBody)
                        return@runBlocking 1
                    }
                    val openApi = json.decodeFromString<OpenApiDocument>(openApiBody)
                    val runPath = "/clients/$clientId:run"
                    val openApiAction = openApi.actions.firstOrNull { it.id == action }
                    if (openApi.paths[runPath]?.post == null || openApiAction == null) {
                        stderr("error: action $action is not described by live OpenAPI for client $clientId")
                        return@runBlocking 1
                    }
                    val payload =
                        ActionRunRequest(
                            action = action,
                            args = args.genericActionArguments(action, openApiAction),
                        )
                    val response =
                        http.post("${api.trimEnd('/')}/clients/$clientId:run") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(payload))
                        }
                    response.forwardActionResultBody(stdout, stderr)
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
        val clientId = args.getOrNull(0)?.takeIf { it.isNotBlank() }
        if (clientId == null) {
            stderr("error: usage is clients <id> <resource...> <action> [--api <url>] [--arg key=value] [--<arg> value]")
            return 2
        }
        val api = args.apiBaseUrl(env)

        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    val openApiResponse = http.get("${api.trimEnd('/')}/clients/$clientId/openapi.json")
                    val openApiBody = openApiResponse.bodyAsText()
                    if (!openApiResponse.status.isSuccess()) {
                        stderr(openApiBody)
                        return@runBlocking 1
                    }
                    val openApi = json.decodeFromString<OpenApiDocument>(openApiBody)
                    val alias = args.generatedActionAlias(openApi.actions.mapTo(mutableSetOf()) { it.id })
                    if (alias == null) {
                        stderr("error: usage is clients <id> <resource...> <action> [--api <url>] [--arg key=value] [--<arg> value]")
                        return@runBlocking 2
                    }
                    val aliasOperation = openApi.paths[alias.path]?.post
                    val openApiAction = openApi.actions.firstOrNull { it.id == alias.actionId }
                    if (
                        aliasOperation?.extensions?.get("x-craftless-action") != alias.actionId ||
                        openApiAction == null
                    ) {
                        stderr("error: action ${alias.actionId} is not described by live OpenAPI for client ${alias.clientId}")
                        return@runBlocking 1
                    }
                    if (args.contains("--help")) {
                        stdout(openApiAction.generatedAliasHelp(alias, "POST ${alias.path}"))
                        return@runBlocking 0
                    }
                    val response =
                        http.post("${api.trimEnd('/')}${alias.path}") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(args.actionAliasArguments(openApiAction, alias.argumentStartIndex)))
                        }
                    response.forwardActionResultBody(stdout, stderr)
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

    private fun getClientResources(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int {
        val clientId = args.getOrNull(0).orEmpty()
        if (clientId.isBlank()) {
            stderr("error: usage is clients <id> resources [--api <url>]")
            return 2
        }
        val api = args.apiBaseUrl(env)
        return runCatching {
            kotlinx.coroutines.runBlocking {
                HttpClient(CIO).use { http ->
                    http.get("${api.trimEnd('/')}/clients/$clientId/resources").forwardBody(stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "failed to fetch resources"}")
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

    private fun runServerStart(
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
        val workspaceRoot = args.optionValue("--workspace")?.let(Path::of)

        LocalSessionApiServer.inMemory(port = port, workspaceRoot = workspaceRoot).use { server ->
            server.start()
            val metadata =
                ApiServerMetadata(
                    ok = true,
                    url = server.url(""),
                    openapi = "/openapi.json",
                    events = "/events",
                    workspace = workspaceRoot?.toString(),
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
            ?: "http://127.0.0.1:8080"

    private fun List<String>.optionValues(name: String): List<String> =
        mapIndexedNotNull { index, value ->
            if (value == name && index + 1 < size) this[index + 1] else null
        }

    private fun List<String>.actionAliasArguments(
        action: OpenApiAction,
        startIndex: Int,
    ): Map<String, JsonElement> {
        val values = linkedMapOf<String, JsonElement>()
        optionValues("--arg").forEach { argument ->
            val parts = argument.split("=", limit = 2)
            require(parts.size == 2 && parts[0].isNotBlank()) { "--arg must use key=value syntax" }
            val name = parts[0]
            val descriptor =
                requireNotNull(action.arguments[name]) {
                    "action ${action.id} does not declare argument $name"
                }
            values[name] = parts[1].toJsonArgument(descriptor.type)
        }

        val positional = mutableListOf<String>()
        var index = startIndex
        while (index < size) {
            val token = this[index]
            when {
                token == "--api" || token == "--arg" -> index += 2
                token.startsWith("--") -> {
                    val name = token.removePrefix("--")
                    require(name.isNotBlank()) { "action argument flag is required" }
                    val argument =
                        requireNotNull(action.arguments[name]) {
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
            val missingRequired =
                action.arguments
                    .filterValues { it.required }
                    .keys
                    .filterNot { it in values }
            require(positional.size == 1 && missingRequired.size == 1) {
                "positional action args require exactly one missing required argument"
            }
            val name = missingRequired.single()
            values[name] = positional.single().toJsonArgument(action.arguments[name]?.type)
        }

        requireRequiredActionArguments(action, values)
        return values
    }

    private fun List<String>.genericActionArguments(
        actionId: String,
        action: OpenApiAction,
    ): Map<String, JsonElement> =
        optionValues("--arg")
            .associate { argument ->
                val parts = argument.split("=", limit = 2)
                require(parts.size == 2 && parts[0].isNotBlank()) { "--arg must use key=value syntax" }
                val name = parts[0]
                val descriptor =
                    requireNotNull(action.arguments[name]) {
                        "action $actionId does not declare argument $name"
                    }
                name to parts[1].toJsonArgument(descriptor.type)
            }.also { values ->
                requireRequiredActionArguments(action, values)
            }

    private fun requireRequiredActionArguments(
        action: OpenApiAction,
        values: Map<String, JsonElement>,
    ) {
        val missing =
            action.arguments
                .filterValues { it.required }
                .keys
                .filterNot { it in values }
        require(missing.isEmpty()) {
            "action ${action.id} requires argument ${missing.first()}"
        }
    }

    private fun OpenApiAction.generatedAliasHelp(
        alias: GeneratedActionAlias,
        route: String,
    ): String =
        buildString {
            appendLine("Action: $id")
            appendLine("Route: $route")
            appendLine("Usage: craftless clients ${alias.clientId} ${alias.segments.joinToString(" ")} [--api <url>] [args]")
            appendLine("Arguments:")
            if (arguments.isEmpty()) {
                appendLine("  none")
            } else {
                arguments.forEach { (name, argument) ->
                    val required = if (argument.required) " required" else ""
                    appendLine("  --$name ${argument.type}$required")
                }
            }
        }.trimEnd()

    private fun String.toJsonArgument(): JsonElement =
        when {
            equals("true", ignoreCase = true) -> JsonPrimitive(true)
            equals("false", ignoreCase = true) -> JsonPrimitive(false)
            toIntOrNull() != null -> JsonPrimitive(toInt())
            else -> JsonPrimitive(this)
        }

    private fun List<String>.generatedActionAlias(actionIds: Set<String>): GeneratedActionAlias? {
        val clientId = getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val commandTokens =
            drop(1)
                .takeWhile { !it.startsWith("--") }
                .filter { it.isNotBlank() }
        val segmentCount =
            commandTokens.size
                .downTo(2)
                .firstOrNull { count -> commandTokens.take(count).joinToString(".") in actionIds }
                ?: commandTokens.size
        val segments = commandTokens.take(segmentCount)
        if (segments.size < 2) {
            return null
        }
        val resourcePath = segments.dropLast(1).joinToString("/")
        val actionName = segments.last()
        return GeneratedActionAlias(
            clientId = clientId,
            segments = segments,
            actionId = segments.joinToString("."),
            path = "/clients/$clientId/$resourcePath:$actionName",
            argumentStartIndex = 1 + segments.size,
        )
    }

    private fun String.toJsonArgument(type: String?): JsonElement =
        when (type) {
            "boolean" ->
                JsonPrimitive(
                    when {
                        equals("true", ignoreCase = true) -> true
                        equals("false", ignoreCase = true) -> false
                        else -> error("boolean argument must be true or false")
                    },
                )
            "integer" -> JsonPrimitive(requireNotNull(toIntOrNull()) { "integer argument is required" })
            "number" -> JsonPrimitive(requireNotNull(toDoubleOrNull()) { "number argument is required" })
            "string" -> JsonPrimitive(this)
            else -> toJsonArgument()
        }

    private suspend fun io.ktor.client.statement.HttpResponse.forwardActionResultBody(
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
    ): Int {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            stderr(body)
            return 1
        }

        val result = json.decodeFromString<ActionInvocationResponse>(body)
        return if (result.status == "ACCEPTED") {
            stdout(body)
            0
        } else {
            stderr(body)
            1
        }
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
    val workspace: String? = null,
)

@Serializable
data class ActionRunRequest(
    val action: String,
    val args: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ActionInvocationResponse(
    val action: String,
    val status: String,
    val message: String? = null,
)

data class GeneratedActionAlias(
    val clientId: String,
    val segments: List<String>,
    val actionId: String,
    val path: String,
    val argumentStartIndex: Int,
)

@Serializable
data class ConnectClientRequest(
    val host: String,
    val port: Int,
)

private class RootCommand :
    CoreCliktCommand(
        name = "craftless",
    ) {
    override fun help(context: Context): String = "Automate real Minecraft Java clients for tests, agents, and CI."

    override fun run() = Unit
}

private class GroupCommand(
    name: String,
) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}

private class LeafCommand(
    name: String,
) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}
