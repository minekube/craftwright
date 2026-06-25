package dev.minekube.craftwright.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class HttpMethod {
    GET,
    POST,
}

@Serializable
data class ApiRoute(
    val method: HttpMethod,
    val path: String,
    val operationId: String,
    val tag: String,
    val javaClass: String,
    val javaMember: String? = null,
    val thread: String = "client",
    val source: String,
    val returnKind: String = "value",
)

class ApiRouteCatalog private constructor(
    val routes: List<ApiRoute>,
) {
    private val byPath: Map<String, ApiRoute> = routes.associateBy { it.path }

    fun route(path: String): ApiRoute =
        byPath[path] ?: error("route not found: $path")

    companion object {
        fun sessionDefaults(): ApiRouteCatalog = ApiRouteCatalog(
            listOf(
                route(HttpMethod.GET, "/openapi.json", "getOpenapiJson", "openapi", "dev.minekube.craftwright.openapi", "openapi", "route"),
                route(HttpMethod.GET, "/version", "getVersion", "version", "dev.minekube.craftwright.version", "version", "route"),
                route(HttpMethod.GET, "/events", "getEvents", "events", "dev.minekube.craftwright.events", "events", "route"),
                route(HttpMethod.GET, "/client", "getClient", "client", "dev.minekube.craftwright.client", "client", "root", "handle"),
                route(HttpMethod.GET, "/client/state", "getClientState", "client", "dev.minekube.craftwright.client", "state", "getter"),
                route(HttpMethod.GET, "/player", "getPlayer", "player", "dev.minekube.craftwright.player", "player", "root", "handle"),
                route(HttpMethod.GET, "/player/name", "getPlayerName", "player", "dev.minekube.craftwright.player", "name", "getter"),
                route(HttpMethod.POST, "/player/sendChat", "playerSendChat", "player", "dev.minekube.craftwright.player", "sendChat", "method"),
                route(HttpMethod.GET, "/connection", "getConnection", "connection", "dev.minekube.craftwright.connection", "connection", "root", "handle"),
                route(HttpMethod.GET, "/o/{handle}", "getObjectHandle", "objects", "java.lang.Object", "handle", "handle", "handle"),
                route(HttpMethod.GET, "/o/{handle}/fields", "getObjectFields", "objects", "java.lang.Object", "fields", "handle"),
                route(HttpMethod.GET, "/o/{handle}/field/{field}", "getObjectField", "objects", "java.lang.Object", "field", "handle"),
                route(HttpMethod.POST, "/o/{handle}/field/{field}", "setObjectField", "objects", "java.lang.Object", "field", "handle"),
                route(HttpMethod.GET, "/o/{handle}/methods", "getObjectMethods", "objects", "java.lang.Object", "methods", "handle"),
                route(HttpMethod.POST, "/o/{handle}/method/{method}", "callObjectMethod", "objects", "java.lang.Object", "method", "handle"),
                route(HttpMethod.GET, "/c/{className}", "getClassMetadata", "classes", "java.lang.Class", "className", "class"),
                route(HttpMethod.GET, "/c/{className}/fields", "getClassFields", "classes", "java.lang.Class", "fields", "class"),
                route(HttpMethod.GET, "/c/{className}/methods", "getClassMethods", "classes", "java.lang.Class", "methods", "class"),
            )
        )

        private fun route(
            method: HttpMethod,
            path: String,
            operationId: String,
            tag: String,
            javaClass: String,
            javaMember: String,
            source: String,
            returnKind: String = "value",
        ): ApiRoute = ApiRoute(
            method = method,
            path = path,
            operationId = operationId,
            tag = tag,
            javaClass = javaClass,
            javaMember = javaMember,
            source = source,
            returnKind = returnKind,
        )
    }
}
