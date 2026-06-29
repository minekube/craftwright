package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ApiRouteCli(
    val command: List<String>,
    val aliases: List<List<String>> = emptyList(),
    val hidden: Boolean = false,
    val stream: Boolean = false,
    val body: List<ApiRouteCliBinding> = emptyList(),
)

@Serializable
data class ApiRouteCliBinding(
    val pointer: String,
    val flag: String? = null,
    val argument: String? = null,
    val fixed: String? = null,
    val type: String = "string",
    val required: Boolean = false,
)

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
    val cli: ApiRouteCli? = null,
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
                    route("GET", "/openapi.json", "getOpenapiJson", "openapi", "supervisor", "openapi", "route", cli("openapi")),
                    route("GET", "/version", "getVersion", "version", "supervisor", "version", "route", cli("version")),
                    route("GET", "/events", "getEvents", "events", "supervisor", "events", "route", cli("events", "list")),
                    route("GET", "/events:stream", "streamEvents", "events", "supervisor", "events", "route", cli("events", stream = true)),
                    route(
                        "POST",
                        "/cache:prepare",
                        "prepareCache",
                        "cache",
                        "cache",
                        "prepare",
                        "method",
                        cli(
                            "cache",
                            "prepare",
                            body =
                                listOf(
                                    bind("/minecraftVersion", flag = "--mc", required = true),
                                    bind("/loader", flag = "--loader", required = true),
                                    bind("/loaderVersion", flag = "--loader-version"),
                                ),
                        ),
                    ),
                    route(
                        "POST",
                        "/cache:export",
                        "exportCache",
                        "cache",
                        "cache",
                        "export",
                        "method",
                        cli(
                            "cache",
                            "export",
                            body =
                                listOf(
                                    bind("/manifest", flag = "--manifest", required = true),
                                    bind("/archive", flag = "--archive"),
                                ),
                        ),
                    ),
                    route(
                        "POST",
                        "/cache:cleanup",
                        "cleanupCache",
                        "cache",
                        "cache",
                        "cleanup",
                        "method",
                        cli(
                            "cache",
                            "cleanup",
                            body = listOf(bind("/manifest", flag = "--manifest", required = true)),
                        ),
                    ),
                    route(
                        "GET",
                        "/runtimes/java",
                        "listJavaRuntimes",
                        "runtimes",
                        "runtimes",
                        "java",
                        "route",
                        cli("runtimes", "java", "list"),
                    ),
                    route(
                        "POST",
                        "/runtimes/java:resolve",
                        "resolveJavaRuntime",
                        "runtimes",
                        "runtimes",
                        "java",
                        "method",
                        cli(
                            "runtimes",
                            "java",
                            "resolve",
                            body = listOf(bind("/minecraftVersion", flag = "--mc", required = true)),
                        ),
                    ),
                    route("GET", "/clients", "listClients", "clients", "clients", "list", "route", cli("clients", "list")),
                    route(
                        "POST",
                        "/clients",
                        "createClient",
                        "clients",
                        "clients",
                        "create",
                        "route",
                        cli(
                            "clients",
                            "create",
                            "{id}",
                            body =
                                listOf(
                                    bind("/id", argument = "id", required = true),
                                    bind("/version", flag = "--version", required = true),
                                    bind("/loader", flag = "--loader", required = true),
                                    bind("/loaderVersion", flag = "--loader-version"),
                                    bind("/profile/name", flag = "--offline-name"),
                                    bind("/profile/kind", flag = "--offline-name", fixed = "OFFLINE"),
                                    bind("/presentation/window", flag = "--visible", fixed = "VISIBLE"),
                                    bind("/presentation/audio", flag = "--audio"),
                                ),
                        ),
                    ),
                    route("GET", "/clients/{id}", "getClient", "clients", "clients", "get", "route", cli("clients", "{id}", "get")),
                    route(
                        "GET",
                        "/clients/{id}/openapi.json",
                        "getClientOpenapiJson",
                        "clients",
                        "clients",
                        "openapi",
                        "route",
                        cli("clients", "{id}", "openapi"),
                    ),
                    route(
                        "POST",
                        "/clients/{id}:attach",
                        "attachClientDriver",
                        "clients",
                        "clients",
                        "attach",
                        "method",
                        cli(
                            "clients",
                            "{id}",
                            "attach",
                            body = listOf(bind("/endpoint", flag = "--endpoint", required = true)),
                        ),
                    ),
                    route(
                        "POST",
                        "/clients/{id}:connect",
                        "clientConnect",
                        "clients",
                        "clients",
                        "connect",
                        "method",
                        cli(
                            "clients",
                            "{id}",
                            "connect",
                            body =
                                listOf(
                                    bind("/host", flag = "--host", required = true),
                                    bind("/port", flag = "--port", type = "integer", required = true),
                                ),
                        ),
                    ),
                    route(
                        "GET",
                        "/clients/{id}/actions",
                        "listClientActions",
                        "clients",
                        "clients",
                        "actions",
                        "action",
                        cli("clients", "{id}", "actions"),
                    ),
                    route(
                        "GET",
                        "/clients/{id}/resources",
                        "listClientResources",
                        "clients",
                        "clients",
                        "resources",
                        "resource",
                        cli("clients", "{id}", "resources"),
                    ),
                    route(
                        "POST",
                        "/clients/{id}:run",
                        "runClientAction",
                        "clients",
                        "clients",
                        "run",
                        "action",
                        cli("clients", "{id}", "run", "{action}"),
                    ),
                    route(
                        "POST",
                        "/clients/{id}:rpc",
                        "clientJsonRpc",
                        "clients",
                        "clients",
                        "rpc",
                        "method",
                        cli(
                            "clients",
                            "{id}",
                            "query",
                            "{target}",
                            body =
                                listOf(
                                    bind("/method", fixed = "query"),
                                    bind("/params/target", argument = "target", required = true),
                                ),
                        ),
                    ),
                    route(
                        "POST",
                        "/clients/{id}:stop",
                        "stopClient",
                        "clients",
                        "clients",
                        "stop",
                        "method",
                        cli("clients", "{id}", "stop"),
                    ),
                    route(
                        "GET",
                        "/clients/{id}/events",
                        "getClientEvents",
                        "clients",
                        "clients",
                        "events",
                        "route",
                        cli("clients", "{id}", "events", "list"),
                    ),
                    route(
                        "GET",
                        "/clients/{id}/events:stream",
                        "streamClientEvents",
                        "clients",
                        "clients",
                        "events",
                        "route",
                        cli("clients", "{id}", "events", stream = true),
                    ),
                ),
            )

        private fun cli(
            vararg command: String,
            stream: Boolean = false,
            hidden: Boolean = false,
            aliases: List<List<String>> = emptyList(),
            body: List<ApiRouteCliBinding> = emptyList(),
        ): ApiRouteCli =
            ApiRouteCli(
                command = command.toList(),
                aliases = aliases,
                hidden = hidden,
                stream = stream,
                body = body,
            )

        private fun bind(
            pointer: String,
            flag: String? = null,
            argument: String? = null,
            fixed: String? = null,
            type: String = "string",
            required: Boolean = false,
        ): ApiRouteCliBinding =
            ApiRouteCliBinding(
                pointer = pointer,
                flag = flag,
                argument = argument,
                fixed = fixed,
                type = type,
                required = required,
            )

        private fun route(
            method: String,
            path: String,
            operationId: String,
            tag: String,
            owner: String,
            member: String,
            source: String,
            cli: ApiRouteCli? = null,
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
                cli = cli,
            )
    }
}
