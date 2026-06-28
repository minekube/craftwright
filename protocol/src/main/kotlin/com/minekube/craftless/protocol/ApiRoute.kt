package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ApiRoute(
    val method: String,
    val path: String,
    val operationId: String,
    val tag: String,
    val owner: String,
    val member: String? = null,
    val target: String = "supervisor",
    val source: String,
    val returnKind: String = "value",
    val actionId: String? = null,
) {
    init {
        require(method in SUPPORTED_ROUTE_METHODS) { "unsupported route method $method" }
        require(path.startsWith("/")) { "route path must start with /" }
        require(!path.containsForbiddenPublicRoutePathToken()) { "route path must be Craftless-owned" }
        require(operationId.isNotBlank()) { "route operation id is required" }
        require(!operationId.containsForbiddenPublicNamespaceToken()) { "route operation id must be Craftless-owned" }
        require(tag.isNotBlank()) { "route tag is required" }
        require(!tag.containsForbiddenPublicNamespaceToken()) { "route tag must be Craftless-owned" }
        require(owner.isNotBlank()) { "route owner is required" }
        require(!owner.containsForbiddenPublicNamespaceToken()) { "route owner must be Craftless-owned" }
        require(member == null || member.isNotBlank()) { "route member is required" }
        require(member == null || !member.containsForbiddenPublicNamespaceToken()) { "route member must be Craftless-owned" }
        require(target in SUPPORTED_ROUTE_TARGETS) { "unsupported route target $target" }
        require(source in SUPPORTED_ROUTE_SOURCES) { "unsupported route source $source" }
        require(returnKind in SUPPORTED_ROUTE_RETURN_KINDS) { "unsupported route return kind $returnKind" }
        actionId?.let { require(it.isCraftlessActionId()) { "invalid action id $it" } }
    }
}

private val SUPPORTED_ROUTE_METHODS = setOf("GET", "POST")
private val SUPPORTED_ROUTE_TARGETS = setOf("supervisor", "client")
private val SUPPORTED_ROUTE_SOURCES = setOf("route", "method", "action", "resource")
private val SUPPORTED_ROUTE_RETURN_KINDS = setOf("value")

private fun String.containsForbiddenPublicRoutePathToken(): Boolean =
    replace(Regex("^/clients/[^/:]+"), "/clients/{id}")
        .containsForbiddenPublicNamespaceToken()

class ApiRouteCatalog(
    val routes: List<ApiRoute>,
) {
    init {
        val duplicateRoute =
            routes
                .groupBy { it.method to it.path }
                .entries
                .firstOrNull { (_, matches) -> matches.size > 1 }
        if (duplicateRoute != null) {
            val (method, path) = duplicateRoute.key
            require(false) { "duplicate route $method $path" }
        }
    }

    private val byPath: Map<String, List<ApiRoute>> = routes.groupBy { it.path }
    private val byMethodAndPath: Map<Pair<String, String>, ApiRoute> =
        routes.associateBy { it.method to it.path }

    fun route(path: String): ApiRoute {
        val matches = byPath[path] ?: error("route not found: $path")
        if (matches.size > 1) {
            val methods = matches.map { it.method }.sorted().joinToString(", ")
            error("ambiguous route path $path has methods $methods; use route(method, path)")
        }
        return matches.single()
    }

    fun route(
        method: String,
        path: String,
    ): ApiRoute = byMethodAndPath[method to path] ?: error("route not found: $method $path")

    companion object {
        fun sessionDefaults(): ApiRouteCatalog =
            ApiRouteCatalog(
                listOf(
                    route("GET", "/openapi.json", "getOpenapiJson", "openapi", "supervisor", "openapi", "route"),
                    route("GET", "/version", "getVersion", "version", "supervisor", "version", "route"),
                    route("GET", "/events", "getEvents", "events", "supervisor", "events", "route"),
                    route("GET", "/events:stream", "streamEvents", "events", "supervisor", "events", "route"),
                    route("POST", "/cache:prepare", "prepareCache", "cache", "cache", "prepare", "method"),
                    route("POST", "/cache:export", "exportCache", "cache", "cache", "export", "method"),
                    route("POST", "/cache:cleanup", "cleanupCache", "cache", "cache", "cleanup", "method"),
                    route("GET", "/runtimes/java", "listJavaRuntimes", "runtimes", "runtimes", "java", "route"),
                    route("POST", "/runtimes/java:resolve", "resolveJavaRuntime", "runtimes", "runtimes", "java", "method"),
                    route("GET", "/clients", "listClients", "clients", "clients", "list", "route"),
                    route("POST", "/clients", "createClient", "clients", "clients", "create", "route"),
                    route("GET", "/clients/{id}", "getClient", "clients", "clients", "get", "route"),
                    route("GET", "/clients/{id}/openapi.json", "getClientOpenapiJson", "clients", "clients", "openapi", "route"),
                    route("POST", "/clients/{id}:attach", "attachClientDriver", "clients", "clients", "attach", "method"),
                    route("POST", "/clients/{id}:connect", "clientConnect", "clients", "clients", "connect", "method"),
                    route("GET", "/clients/{id}/actions", "listClientActions", "clients", "clients", "actions", "action"),
                    route("GET", "/clients/{id}/resources", "listClientResources", "clients", "clients", "resources", "resource"),
                    route("POST", "/clients/{id}:run", "runClientAction", "clients", "clients", "run", "action"),
                    route("POST", "/clients/{id}:stop", "stopClient", "clients", "clients", "stop", "method"),
                    route("GET", "/clients/{id}/events", "getClientEvents", "clients", "clients", "events", "route"),
                    route("GET", "/clients/{id}/events:stream", "streamClientEvents", "clients", "clients", "events", "route"),
                ),
            )

        private fun route(
            method: String,
            path: String,
            operationId: String,
            tag: String,
            owner: String,
            member: String,
            source: String,
            returnKind: String = "value",
        ): ApiRoute =
            ApiRoute(
                method = method,
                path = path,
                operationId = operationId,
                tag = tag,
                owner = owner,
                member = member,
                target = if (source == "action" || source == "resource") "client" else "supervisor",
                source = source,
                returnKind = returnKind,
            )
    }
}
