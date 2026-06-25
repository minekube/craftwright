package com.minekube.craftless.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenApiDocument(
    val openapi: String = "3.1.0",
    val info: OpenApiInfo = OpenApiInfo(),
    val paths: Map<String, OpenApiPath>,
    @SerialName("x-craftless")
    val extensions: Map<String, String> = emptyMap(),
    @SerialName("x-craftless-actions")
    val actions: List<OpenApiAction> = emptyList(),
) {
    companion object {
        fun from(
            catalog: ApiRouteCatalog,
            extensions: Map<String, String> = emptyMap(),
            actions: List<OpenApiAction> = emptyList(),
        ): OpenApiDocument {
            val duplicateAction =
                actions
                    .groupBy { it.id }
                    .entries
                    .firstOrNull { (_, matches) -> matches.size > 1 }
            if (duplicateAction != null) {
                throw IllegalArgumentException("duplicate action id ${duplicateAction.key}")
            }
            val actionsById = actions.associateBy { it.id }
            return OpenApiDocument(
                paths =
                    catalog.routes.groupBy { it.path }.mapValues { (_, routes) ->
                        OpenApiPath(
                            get = routes.firstOrNull { it.method == "GET" }?.toOperation(actionsById),
                            post = routes.firstOrNull { it.method == "POST" }?.toOperation(actionsById),
                        )
                    },
                extensions = extensions,
                actions = actions,
            )
        }
    }
}

@Serializable
data class OpenApiInfo(
    val title: String = "Craftless Client Session API",
    val version: String = "0.1.0",
)

@Serializable
data class OpenApiPath(
    val get: OpenApiOperation? = null,
    val post: OpenApiOperation? = null,
)

@Serializable
data class OpenApiOperation(
    val operationId: String,
    val tags: List<String>,
    val responses: Map<String, OpenApiResponse>,
    val requestBody: OpenApiRequestBody? = null,
    @SerialName("x-craftless")
    val extensions: Map<String, String>,
)

@Serializable
data class OpenApiResponse(
    val description: String = "OK",
    val content: Map<String, OpenApiMediaType> = emptyMap(),
)

@Serializable
data class OpenApiRequestBody(
    val required: Boolean = true,
    val content: Map<String, OpenApiMediaType>,
)

@Serializable
data class OpenApiMediaType(
    val schema: OpenApiSchema,
)

@Serializable
data class OpenApiSchema(
    val type: String,
    val properties: Map<String, OpenApiSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean? = null,
    val items: OpenApiSchema? = null,
)

@Serializable
data class OpenApiAction(
    val id: String,
    val schemaVersion: String,
    @SerialName("args")
    val arguments: Map<String, OpenApiActionArgument> = emptyMap(),
    val result: OpenApiActionResult = OpenApiActionResult(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid action id $id" }
        require(schemaVersion.isNotBlank()) { "action schema version is required" }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument name $name" }
        }
    }
}

@Serializable
data class OpenApiActionResult(
    val properties: Map<String, OpenApiActionSchema> = defaultOpenApiActionResultProperties(),
    val required: List<String> = listOf("action", "status"),
) {
    init {
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action result property name $name" }
        }
        required.forEach { name ->
            require(properties.containsKey(name)) { "required action result property $name is not declared" }
        }
    }
}

@Serializable
data class OpenApiActionSchema(
    val type: String,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action result property type $type" }
    }
}

fun String.isCraftlessActionId(): Boolean =
    matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) &&
        !startsWith("minecraft.")

fun String.isCraftlessActionArgumentName(): Boolean =
    matches(Regex("[a-z][a-z0-9-]*")) &&
        !startsWith("minecraft-")

@Serializable
data class OpenApiActionArgument(
    val type: String,
    val required: Boolean = false,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action argument type $type" }
    }
}

fun String.isCraftlessActionArgumentType(): Boolean = this in CRAFTLESS_ACTION_ARGUMENT_TYPES

private val CRAFTLESS_ACTION_ARGUMENT_TYPES =
    setOf("boolean", "integer", "number", "string", "object", "array")

private fun ApiRoute.toOperation(actionsById: Map<String, OpenApiAction>): OpenApiOperation {
    val route = this
    return OpenApiOperation(
        operationId = operationId,
        tags = listOf(tag),
        responses = route.responses(actionsById),
        requestBody = route.requestBody(actionsById),
        extensions =
            buildMap {
                put("x-craftless-owner", route.owner)
                route.member?.let { put("x-craftless-member", it) }
                put("x-craftless-target", target)
                put("x-craftless-return", returnKind)
                put("x-craftless-source", source)
                actionId?.let { put("x-craftless-action", it) }
            },
    )
}

private fun ApiRoute.responses(actionsById: Map<String, OpenApiAction>): Map<String, OpenApiResponse> {
    val successStatus = if (path == "/clients" && method == "POST") "201" else "200"
    return buildMap {
        put(
            successStatus,
            when {
                source == "action" && method == "POST" -> actionInvocationResponse(actionId?.let(actionsById::get)?.result)
                path.endsWith("openapi.json") && method == "GET" -> openApiDocumentResponse()
                path == "/version" && method == "GET" -> versionResponse()
                (path == "/events" || path.endsWith("/events")) && method == "GET" -> eventListResponse()
                path.endsWith("/actions") && method == "GET" -> actionListResponse()
                path == "/clients" && method == "GET" -> clientListResponse()
                path == "/clients" && method == "POST" -> clientResponse()
                path.endsWith(":connect") && method == "POST" -> clientResponse()
                path.endsWith(":stop") && method == "POST" -> clientResponse()
                path.matches(Regex("/clients/\\{?[^/]+}?")) && method == "GET" -> clientResponse()
                else -> OpenApiResponse()
            },
        )
        errorStatuses().forEach { status ->
            put(status, errorResponse(status))
        }
    }
}

private fun ApiRoute.errorStatuses(): List<String> =
    when {
        path == "/clients" && method == "POST" -> listOf("400")
        path.endsWith(":connect") && method == "POST" -> listOf("400", "404", "409")
        source == "action" && method == "POST" -> listOf("400", "404", "409")
        path.endsWith(":run") && method == "POST" -> listOf("400", "404", "409")
        path.endsWith(":stop") && method == "POST" -> listOf("404")
        path == "/clients/{id}" && method == "GET" -> listOf("404")
        path == "/clients/{id}/openapi.json" && method == "GET" -> listOf("404")
        path == "/clients/{id}/actions" && method == "GET" -> listOf("404")
        path == "/clients/{id}/events" && method == "GET" -> listOf("404")
        else -> emptyList()
    }

private fun ApiRoute.requestBody(actionsById: Map<String, OpenApiAction>): OpenApiRequestBody? =
    when {
        method != "POST" -> null
        actionId != null -> actionsById[actionId]?.arguments?.toRequestBody()
        path == "/clients" -> createClientRequestBody()
        path.endsWith(":connect") -> connectRequestBody()
        path.endsWith(":run") -> genericActionRequestBody()
        else -> null
    }

private fun Map<String, OpenApiActionArgument>.toRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties = mapValues { (_, argument) -> OpenApiSchema(type = argument.type) },
                    required = filterValues { it.required }.keys.toList(),
                    additionalProperties = false,
                ),
            ),
    )

private fun actionInvocationResponse(result: OpenApiActionResult? = null): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                result?.toOpenApiSchema() ?: OpenApiActionResult().toOpenApiSchema(),
            ),
    )

private fun errorResponse(status: String): OpenApiResponse =
    OpenApiResponse(
        description =
            when (status) {
                "400" -> "Bad Request"
                "404" -> "Not Found"
                "409" -> "Conflict"
                else -> "Error"
            },
        content = jsonContent(errorSchema()),
    )

private fun errorSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "code" to OpenApiSchema(type = "string"),
                "message" to OpenApiSchema(type = "string"),
            ),
        required = listOf("code", "message"),
    )

private fun versionResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "minecraft" to OpenApiSchema(type = "string"),
                            "loader" to OpenApiSchema(type = "string"),
                            "loaderVersion" to OpenApiSchema(type = "string"),
                            "driver" to OpenApiSchema(type = "string"),
                            "driverVersion" to OpenApiSchema(type = "string"),
                            "java" to OpenApiSchema(type = "string"),
                            "mappingsFingerprint" to OpenApiSchema(type = "string"),
                            "openapiGeneratedAt" to OpenApiSchema(type = "string"),
                        ),
                    required =
                        listOf(
                            "minecraft",
                            "loader",
                            "loaderVersion",
                            "driver",
                            "driverVersion",
                            "java",
                            "mappingsFingerprint",
                            "openapiGeneratedAt",
                        ),
                ),
            ),
    )

private fun eventListResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "array",
                    items =
                        OpenApiSchema(
                            type = "object",
                            properties =
                                mapOf(
                                    "type" to OpenApiSchema(type = "string"),
                                    "client" to OpenApiSchema(type = "string"),
                                    "message" to OpenApiSchema(type = "string"),
                                    "time" to OpenApiSchema(type = "string"),
                                ),
                            required = listOf("type", "time"),
                        ),
                ),
            ),
    )

private fun actionListResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "array",
                    items = actionDescriptorSchema(),
                ),
            ),
    )

private fun openApiDocumentResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "openapi" to OpenApiSchema(type = "string"),
                            "info" to
                                OpenApiSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "title" to OpenApiSchema(type = "string"),
                                            "version" to OpenApiSchema(type = "string"),
                                        ),
                                    required = listOf("title", "version"),
                                ),
                            "paths" to OpenApiSchema(type = "object", additionalProperties = true),
                            "x-craftless" to OpenApiSchema(type = "object", additionalProperties = true),
                            "x-craftless-actions" to
                                OpenApiSchema(
                                    type = "array",
                                    items = actionDescriptorSchema(),
                                ),
                        ),
                    required = listOf("openapi", "info", "paths"),
                ),
            ),
    )

private fun actionDescriptorSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "id" to OpenApiSchema(type = "string"),
                "schemaVersion" to OpenApiSchema(type = "string"),
                "args" to OpenApiSchema(type = "object", additionalProperties = true),
                "result" to OpenApiSchema(type = "object", additionalProperties = true),
            ),
        required = listOf("id", "schemaVersion"),
    )

private fun genericActionRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "action" to OpenApiSchema(type = "string"),
                            "args" to OpenApiSchema(type = "object", additionalProperties = true),
                        ),
                    required = listOf("action"),
                ),
            ),
    )

private fun clientListResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "array",
                    items = clientSchema(),
                ),
            ),
    )

private fun clientResponse(): OpenApiResponse = OpenApiResponse(content = jsonContent(clientSchema()))

private fun clientSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "id" to OpenApiSchema(type = "string"),
                "instance" to
                    OpenApiSchema(
                        type = "object",
                        properties =
                            mapOf(
                                "id" to OpenApiSchema(type = "string"),
                                "version" to
                                    OpenApiSchema(
                                        type = "object",
                                        properties =
                                            mapOf(
                                                "id" to OpenApiSchema(type = "string"),
                                            ),
                                        required = listOf("id"),
                                    ),
                                "loader" to OpenApiSchema(type = "string"),
                                "files" to instanceFilesSchema(),
                            ),
                        required = listOf("id", "version", "loader", "files"),
                    ),
                "profile" to profileSchema(),
                "state" to OpenApiSchema(type = "string"),
            ),
        required = listOf("id", "instance", "profile", "state"),
    )

private fun createClientRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "id" to OpenApiSchema(type = "string"),
                            "version" to OpenApiSchema(type = "string"),
                            "loader" to OpenApiSchema(type = "string"),
                            "profile" to profileSchema(),
                        ),
                    required = listOf("id", "version", "loader", "profile"),
                ),
            ),
    )

private fun connectRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "host" to OpenApiSchema(type = "string"),
                            "port" to OpenApiSchema(type = "integer"),
                        ),
                    required = listOf("host", "port"),
                ),
            ),
    )

private fun profileSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "kind" to OpenApiSchema(type = "string"),
                "name" to OpenApiSchema(type = "string"),
            ),
        required = listOf("kind", "name"),
    )

private fun instanceFilesSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "root" to OpenApiSchema(type = "string"),
                "gameRoot" to OpenApiSchema(type = "string"),
                "mods" to OpenApiSchema(type = "string"),
                "config" to OpenApiSchema(type = "string"),
                "saves" to OpenApiSchema(type = "string"),
                "resourcePacks" to OpenApiSchema(type = "string"),
                "shaderPacks" to OpenApiSchema(type = "string"),
            ),
        required = listOf("root", "gameRoot", "mods", "config", "saves", "resourcePacks", "shaderPacks"),
    )

private fun jsonContent(schema: OpenApiSchema): Map<String, OpenApiMediaType> = mapOf("application/json" to OpenApiMediaType(schema))

private fun OpenApiActionResult.toOpenApiSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties = properties.mapValues { (_, schema) -> schema.toOpenApiSchema() },
        required = required,
    )

private fun OpenApiActionSchema.toOpenApiSchema(): OpenApiSchema = OpenApiSchema(type = type)

private fun defaultOpenApiActionResultProperties(): Map<String, OpenApiActionSchema> =
    mapOf(
        "action" to OpenApiActionSchema("string"),
        "status" to OpenApiActionSchema("string"),
        "message" to OpenApiActionSchema("string"),
    )
