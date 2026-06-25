package dev.minekube.craftwright.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ApiRoute(
    val method: String,
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
                route("GET", "/openapi.json", "getOpenapiJson", "openapi", "dev.minekube.craftwright.openapi", "openapi", "route"),
                route("GET", "/version", "getVersion", "version", "dev.minekube.craftwright.version", "version", "route"),
                route("GET", "/events", "getEvents", "events", "dev.minekube.craftwright.events", "events", "route"),
                route("GET", "/client", "getClient", "client", "dev.minekube.craftwright.client", "client", "root", "handle"),
                route("GET", "/client/state", "getClientState", "client", "dev.minekube.craftwright.client", "state", "getter"),
                route("GET", "/player", "getPlayer", "player", "dev.minekube.craftwright.player", "player", "root", "handle"),
                route("GET", "/player/name", "getPlayerName", "player", "dev.minekube.craftwright.player", "name", "getter"),
                route("GET", "/player/position", "getPlayerPosition", "player", "dev.minekube.craftwright.player", "position", "getter"),
                route("POST", "/player/sendChat", "playerSendChat", "player", "dev.minekube.craftwright.player", "sendChat", "method"),
                route("GET", "/connection", "getConnection", "connection", "dev.minekube.craftwright.connection", "connection", "root", "handle"),
                route("POST", "/clients", "createClient", "clients", "dev.minekube.craftwright.daemon.clients", "create", "route"),
                route("POST", "/clients/{id}/connection/connect", "clientConnect", "clients", "dev.minekube.craftwright.daemon.clients", "connect", "method"),
                route("POST", "/clients/{id}/player/sendChat", "clientPlayerSendChat", "clients", "dev.minekube.craftwright.daemon.clients", "sendChat", "method"),
                route("GET", "/clients/{id}/player", "getClientPlayer", "clients", "dev.minekube.craftwright.daemon.clients", "player", "root", "value"),
                route("GET", "/clients/{id}/player/position", "getClientPlayerPosition", "clients", "dev.minekube.craftwright.daemon.clients", "position", "getter"),
                route("POST", "/clients/{id}/stop", "stopClient", "clients", "dev.minekube.craftwright.daemon.clients", "stop", "method"),
                route("GET", "/clients/{id}/events", "getClientEvents", "clients", "dev.minekube.craftwright.daemon.clients", "events", "route"),
                route("GET", "/o/{handle}", "getObjectHandle", "objects", "java.lang.Object", "handle", "handle", "handle"),
                route("GET", "/o/{handle}/fields", "getObjectFields", "objects", "java.lang.Object", "fields", "handle"),
                route("GET", "/o/{handle}/field/{field}", "getObjectField", "objects", "java.lang.Object", "field", "handle"),
                route("POST", "/o/{handle}/field/{field}", "setObjectField", "objects", "java.lang.Object", "field", "handle"),
                route("GET", "/o/{handle}/methods", "getObjectMethods", "objects", "java.lang.Object", "methods", "handle"),
                route("POST", "/o/{handle}/method/{method}", "callObjectMethod", "objects", "java.lang.Object", "method", "handle"),
                route("GET", "/c/{className}", "getClassMetadata", "classes", "java.lang.Class", "className", "class"),
                route("GET", "/c/{className}/fields", "getClassFields", "classes", "java.lang.Class", "fields", "class"),
                route("GET", "/c/{className}/methods", "getClassMethods", "classes", "java.lang.Class", "methods", "class"),
            )
        )

        private fun route(
            method: String,
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
