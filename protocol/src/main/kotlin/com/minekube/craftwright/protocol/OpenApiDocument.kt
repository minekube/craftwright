package com.minekube.craftwright.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenApiDocument(
    val openapi: String = "3.1.0",
    val info: OpenApiInfo = OpenApiInfo(),
    val paths: Map<String, OpenApiPath>,
    @SerialName("x-craftwright")
    val extensions: Map<String, String> = emptyMap(),
    @SerialName("x-craftwright-actions")
    val actions: List<OpenApiAction> = emptyList(),
) {
    companion object {
        fun from(
            catalog: ApiRouteCatalog,
            extensions: Map<String, String> = emptyMap(),
            actions: List<OpenApiAction> = emptyList(),
        ): OpenApiDocument {
            val actionsById = actions.associateBy { it.id }
            return OpenApiDocument(
                paths = catalog.routes.groupBy { it.path }.mapValues { (_, routes) ->
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
    val title: String = "Craftwright Client Session API",
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
    @SerialName("x-craftwright")
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
)

@Serializable
data class OpenApiActionArgument(
    val type: String,
    val required: Boolean = false,
)

private fun ApiRoute.toOperation(actionsById: Map<String, OpenApiAction>): OpenApiOperation {
    val route = this
    return OpenApiOperation(
        operationId = operationId,
        tags = listOf(tag),
        responses = route.responses(),
        requestBody = route.requestBody(actionsById),
        extensions = buildMap {
            put("x-craftwright-java-class", route.javaClass)
            javaMember?.let { put("x-craftwright-java-method", it) }
            put("x-craftwright-thread", thread)
            put("x-craftwright-return", returnKind)
            put("x-craftwright-source", source)
            actionId?.let { put("x-craftwright-action", it) }
        },
    )
}

private fun ApiRoute.responses(): Map<String, OpenApiResponse> {
    val successStatus = if (path == "/clients" && method == "POST") "201" else "200"
    return mapOf(
        successStatus to when {
            source == "action" && method == "POST" -> actionInvocationResponse()
            path == "/clients" && method == "GET" -> clientListResponse()
            path == "/clients" && method == "POST" -> clientResponse()
            path.endsWith(":connect") && method == "POST" -> clientResponse()
            path.endsWith("/stop") && method == "POST" -> clientResponse()
            path.matches(Regex("/clients/\\{?[^/]+}?")) && method == "GET" -> clientResponse()
            else -> OpenApiResponse()
        }
    )
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
        content = jsonContent(
            OpenApiSchema(
                type = "object",
                properties = mapValues { (_, argument) -> OpenApiSchema(type = argument.type) },
                required = filterValues { it.required }.keys.toList(),
            )
        )
    )

private fun actionInvocationResponse(): OpenApiResponse =
    OpenApiResponse(
        content = jsonContent(
            OpenApiSchema(
                type = "object",
                properties = mapOf(
                    "action" to OpenApiSchema(type = "string"),
                    "status" to OpenApiSchema(type = "string"),
                    "message" to OpenApiSchema(type = "string"),
                ),
                required = listOf("action", "status"),
            )
        )
    )

private fun genericActionRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content = jsonContent(
            OpenApiSchema(
                type = "object",
                properties = mapOf(
                    "action" to OpenApiSchema(type = "string"),
                    "args" to OpenApiSchema(type = "object", additionalProperties = true),
                ),
                required = listOf("action"),
            )
        )
    )

private fun clientListResponse(): OpenApiResponse =
    OpenApiResponse(
        content = jsonContent(
            OpenApiSchema(
                type = "array",
                items = clientSchema(),
            )
        )
    )

private fun clientResponse(): OpenApiResponse =
    OpenApiResponse(content = jsonContent(clientSchema()))

private fun clientSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties = mapOf(
            "id" to OpenApiSchema(type = "string"),
            "instance" to OpenApiSchema(
                type = "object",
                properties = mapOf(
                    "id" to OpenApiSchema(type = "string"),
                    "version" to OpenApiSchema(
                        type = "object",
                        properties = mapOf(
                            "id" to OpenApiSchema(type = "string"),
                        ),
                        required = listOf("id"),
                    ),
                    "loader" to OpenApiSchema(type = "string"),
                ),
                required = listOf("id", "version", "loader"),
            ),
            "profile" to profileSchema(),
            "state" to OpenApiSchema(type = "string"),
        ),
        required = listOf("id", "instance", "profile", "state"),
    )

private fun createClientRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content = jsonContent(
            OpenApiSchema(
                type = "object",
                properties = mapOf(
                    "id" to OpenApiSchema(type = "string"),
                    "version" to OpenApiSchema(type = "string"),
                    "loader" to OpenApiSchema(type = "string"),
                    "profile" to profileSchema(),
                ),
                required = listOf("id", "version", "loader", "profile"),
            )
        )
    )

private fun connectRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content = jsonContent(
            OpenApiSchema(
                type = "object",
                properties = mapOf(
                    "host" to OpenApiSchema(type = "string"),
                    "port" to OpenApiSchema(type = "integer"),
                ),
                required = listOf("host", "port"),
            )
        )
    )

private fun profileSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties = mapOf(
            "kind" to OpenApiSchema(type = "string"),
            "name" to OpenApiSchema(type = "string"),
        ),
        required = listOf("kind", "name"),
    )

private fun jsonContent(schema: OpenApiSchema): Map<String, OpenApiMediaType> =
    mapOf("application/json" to OpenApiMediaType(schema))
