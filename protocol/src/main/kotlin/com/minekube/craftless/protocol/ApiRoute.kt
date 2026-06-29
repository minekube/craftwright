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
    val summary: String? = null,
    val description: String? = null,
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
        private const val OPENAPI_DESCRIPTION =
            "Returns the stable supervisor API contract for Craftless lifecycle, cache, runtime, client management, events, and per-client discovery. Adaptive agents and CLIs should fetch this before choosing supervisor routes."

        private const val VERSION_DESCRIPTION =
            "Returns daemon and runtime identity information, including Craftless driver, Java runtime, platform, and protocol details. Use it to confirm which local supervisor is answering before making lifecycle claims."

        private const val EVENTS_DESCRIPTION =
            "Lists recent supervisor lifecycle and diagnostic events retained by the daemon. Use this for bounded status checks before switching to the live stream for ongoing observation."

        private const val EVENTS_STREAM_DESCRIPTION =
            "Streams supervisor lifecycle and diagnostic events as Server-Sent Events. Subscribe when an agent needs fresh evidence about daemon, cache, client, attach, connect, or stop progress."

        private const val CACHE_PREPARE_DESCRIPTION =
            "Prepares Craftless-owned cache and launch material for a Minecraft version and loader before a client is created. This resolves metadata, artifacts, libraries, assets, native paths, and launch inputs without exposing launcher internals."

        private const val CACHE_EXPORT_DESCRIPTION =
            "Exports prepared cache material into an archive or manifest-oriented artifact set. Use this for repeatable CI, distribution, or offline preparation after cache preparation has resolved the runtime inputs."

        private const val CACHE_CLEANUP_DESCRIPTION =
            "Cleans cache material for a prepared manifest. Use this to remove obsolete resolved artifacts while keeping cache management inside the supervisor API instead of deleting launcher files directly."

        private const val JAVA_LIST_DESCRIPTION =
            "Lists Java runtime candidates known to Craftless. Use this to inspect configured, managed, and system runtimes before resolving which Java runtime can launch a selected Minecraft version."

        private const val JAVA_RESOLVE_DESCRIPTION =
            "Resolves the Java runtime suitable for a requested Minecraft version. Use this before launch diagnostics when an agent must explain why a version can or cannot be started on the current machine."

        private const val CLIENT_LIST_DESCRIPTION =
            "Lists daemon-managed client processes. Use this before creating another client in an existing daemon or workspace so agents can reuse or stop prior attempts instead of launching duplicates."

        private const val CLIENT_CREATE_DESCRIPTION =
            "This operation launches a new daemon-managed real Minecraft Java client process. This is not a selector, retry, or reuse operation. Before calling it in an existing daemon or workspace, call GET /clients and GET /clients/{id} to reuse a suitable client. If replacing a failed attempt, stop the old client with POST /clients/{id}:stop first. Creating fresh timestamped ids for retries leaves multiple Minecraft clients running."

        private const val CLIENT_GET_DESCRIPTION =
            "Inspects one existing daemon-managed client process. Prefer this when a client id is already known; it does not launch a new client."

        private const val CLIENT_CONNECT_DESCRIPTION =
            "Connects an existing client process to a Minecraft server. This does not create or replace a client."

        private const val CLIENT_OPENAPI_DESCRIPTION =
            "Returns the generated live API for one client. This per-client OpenAPI document is the authority for that client's discovered actions, resources, aliases, schemas, handles, availability, and runtime fingerprints."

        private const val CLIENT_ATTACH_DESCRIPTION =
            "Attaches an already-running in-client Craftless driver endpoint to the supervisor. Use this when a real client exists outside daemon launch but should still expose generated per-client APIs."

        private const val CLIENT_ACTIONS_DESCRIPTION =
            "Lists the current action projection for one client. This is convenient discovery evidence, while the generated live API remains the authority for schemas, aliases, and invocation details."

        private const val CLIENT_RESOURCES_DESCRIPTION =
            "Lists the current resource projection for one client, including discovered resource groups and handles when available. Use it to inspect live affordances before invoking advertised actions."

        private const val CLIENT_ARTIFACT_DESCRIPTION =
            "Downloads a daemon-owned artifact for one client, such as media produced by a generated action. Artifact ids are resolved under the client's Craftless runtime artifact directory and cannot traverse outside it."

        private const val CLIENT_RUN_DESCRIPTION =
            "Invokes one advertised action on an existing client through the generic public execution route. Agents must choose the action id and arguments from the generated live API or projections for that same client."

        private const val CLIENT_RPC_DESCRIPTION =
            "Sends a JSON-RPC-style control or query request to an existing client. Use this for generic query, invoke, subscribe, and unsubscribe flows when the generated API advertises the target shape."

        private const val CLIENT_STOP_DESCRIPTION =
            "Stops and releases one daemon-managed client process. Use this before replacing or retrying a failed client launch."

        private const val CLIENT_EVENTS_DESCRIPTION =
            "Lists recent lifecycle, runtime, capability, and invocation events for one client. Use this for bounded evidence about attach, connect, discovery, or action outcomes."

        private const val CLIENT_EVENTS_STREAM_DESCRIPTION =
            "Streams lifecycle, runtime, capability, and invocation events for one client as Server-Sent Events. Subscribe before state-changing work when an agent needs correlated live evidence."

        fun sessionDefaults(): ApiRouteCatalog =
            ApiRouteCatalog(
                listOf(
                    route(
                        "GET",
                        "/openapi.json",
                        "getOpenapiJson",
                        "openapi",
                        "supervisor",
                        "openapi",
                        "route",
                        cli("openapi"),
                        summary = "Fetch supervisor OpenAPI",
                        description = OPENAPI_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/version",
                        "getVersion",
                        "version",
                        "supervisor",
                        "version",
                        "route",
                        cli("version"),
                        summary = "Inspect daemon runtime",
                        description = VERSION_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/events",
                        "getEvents",
                        "events",
                        "supervisor",
                        "events",
                        "route",
                        cli("events", "list"),
                        summary = "List supervisor events",
                        description = EVENTS_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/events:stream",
                        "streamEvents",
                        "events",
                        "supervisor",
                        "events",
                        "route",
                        cli("events", stream = true),
                        summary = "Stream supervisor events",
                        description = EVENTS_STREAM_DESCRIPTION,
                    ),
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
                        summary = "Prepare cache artifacts",
                        description = CACHE_PREPARE_DESCRIPTION,
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
                        summary = "Export prepared cache",
                        description = CACHE_EXPORT_DESCRIPTION,
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
                        summary = "Clean prepared cache",
                        description = CACHE_CLEANUP_DESCRIPTION,
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
                        summary = "List Java runtimes",
                        description = JAVA_LIST_DESCRIPTION,
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
                        summary = "Resolve Java runtime",
                        description = JAVA_RESOLVE_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/clients",
                        "listClients",
                        "clients",
                        "clients",
                        "list",
                        "route",
                        cli("clients", "list"),
                        summary = "List client processes",
                        description = CLIENT_LIST_DESCRIPTION,
                    ),
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
                        summary = "Launch a new client process",
                        description = CLIENT_CREATE_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/clients/{id}",
                        "getClient",
                        "clients",
                        "clients",
                        "get",
                        "route",
                        cli("clients", "{id}", "get"),
                        summary = "Inspect a client process",
                        description = CLIENT_GET_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/clients/{id}/openapi.json",
                        "getClientOpenapiJson",
                        "clients",
                        "clients",
                        "openapi",
                        "route",
                        cli("clients", "{id}", "openapi"),
                        summary = "Fetch client OpenAPI",
                        description = CLIENT_OPENAPI_DESCRIPTION,
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
                        summary = "Attach client driver",
                        description = CLIENT_ATTACH_DESCRIPTION,
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
                        summary = "Connect an existing client",
                        description = CLIENT_CONNECT_DESCRIPTION,
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
                        summary = "List client actions",
                        description = CLIENT_ACTIONS_DESCRIPTION,
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
                        summary = "List client resources",
                        description = CLIENT_RESOURCES_DESCRIPTION,
                    ),
                    route(
                        "GET",
                        "/clients/{id}/artifacts/{artifact-id}",
                        "getClientArtifact",
                        "clients",
                        "clients",
                        "artifacts",
                        "route",
                        cli("clients", "{id}", "artifacts", "{artifact-id}"),
                        summary = "Download client artifact",
                        description = CLIENT_ARTIFACT_DESCRIPTION,
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
                        summary = "Run client action",
                        description = CLIENT_RUN_DESCRIPTION,
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
                        summary = "Send client RPC",
                        description = CLIENT_RPC_DESCRIPTION,
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
                        summary = "Stop a client process",
                        description = CLIENT_STOP_DESCRIPTION,
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
                        summary = "List client events",
                        description = CLIENT_EVENTS_DESCRIPTION,
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
                        summary = "Stream client events",
                        description = CLIENT_EVENTS_STREAM_DESCRIPTION,
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
            summary: String? = null,
            description: String? = null,
        ): ApiRoute =
            ApiRoute(
                method = method,
                path = path,
                operationId = operationId,
                tag = tag,
                summary = summary,
                description = description,
                owner = owner,
                member = member,
                target = if (source == "action" || source == "resource") "client" else "supervisor",
                source = source,
                returnKind = returnKind,
                cli = cli,
            )
    }
}
