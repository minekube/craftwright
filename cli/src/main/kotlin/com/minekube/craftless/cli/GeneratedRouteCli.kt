package com.minekube.craftless.cli

import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.OpenApiCliBinding
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiOperation
import com.minekube.craftless.protocol.OpenApiRequestBody
import com.minekube.craftless.protocol.OpenApiSchema
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put

internal class GeneratedRouteCli(
    private val json: Json,
    private val httpClientFactory: (Map<String, String>) -> HttpClient,
) {
    fun run(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        env: Map<String, String>,
    ): Int? {
        if (args.isEmpty()) {
            return null
        }
        val api = args.apiBaseUrl(env)
        val openApi = loadOpenApi(api, env)
        val match = openApi.generatedOperations().firstNotNullOfOrNull { operation -> operation.match(args) } ?: return null
        if (args.contains("--help")) {
            stdout(match.help())
            return 0
        }
        return runCatching {
            kotlinx.coroutines.runBlocking {
                httpClientFactory(env).use { http ->
                    val path = match.resolvedPath()
                    val response =
                        if (match.operation.method == "GET") {
                            http.get("${api.trimEnd('/')}$path")
                        } else {
                            http.post("${api.trimEnd('/')}$path") {
                                val body = match.boundBody(args)
                                if (body != null) {
                                    contentType(ContentType.Application.Json)
                                    setBody(json.encodeToString(body))
                                }
                            }
                        }
                    response.forwardRouteBody(args, stdout, stderr)
                }
            }
        }.getOrElse { error ->
            stderr("error: ${error.message ?: "generated route failed"}")
            2
        }
    }

    private fun loadOpenApi(
        api: String,
        env: Map<String, String>,
    ): OpenApiDocument =
        runCatching {
            kotlinx.coroutines.runBlocking {
                httpClientFactory(env).use { http ->
                    val response = http.get("${api.trimEnd('/')}/openapi.json")
                    val body = response.bodyAsText()
                    if (!response.status.isSuccess()) {
                        error(body)
                    }
                    json.decodeFromString<OpenApiDocument>(body)
                }
            }
        }.getOrElse {
            OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
        }

    private fun OpenApiDocument.generatedOperations(): List<GeneratedOperation> =
        paths
            .flatMap { (path, operations) ->
                listOfNotNull(
                    operations.get?.let { operation -> GeneratedOperation("GET", path, operation) },
                    operations.post?.let { operation -> GeneratedOperation("POST", path, operation) },
                )
            }.filter { operation ->
                val cli = operation.operation.cli
                cli?.hidden != true && cli?.command?.isNotEmpty() == true
            }

    private fun GeneratedOperation.match(args: List<String>): GeneratedRouteMatch? {
        val cli = operation.cli ?: return null
        val patterns = listOf(cli.command) + cli.aliases
        return patterns.firstNotNullOfOrNull { pattern ->
            matchPattern(pattern, args)?.let { match ->
                GeneratedRouteMatch(
                    operation = this,
                    command = pattern,
                    captures = match.captures,
                )
            }
        }
    }

    private fun GeneratedOperation.matchPattern(
        pattern: List<String>,
        args: List<String>,
    ): PatternMatch? {
        val help = args.contains("--help")
        var index = 0
        val captures = linkedMapOf<String, String>()
        pattern.forEach { expected ->
            val actual = args.getOrNull(index)
            if (actual == null || actual.startsWith("--")) {
                if (help && expected.isPlaceholder()) {
                    return@forEach
                }
                return null
            }
            if (expected.isPlaceholder()) {
                captures[expected.placeholderName()] = actual
            } else if (expected != actual) {
                return null
            }
            index += 1
        }
        val trailing = args.drop(index).filterNot { it == "--help" }
        if (trailing.hasUnexpectedPositional()) {
            return null
        }
        return PatternMatch(captures)
    }

    private fun GeneratedRouteMatch.resolvedPath(): String =
        captures.entries.fold(operation.path) { current, (name, value) ->
            current.replace("{$name}", value.encodeURLPathPart())
        }

    private fun GeneratedRouteMatch.boundBody(args: List<String>): JsonObject? {
        val cli = operation.operation.cli
        val bindings = cli?.body?.bindings.orEmpty()
        if (bindings.isEmpty()) {
            return null
        }
        val schema = operation.operation.requestBody?.jsonSchema()
        val root = linkedMapOf<String, Any?>()
        bindings.forEach { binding ->
            val fieldSchema = schema?.schemaAt(binding.pointer)
            val value = binding.boundValue(args, captures, fieldSchema)
            if (value == null) {
                if (binding.required) {
                    val name = binding.flag ?: binding.argument?.let { "<$it>" } ?: binding.pointer
                    error("$name is required\n${help()}")
                }
                return@forEach
            }
            root.putPointer(binding.pointer, value)
        }
        return root.toJsonObject()
    }

    private fun OpenApiCliBinding.boundValue(
        args: List<String>,
        captures: Map<String, String>,
        schema: OpenApiSchema?,
    ): Any? {
        val option = flag
        val raw =
            when {
                argument != null -> captures[argument]
                option != null && fixed != null && args.contains(option) -> fixed
                option != null && fixed == null -> args.optionValue(option)
                option == null && fixed != null -> fixed
                else -> null
            } ?: schema?.default?.takeIf { option != null || fixed != null }
        return raw?.toRouteValue(type, schema, flag)
    }

    private fun String.toRouteValue(
        type: String,
        schema: OpenApiSchema?,
        label: String?,
    ): Any =
        when (type) {
            "boolean" ->
                when {
                    equals("true", ignoreCase = true) -> true
                    equals("false", ignoreCase = true) -> false
                    else -> error("boolean value is required")
                }
            "integer" -> toIntOrNull() ?: error("integer value is required")
            "number" -> toDoubleOrNull() ?: error("number value is required")
            else -> schema?.enumValues?.matchEnum(this, label) ?: this
        }

    private fun List<String>.matchEnum(
        value: String,
        label: String?,
    ): String =
        firstOrNull { candidate ->
            candidate.equals(value, ignoreCase = true) ||
                candidate.equals(value.replace('-', '_'), ignoreCase = true)
        } ?: if (label == "--audio") {
            error("--audio must be ${joinToString(" or ") { it.lowercase() }}")
        } else {
            error("${label ?: "value"} must be one of ${joinToString(", ")}")
        }

    private fun OpenApiRequestBody.jsonSchema(): OpenApiSchema? = content["application/json"]?.schema

    private fun OpenApiSchema.schemaAt(pointer: String): OpenApiSchema? {
        if (pointer.isBlank() || pointer == "/") {
            return this
        }
        return pointer.trim('/').split('/').fold(this as OpenApiSchema?) { current, part ->
            current?.properties?.get(part)
        }
    }

    private fun GeneratedRouteMatch.help(): String =
        buildString {
            appendLine("Route: ${operation.method} ${operation.path}")
            operation.operation.summary?.let { appendLine(it) }
            operation.operation.description?.let { appendLine(it) }
            val cli = operation.operation.cli
            val bindings = cli?.body?.bindings.orEmpty()
            val schema = operation.operation.requestBody?.jsonSchema()
            appendLine("Usage: craftless ${usage(bindings, schema)}")
            if (bindings.any { it.flag != null }) {
                appendLine("Options:")
                val seen = mutableSetOf<String>()
                bindings
                    .filter { it.flag != null }
                    .forEach { binding ->
                        val flag = requireNotNull(binding.flag)
                        if (!seen.add(flag) || binding.fixed != null && bindings.any { it.flag == flag && it.fixed == null }) {
                            return@forEach
                        }
                        val fieldSchema = schema?.schemaAt(binding.pointer)
                        appendLine("  $flag ${binding.optionSummary(fieldSchema)}".trimEnd())
                    }
            }
        }.trimEnd()

    private fun GeneratedRouteMatch.usage(
        bindings: List<OpenApiCliBinding>,
        schema: OpenApiSchema?,
    ): String =
        buildList {
            addAll(command.map { token -> token.helpToken() })
            val seen = mutableSetOf<String>()
            bindings
                .filter { it.flag != null }
                .forEach { binding ->
                    val flag = requireNotNull(binding.flag)
                    if (!seen.add(flag) || binding.fixed != null && bindings.any { it.flag == flag && it.fixed == null }) {
                        return@forEach
                    }
                    add(binding.usageToken(schema?.schemaAt(binding.pointer)))
                }
        }.joinToString(" ")

    private fun OpenApiCliBinding.usageToken(schema: OpenApiSchema?): String {
        val flag = requireNotNull(flag)
        if (fixed != null) {
            return "[$flag]"
        }
        val enumValues = schema?.enumValues
        val value =
            when {
                enumValues != null -> "<${enumValues.joinToString("|") { it.lowercase() }}>"
                pointer.endsWith("/loaderVersion") -> "<version>"
                else -> "<$type>"
            }
        return if (required) "$flag $value" else "[$flag $value]"
    }

    private fun OpenApiCliBinding.optionSummary(schema: OpenApiSchema?): String =
        buildList {
            if (fixed == null) {
                add(type)
            }
            if (required) {
                add("required")
            }
            schema?.default?.let { default -> add("default=$default") }
            schema?.enumValues?.let { values -> add("enum=${values.joinToString("|")}") }
        }.joinToString(" ")

    private suspend fun io.ktor.client.statement.HttpResponse.forwardRouteBody(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
    ): Int {
        val body = bodyAsText()
        return if (status.isSuccess()) {
            if (args.contains("--jsonl")) {
                Json.parseToJsonElement(body).jsonArray.forEach { element ->
                    stdout(json.encodeToString(element))
                }
            } else {
                stdout(body)
            }
            0
        } else {
            stderr(body)
            1
        }
    }

    private fun MutableMap<String, Any?>.putPointer(
        pointer: String,
        value: Any?,
    ) {
        val parts = pointer.trim('/').split('/').filter { it.isNotBlank() }
        require(parts.isNotEmpty()) { "JSON pointer must name a field" }
        var current = this
        parts.dropLast(1).forEach { part ->
            @Suppress("UNCHECKED_CAST")
            current = current.getOrPut(part) { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
        }
        current[parts.last()] = value
    }

    private fun Map<String, Any?>.toJsonObject(): JsonObject =
        buildJsonObject {
            forEach { (key, value) -> put(key, value.toJsonElement()) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> JsonPrimitive(null)
            is Boolean -> JsonPrimitive(this)
            is Int -> JsonPrimitive(this)
            is Double -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            is Map<*, *> -> (this as Map<String, Any?>).toJsonObject()
            else -> JsonPrimitive(toString())
        }

    private fun List<String>.optionValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index + 1 < size) this[index + 1] else null
    }

    private fun List<String>.apiBaseUrl(env: Map<String, String>): String =
        optionValue("--api")
            ?: env["CRAFTLESS"]
            ?: "http://127.0.0.1:8080"

    private fun List<String>.hasUnexpectedPositional(): Boolean {
        var index = 0
        while (index < size) {
            val token = this[index]
            when {
                token == "--jsonl" -> index += 1
                token == "--api" || token == "--openapi-cache" || token == "--workspace" -> index += 2
                token.startsWith("--") -> {
                    val next = getOrNull(index + 1)
                    index += if (next != null && !next.startsWith("--")) 2 else 1
                }
                else -> return true
            }
        }
        return false
    }

    private fun String.isPlaceholder(): Boolean = startsWith("{") && endsWith("}")

    private fun String.placeholderName(): String = removePrefix("{").removeSuffix("}")

    private fun String.helpToken(): String = if (isPlaceholder()) "<${placeholderName()}>" else this
}

private data class GeneratedOperation(
    val method: String,
    val path: String,
    val operation: OpenApiOperation,
)

private data class PatternMatch(
    val captures: Map<String, String>,
)

private data class GeneratedRouteMatch(
    val operation: GeneratedOperation,
    val command: List<String>,
    val captures: Map<String, String>,
)
