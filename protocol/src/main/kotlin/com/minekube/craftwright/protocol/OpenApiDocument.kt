package com.minekube.craftwright.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenApiDocument(
    val openapi: String = "3.1.0",
    val info: OpenApiInfo = OpenApiInfo(),
    val paths: Map<String, OpenApiPath>,
) {
    companion object {
        fun from(catalog: ApiRouteCatalog): OpenApiDocument =
            OpenApiDocument(paths = catalog.routes.groupBy { it.path }.mapValues { (_, routes) ->
                OpenApiPath(
                    get = routes.firstOrNull { it.method == "GET" }?.toOperation(),
                    post = routes.firstOrNull { it.method == "POST" }?.toOperation(),
                )
            })
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
    val responses: Map<String, OpenApiResponse> = mapOf("200" to OpenApiResponse()),
    @SerialName("x-craftwright")
    val extensions: Map<String, String>,
)

@Serializable
data class OpenApiResponse(
    val description: String = "OK",
)

private fun ApiRoute.toOperation(): OpenApiOperation {
    val route = this
    return OpenApiOperation(
        operationId = operationId,
        tags = listOf(tag),
        extensions = buildMap {
            put("x-craftwright-java-class", route.javaClass)
            javaMember?.let { put("x-craftwright-java-method", it) }
            put("x-craftwright-thread", thread)
            put("x-craftwright-return", returnKind)
            put("x-craftwright-source", source)
        },
    )
}
