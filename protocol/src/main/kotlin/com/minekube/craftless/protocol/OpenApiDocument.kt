package com.minekube.craftless.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenApiDocument(
    val openapi: String = "3.1.0",
    val info: OpenApiInfo = OpenApiInfo(),
    val tags: List<OpenApiTag> = emptyList(),
    val paths: Map<String, OpenApiPath>,
    @SerialName("x-craftless")
    val extensions: Map<String, String> = emptyMap(),
    @SerialName("x-craftless-actions")
    val actions: List<OpenApiAction> = emptyList(),
    @SerialName("x-craftless-resources")
    val resources: List<OpenApiResource> = emptyList(),
    @SerialName("x-craftless-handles")
    val handles: List<OpenApiHandle> = emptyList(),
    @SerialName("x-craftless-events")
    val events: List<OpenApiEvent> = emptyList(),
) {
    companion object {
        fun from(
            catalog: ApiRouteCatalog,
            extensions: Map<String, String> = emptyMap(),
            actions: List<OpenApiAction> = emptyList(),
            resources: List<OpenApiResource>? = null,
            handles: List<OpenApiHandle> = emptyList(),
            events: List<OpenApiEvent> = emptyList(),
        ): OpenApiDocument {
            val duplicateAction =
                actions
                    .groupBy { it.id }
                    .entries
                    .firstOrNull { (_, matches) -> matches.size > 1 }
            if (duplicateAction != null) {
                throw IllegalArgumentException("duplicate action id ${duplicateAction.key}")
            }
            val resourceProjection = resources ?: actions.toOpenApiResources()
            val actionsById = actions.associateBy { it.id }
            val duplicateResource =
                resourceProjection
                    .groupBy { it.id }
                    .entries
                    .firstOrNull { (_, matches) -> matches.size > 1 }
            if (duplicateResource != null) {
                throw IllegalArgumentException("duplicate resource id ${duplicateResource.key}")
            }
            val duplicateEvent =
                events
                    .groupBy { it.id }
                    .entries
                    .firstOrNull { (_, matches) -> matches.size > 1 }
            if (duplicateEvent != null) {
                throw IllegalArgumentException("duplicate event id ${duplicateEvent.key}")
            }
            resourceProjection.forEach { resource ->
                resource.actions.forEach { actionId ->
                    require(actionId in actionsById) { "resource ${resource.id} references unknown action $actionId" }
                }
            }
            val duplicateHandle =
                handles
                    .groupBy { it.id }
                    .entries
                    .firstOrNull { (_, matches) -> matches.size > 1 }
            if (duplicateHandle != null) {
                throw IllegalArgumentException("duplicate handle id ${duplicateHandle.key}")
            }
            val resourceIds = resourceProjection.map { it.id }.toSet()
            handles.forEach { handle ->
                require(handle.resource in resourceIds) { "handle ${handle.id} references unknown resource ${handle.resource}" }
            }
            catalog.routes.forEach { route ->
                val actionId = route.actionId
                if (actionId != null && actionId !in actionsById) {
                    throw IllegalArgumentException("action route ${route.operationId} references unknown action $actionId")
                }
            }
            return OpenApiDocument(
                tags = catalog.toOpenApiTags(),
                paths =
                    catalog.routes.groupBy { it.path }.mapValues { (_, routes) ->
                        OpenApiPath(
                            get = routes.firstOrNull { it.method == "GET" }?.toOperation(actionsById),
                            post = routes.firstOrNull { it.method == "POST" }?.toOperation(actionsById),
                        )
                    },
                extensions = extensions,
                actions = actions,
                resources = resourceProjection,
                handles = handles,
                events = events,
            )
        }

        fun fromRuntimeGraph(
            graph: RuntimeCapabilityGraph,
            extensions: Map<String, String> = emptyMap(),
        ): OpenApiDocument {
            val actions = graph.operations.sortedBy { it.id }.map { it.toOpenApiAction() }
            return from(
                catalog =
                    ApiRouteCatalog(
                        ApiRouteCatalog.sessionDefaults().routes +
                            graph.operations.sortedBy { it.id }.map { it.toActionAliasRoute() },
                    ),
                extensions = extensions + ("runtimeGraphFingerprint" to graph.fingerprint()),
                actions = actions,
                resources = graph.toOpenApiResources(actions),
                handles = graph.handles.sortedBy { it.id }.map { it.toOpenApiHandle() },
                events = graph.events.sortedBy { it.id }.map { it.toOpenApiEvent() },
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
data class OpenApiTag(
    val name: String,
    val description: String,
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
    val summary: String? = null,
    val description: String? = null,
    val responses: Map<String, OpenApiResponse>,
    val requestBody: OpenApiRequestBody? = null,
    @SerialName("x-craftless-cli")
    val cli: OpenApiCliOperation? = null,
    @SerialName("x-craftless")
    val extensions: Map<String, String>,
)

@Serializable
data class OpenApiCliOperation(
    val command: List<String>,
    val aliases: List<List<String>> = emptyList(),
    val hidden: Boolean = false,
    val stream: Boolean = false,
    val body: OpenApiCliBody? = null,
)

@Serializable
data class OpenApiCliBody(
    val bindings: List<OpenApiCliBinding> = emptyList(),
)

@Serializable
data class OpenApiCliBinding(
    val pointer: String,
    val flag: String? = null,
    val argument: String? = null,
    val fixed: String? = null,
    val type: String = "string",
    val required: Boolean = false,
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
    @SerialName("enum")
    val enumValues: List<String>? = null,
    val default: String? = null,
    val properties: Map<String, OpenApiSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean? = null,
    val items: OpenApiSchema? = null,
    val nullable: Boolean? = null,
)

@Serializable
data class OpenApiAction(
    val id: String,
    val schemaVersion: String,
    @SerialName("args")
    val arguments: Map<String, OpenApiActionArgument> = emptyMap(),
    val result: OpenApiActionResult = OpenApiActionResult(),
    val source: OpenApiActionSource = OpenApiActionSource.BINDING,
    val availability: OpenApiActionAvailability = OpenApiActionAvailability.AVAILABLE,
    val availabilityReason: String? = null,
) {
    init {
        require(id.isCraftlessActionId()) { "invalid action id $id" }
        require(schemaVersion.isNotBlank()) { "action schema version is required" }
        require(availabilityReason == null || availabilityReason.isCraftlessActionArgumentName()) {
            "availability reason must be a machine-readable Craftless code"
        }
        if (availability == OpenApiActionAvailability.UNAVAILABLE) {
            require(!availabilityReason.isNullOrBlank()) { "unavailable action $id requires availability reason" }
        } else {
            require(availabilityReason == null) { "available action $id must not declare availability reason" }
        }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument name $name" }
        }
    }
}

@Serializable
data class OpenApiResource(
    val id: String,
    val actions: List<String>,
    val availability: OpenApiResourceAvailability,
    val availabilityReasons: List<String>,
    val actionDescriptors: List<OpenApiResourceActionDescriptor>,
    val schema: OpenApiActionSchema = OpenApiActionSchema("object"),
) {
    init {
        require(id.isCraftlessResourceId()) { "invalid resource id $id" }
        require(actions.distinct() == actions) { "resource $id declares duplicate actions" }
        require(availabilityReasons.distinct() == availabilityReasons) {
            "resource $id declares duplicate availability reasons"
        }
        availabilityReasons.forEach { reason ->
            require(reason.isCraftlessActionArgumentName()) { "invalid resource availability reason $reason" }
        }
        actions.forEach { actionId ->
            require(actionId.isCraftlessActionId()) { "invalid resource action id $actionId" }
            require(actionId.startsWith("$id.")) { "resource $id cannot reference action $actionId" }
        }
        require(actionDescriptors.map { it.id } == actions) {
            "resource $id action descriptors must match resource actions"
        }
    }
}

@Serializable
data class OpenApiEvent(
    val id: String,
    val payload: OpenApiActionSchema,
    val availability: OpenApiActionAvailability = OpenApiActionAvailability.AVAILABLE,
    val availabilityReason: String? = null,
) {
    init {
        require(id.isCraftlessActionId()) { "invalid event id $id" }
        require(availabilityReason == null || availabilityReason.isCraftlessActionArgumentName()) {
            "event availability reason must be a machine-readable Craftless code"
        }
        if (availability == OpenApiActionAvailability.UNAVAILABLE) {
            require(!availabilityReason.isNullOrBlank()) { "unavailable event $id requires availability reason" }
        } else {
            require(availabilityReason == null) { "available event $id must not declare availability reason" }
        }
    }
}

@Serializable
data class OpenApiHandle(
    val id: String,
    val resource: String,
    val schema: OpenApiActionSchema,
    val availability: OpenApiActionAvailability = OpenApiActionAvailability.AVAILABLE,
    val availabilityReason: String? = null,
) {
    init {
        require(id.isCraftlessActionId()) { "invalid handle id $id" }
        require(resource.isCraftlessResourceId()) { "invalid handle resource $resource" }
        require(id.startsWith("$resource.")) { "handle $id must belong to resource $resource" }
        require(availabilityReason == null || availabilityReason.isCraftlessActionArgumentName()) {
            "handle availability reason must be a machine-readable Craftless code"
        }
        if (availability == OpenApiActionAvailability.UNAVAILABLE) {
            require(!availabilityReason.isNullOrBlank()) { "unavailable handle $id requires availability reason" }
        } else {
            require(availabilityReason == null) { "available handle $id must not declare availability reason" }
        }
    }
}

@Serializable
data class OpenApiResourceActionDescriptor(
    val id: String,
    val schemaVersion: String,
    @SerialName("args")
    val arguments: Map<String, OpenApiActionArgument> = emptyMap(),
    val result: OpenApiActionResult = OpenApiActionResult(),
    val source: OpenApiActionSource = OpenApiActionSource.BINDING,
    val availability: OpenApiActionAvailability = OpenApiActionAvailability.AVAILABLE,
    val availabilityReason: String? = null,
) {
    init {
        require(id.isCraftlessActionId()) { "invalid resource action id $id" }
        require(schemaVersion.isNotBlank()) { "resource action schema version is required" }
        require(availabilityReason == null || availabilityReason.isCraftlessActionArgumentName()) {
            "resource action availability reason must be a machine-readable Craftless code"
        }
        if (availability == OpenApiActionAvailability.UNAVAILABLE) {
            require(!availabilityReason.isNullOrBlank()) { "unavailable resource action $id requires availability reason" }
        } else {
            require(availabilityReason == null) { "available resource action $id must not declare availability reason" }
        }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid resource action argument name $name" }
        }
    }
}

@Serializable
enum class OpenApiResourceAvailability {
    @SerialName("available")
    AVAILABLE,

    @SerialName("partial")
    PARTIAL,

    @SerialName("unavailable")
    UNAVAILABLE,
}

@Serializable
enum class OpenApiActionSource {
    @SerialName("binding")
    BINDING,

    @SerialName("runtime-probe")
    RUNTIME_PROBE,
}

@Serializable
enum class OpenApiActionAvailability {
    @SerialName("available")
    AVAILABLE,

    @SerialName("unavailable")
    UNAVAILABLE,
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
    val properties: Map<String, OpenApiActionSchema> = emptyMap(),
    val items: OpenApiActionSchema? = null,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action result property type $type" }
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action schema property $name" }
        }
    }
}

fun String.isCraftlessActionId(): Boolean =
    matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+")) &&
        !containsForbiddenPublicNamespaceToken() &&
        !isForbiddenPublicScenarioShortcutActionId()

fun String.isCraftlessResourceId(): Boolean =
    matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")) &&
        !containsForbiddenPublicNamespaceToken()

fun String.isCraftlessActionArgumentName(): Boolean =
    matches(Regex("[a-z][a-z0-9-]*")) &&
        !containsForbiddenPublicNamespaceToken()

@Serializable
data class OpenApiActionArgument(
    val type: String,
    val required: Boolean = false,
    val properties: Map<String, OpenApiActionArgument> = emptyMap(),
    val items: OpenApiActionArgument? = null,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action argument type $type" }
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument schema property $name" }
        }
    }
}

fun String.isCraftlessActionArgumentType(): Boolean = this in CRAFTLESS_ACTION_ARGUMENT_TYPES

private val CRAFTLESS_ACTION_ARGUMENT_TYPES =
    setOf("boolean", "integer", "number", "string", "object", "array")

private val FORBIDDEN_PUBLIC_NAMESPACE_TOKENS =
    setOf(
        "fabric",
        "yarn",
        "intermediary",
        "minecraft",
        "hmc",
        "headlessmc",
        "prism",
        "multimc",
        "mmc",
        "baritone",
        "swarmbot",
    )

private val FORBIDDEN_PUBLIC_SCENARIO_SHORTCUT_ACTION_IDS =
    setOf(
        "find.tree",
        "find.cow",
        "mine.log",
        "collect.wood",
        "craft.sword",
        "craft.planks",
        "craft.table",
        "make.weapon",
        "kill.cow",
        "hunt.animal",
        "pickup.log",
        "equip.log",
        "build.house",
        "place.log",
    )

internal fun String.containsForbiddenPublicNamespaceToken(): Boolean {
    val normalized = lowercase()
    return FORBIDDEN_PUBLIC_NAMESPACE_TOKENS.any { token -> token in normalized }
}

private fun String.isForbiddenPublicScenarioShortcutActionId(): Boolean = lowercase() in FORBIDDEN_PUBLIC_SCENARIO_SHORTCUT_ACTION_IDS

fun List<OpenApiAction>.toOpenApiResources(): List<OpenApiResource> =
    groupBy { it.resourceId() }
        .toSortedMap()
        .map { (resourceId, actions) ->
            val sortedActions = actions.sortedBy { it.id }
            OpenApiResource(
                id = resourceId,
                actions = sortedActions.map { it.id },
                availability = sortedActions.toResourceAvailability(),
                availabilityReasons = sortedActions.toResourceAvailabilityReasons(),
                actionDescriptors = sortedActions.map { it.toOpenApiResourceActionDescriptor() },
            )
        }

private fun RuntimeCapabilityGraph.toOpenApiResources(actions: List<OpenApiAction>): List<OpenApiResource> {
    val actionsByResource = actions.groupBy { it.resourceId() }
    val resourceIds = (resources.map { it.id } + actionsByResource.keys.sorted()).distinct()
    return resourceIds.map { resourceId ->
        val resourceNode = resources.firstOrNull { it.id == resourceId }
        val resourceActions = actionsByResource[resourceId].orEmpty().sortedBy { it.id }
        val availabilityReasons =
            (
                listOfNotNull(resourceNode?.availability?.reason) +
                    resourceActions.mapNotNull { it.availabilityReason }
            ).distinct().sorted()
        OpenApiResource(
            id = resourceId,
            actions = resourceActions.map { it.id },
            availability =
                when {
                    resourceNode?.availability?.state == RuntimeAvailabilityState.UNAVAILABLE ->
                        OpenApiResourceAvailability.UNAVAILABLE
                    resourceActions.isEmpty() -> OpenApiResourceAvailability.AVAILABLE
                    resourceActions.all { it.availability == OpenApiActionAvailability.AVAILABLE } ->
                        OpenApiResourceAvailability.AVAILABLE
                    resourceActions.all { it.availability == OpenApiActionAvailability.UNAVAILABLE } ->
                        OpenApiResourceAvailability.UNAVAILABLE
                    else -> OpenApiResourceAvailability.PARTIAL
                },
            availabilityReasons = availabilityReasons,
            actionDescriptors = resourceActions.map { it.toOpenApiResourceActionDescriptor() },
            schema = resourceNode?.schema?.toOpenApiActionSchema() ?: OpenApiActionSchema("object"),
        )
    }
}

private fun OpenApiAction.resourceId(): String = id.substringBeforeLast(".")

private fun OpenApiAction.toOpenApiResourceActionDescriptor(): OpenApiResourceActionDescriptor =
    OpenApiResourceActionDescriptor(
        id = id,
        schemaVersion = schemaVersion,
        arguments = arguments,
        result = result,
        source = source,
        availability = availability,
        availabilityReason = availabilityReason,
    )

private fun List<OpenApiAction>.toResourceAvailability(): OpenApiResourceAvailability =
    when {
        all { it.availability == OpenApiActionAvailability.AVAILABLE } -> OpenApiResourceAvailability.AVAILABLE
        all { it.availability == OpenApiActionAvailability.UNAVAILABLE } -> OpenApiResourceAvailability.UNAVAILABLE
        else -> OpenApiResourceAvailability.PARTIAL
    }

private fun List<OpenApiAction>.toResourceAvailabilityReasons(): List<String> =
    mapNotNull { it.availabilityReason }
        .distinct()
        .sorted()

private fun ApiRouteCatalog.toOpenApiTags(): List<OpenApiTag> =
    routes
        .map { it.tag }
        .distinct()
        .sorted()
        .map { tag ->
            OpenApiTag(
                name = tag,
                description = tag.descriptionForOpenApi(),
            )
        }

private fun String.descriptionForOpenApi(): String =
    when (this) {
        "openapi" ->
            "Machine contracts for the stable supervisor API and generated per-client APIs. Agents should fetch these documents before choosing routes, commands, actions, schemas, or stream contracts."
        "version" ->
            "Daemon, protocol, platform, and runtime identity. Use these operations to confirm which Craftless supervisor is answering before making live status or compatibility claims."
        "events" ->
            "Bounded event history and live Server-Sent Event streams for supervisor and client lifecycle evidence. Use events to correlate launch, attach, connect, discovery, invocation, and stop progress."
        "cache" ->
            "Craftless-owned cache preparation, export, and cleanup. These operations resolve Minecraft runtime inputs without exposing launcher internals as public API contracts."
        "runtimes" ->
            "Java runtime discovery and resolution for Minecraft launches. Use these operations to explain or select compatible Java runtimes through Craftless-owned surfaces."
        "clients" ->
            "Daemon-managed real Minecraft Java clients, including create, inspect, attach, connect, generated per-client OpenAPI, action/resource discovery, generic action invocation, RPC, events, and stop flows."
        else -> "Craftless-owned API operations for $this."
    }

private fun ApiRoute.toOperation(actionsById: Map<String, OpenApiAction>): OpenApiOperation {
    val route = this
    return OpenApiOperation(
        operationId = operationId,
        tags = listOf(tag),
        summary = summary,
        description = description,
        responses = route.responses(actionsById),
        requestBody = route.requestBody(actionsById),
        cli = route.cli?.toOpenApiCliOperation(),
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

private fun ApiRouteCli.toOpenApiCliOperation(): OpenApiCliOperation =
    OpenApiCliOperation(
        command = command,
        aliases = aliases,
        hidden = hidden,
        stream = stream,
        body =
            body
                .takeIf { it.isNotEmpty() }
                ?.let { bindings -> OpenApiCliBody(bindings.map { binding -> binding.toOpenApiCliBinding() }) },
    )

private fun ApiRouteCliBinding.toOpenApiCliBinding(): OpenApiCliBinding =
    OpenApiCliBinding(
        pointer = pointer,
        flag = flag,
        argument = argument,
        fixed = fixed,
        type = type,
        required = required,
    )

private fun RuntimeOperationNode.toOpenApiAction(): OpenApiAction =
    OpenApiAction(
        id = id,
        schemaVersion = "1",
        arguments = arguments.mapValues { (_, schema) -> schema.toOpenApiActionArgument() },
        result =
            OpenApiActionResult(
                properties =
                    mapOf(
                        "action" to OpenApiActionSchema("string"),
                        "status" to OpenApiActionSchema("string"),
                        "message" to OpenApiActionSchema("string"),
                        "data" to result.toOpenApiActionSchema(),
                    ),
                required = listOf("action", "status") + if (result.required) listOf("data") else emptyList(),
            ),
        source = OpenApiActionSource.RUNTIME_PROBE,
        availability = availability.toOpenApiActionAvailability(),
        availabilityReason = availability.reason,
    )

private fun RuntimeEventNode.toOpenApiEvent(): OpenApiEvent =
    OpenApiEvent(
        id = id,
        payload = payload.toOpenApiActionSchema(),
        availability = availability.toOpenApiActionAvailability(),
        availabilityReason = availability.reason,
    )

private fun RuntimeHandleNode.toOpenApiHandle(): OpenApiHandle =
    OpenApiHandle(
        id = id,
        resource = resource,
        schema = schema.toOpenApiActionSchema(),
        availability = availability.toOpenApiActionAvailability(),
        availabilityReason = availability.reason,
    )

private fun RuntimeAvailability.toOpenApiActionAvailability(): OpenApiActionAvailability =
    when (state) {
        RuntimeAvailabilityState.AVAILABLE -> OpenApiActionAvailability.AVAILABLE
        RuntimeAvailabilityState.UNAVAILABLE -> OpenApiActionAvailability.UNAVAILABLE
    }

private fun RuntimeOperationNode.toActionAliasRoute(): ApiRoute {
    val parts = id.split(".")
    val resourcePath = parts.dropLast(1).joinToString("/")
    val action = parts.last()
    return ApiRoute(
        method = "POST",
        path = "/clients/{id}/$resourcePath:$action",
        operationId = "run${parts.joinToString("") { it.toPascalIdentifier() }}",
        tag = "clients",
        owner = "clients",
        member = "run",
        target = "client",
        source = "action",
        actionId = id,
    )
}

private fun String.toPascalIdentifier(): String =
    split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

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
                path.endsWith("/resources") && method == "GET" -> resourceListResponse()
                path == "/cache:prepare" && method == "POST" -> cachePrepareResponse()
                path == "/cache:export" && method == "POST" -> cacheExportResponse()
                path == "/cache:cleanup" && method == "POST" -> cacheCleanupResponse()
                path == "/clients" && method == "GET" -> clientListResponse()
                path == "/clients" && method == "POST" -> clientResponse()
                path.endsWith(":attach") && method == "POST" -> clientResponse()
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
        path == "/cache:prepare" && method == "POST" -> listOf("400")
        path == "/cache:export" && method == "POST" -> listOf("400")
        path == "/cache:cleanup" && method == "POST" -> listOf("400")
        path.endsWith(":attach") && method == "POST" -> listOf("400", "404")
        path.endsWith(":connect") && method == "POST" -> listOf("400", "404", "409")
        source == "action" && method == "POST" -> listOf("400", "404", "409")
        path.endsWith(":run") && method == "POST" -> listOf("400", "404", "409")
        path.endsWith(":stop") && method == "POST" -> listOf("404")
        path == "/clients/{id}" && method == "GET" -> listOf("404")
        path == "/clients/{id}/openapi.json" && method == "GET" -> listOf("404")
        path == "/clients/{id}/actions" && method == "GET" -> listOf("404")
        path == "/clients/{id}/resources" && method == "GET" -> listOf("404")
        path == "/clients/{id}/events" && method == "GET" -> listOf("404")
        else -> emptyList()
    }

private fun ApiRoute.requestBody(actionsById: Map<String, OpenApiAction>): OpenApiRequestBody? =
    when {
        method != "POST" -> null
        actionId != null -> actionsById[actionId]?.arguments?.toRequestBody()
        path == "/cache:prepare" -> cachePrepareRequestBody()
        path == "/cache:export" -> cacheExportRequestBody()
        path == "/cache:cleanup" -> cacheCleanupRequestBody()
        path == "/clients" -> createClientRequestBody()
        path.endsWith(":attach") -> attachDriverRequestBody()
        path.endsWith(":connect") -> connectRequestBody()
        path.endsWith(":rpc") -> jsonRpcRequestBody()
        path.endsWith(":run") -> genericActionRequestBody()
        else -> null
    }

private fun Map<String, OpenApiActionArgument>.toRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties = mapValues { (_, argument) -> argument.toOpenApiSchema() },
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
                            "loader" to OpenApiSchema(type = "string", enumValues = Loader.entries.map { it.name }),
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

private fun resourceListResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "array",
                    items = resourceDescriptorSchema(),
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
                            "x-craftless-resources" to
                                OpenApiSchema(
                                    type = "array",
                                    items = resourceDescriptorSchema(),
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
                "source" to OpenApiSchema(type = "string"),
                "availability" to OpenApiSchema(type = "string"),
                "availabilityReason" to OpenApiSchema(type = "string", nullable = true),
                "args" to OpenApiSchema(type = "object", additionalProperties = true),
                "result" to OpenApiSchema(type = "object", additionalProperties = true),
            ),
        required = listOf("id", "schemaVersion", "source", "availability"),
    )

private fun resourceDescriptorSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "id" to OpenApiSchema(type = "string"),
                "actions" to OpenApiSchema(type = "array", items = OpenApiSchema(type = "string")),
                "availability" to OpenApiSchema(type = "string"),
                "availabilityReasons" to OpenApiSchema(type = "array", items = OpenApiSchema(type = "string")),
                "actionDescriptors" to
                    OpenApiSchema(
                        type = "array",
                        items = actionDescriptorSchema(),
                    ),
            ),
        required = listOf("id", "actions", "availability", "availabilityReasons", "actionDescriptors"),
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

private fun cachePrepareResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "minecraftVersion" to OpenApiSchema(type = "string"),
                            "loader" to OpenApiSchema(type = "string", enumValues = Loader.entries.map { it.name }),
                            "loaderVersion" to OpenApiSchema(type = "string", nullable = true),
                            "cacheRoot" to OpenApiSchema(type = "string"),
                            "minecraftVersionRoot" to OpenApiSchema(type = "string"),
                            "loaderRoot" to OpenApiSchema(type = "string"),
                            "runtimeRoot" to OpenApiSchema(type = "string"),
                            "manifest" to OpenApiSchema(type = "string"),
                            "status" to OpenApiSchema(type = "string"),
                            "artifacts" to
                                OpenApiSchema(
                                    type = "array",
                                    items =
                                        OpenApiSchema(
                                            type = "object",
                                            properties =
                                                mapOf(
                                                    "kind" to OpenApiSchema(type = "string"),
                                                    "handle" to OpenApiSchema(type = "string"),
                                                    "source" to OpenApiSchema(type = "string", nullable = true),
                                                    "status" to OpenApiSchema(type = "string"),
                                                ),
                                            required = listOf("kind", "handle", "status"),
                                        ),
                                ),
                            "launch" to
                                OpenApiSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "classpath" to
                                                OpenApiSchema(
                                                    type = "array",
                                                    items = OpenApiSchema(type = "string"),
                                                ),
                                            "nativePath" to
                                                OpenApiSchema(
                                                    type = "array",
                                                    items = OpenApiSchema(type = "string"),
                                                ),
                                            "javaExecutable" to OpenApiSchema(type = "string", nullable = true),
                                            "arguments" to OpenApiSchema(type = "string", nullable = true),
                                        ),
                                    required = listOf("classpath", "nativePath", "javaExecutable", "arguments"),
                                ),
                        ),
                    required =
                        listOf(
                            "minecraftVersion",
                            "loader",
                            "loaderVersion",
                            "cacheRoot",
                            "minecraftVersionRoot",
                            "loaderRoot",
                            "runtimeRoot",
                            "manifest",
                            "status",
                            "artifacts",
                            "launch",
                        ),
                ),
            ),
    )

private fun cacheExportResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "manifest" to OpenApiSchema(type = "string"),
                            "archive" to OpenApiSchema(type = "string"),
                            "included" to
                                OpenApiSchema(
                                    type = "array",
                                    items = OpenApiSchema(type = "string"),
                                ),
                            "status" to OpenApiSchema(type = "string"),
                        ),
                    required = listOf("manifest", "archive", "included", "status"),
                ),
            ),
    )

private fun cacheCleanupResponse(): OpenApiResponse =
    OpenApiResponse(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "manifest" to OpenApiSchema(type = "string"),
                            "deleted" to
                                OpenApiSchema(
                                    type = "array",
                                    items = OpenApiSchema(type = "string"),
                                ),
                            "missing" to
                                OpenApiSchema(
                                    type = "array",
                                    items = OpenApiSchema(type = "string"),
                                ),
                            "status" to OpenApiSchema(type = "string"),
                        ),
                    required = listOf("manifest", "deleted", "missing", "status"),
                ),
            ),
    )

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
                "presentation" to presentationSchema(),
                "state" to OpenApiSchema(type = "string"),
            ),
        required = listOf("id", "instance", "profile", "presentation", "state"),
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
                            "loader" to OpenApiSchema(type = "string", enumValues = Loader.entries.map { it.name }),
                            "loaderVersion" to OpenApiSchema(type = "string", nullable = true),
                            "profile" to profileSchema(),
                            "presentation" to presentationSchema(),
                        ),
                    required = listOf("id", "version", "loader"),
                ),
            ),
    )

private fun presentationSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties =
            mapOf(
                "window" to
                    OpenApiSchema(
                        type = "string",
                        enumValues = ClientWindowMode.entries.map { it.name },
                        default = ClientWindowMode.NONE.name,
                    ),
                "audio" to
                    OpenApiSchema(
                        type = "string",
                        enumValues = ClientAudioMode.entries.map { it.name },
                        default = ClientAudioMode.MUTED.name,
                    ),
            ),
        required = listOf("window", "audio"),
    )

private fun cachePrepareRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "minecraftVersion" to OpenApiSchema(type = "string"),
                            "loader" to OpenApiSchema(type = "string", enumValues = Loader.entries.map { it.name }),
                            "loaderVersion" to OpenApiSchema(type = "string", nullable = true),
                        ),
                    required = listOf("minecraftVersion", "loader"),
                ),
            ),
    )

private fun cacheExportRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "manifest" to OpenApiSchema(type = "string"),
                            "archive" to OpenApiSchema(type = "string", nullable = true),
                        ),
                    required = listOf("manifest"),
                ),
            ),
    )

private fun cacheCleanupRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "manifest" to OpenApiSchema(type = "string"),
                        ),
                    required = listOf("manifest"),
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

private fun attachDriverRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "endpoint" to OpenApiSchema(type = "string"),
                        ),
                    required = listOf("endpoint"),
                ),
            ),
    )

private fun jsonRpcRequestBody(): OpenApiRequestBody =
    OpenApiRequestBody(
        content =
            jsonContent(
                OpenApiSchema(
                    type = "object",
                    properties =
                        mapOf(
                            "method" to OpenApiSchema(type = "string", enumValues = JsonRpcMethod.allowed.toList()),
                            "params" to
                                OpenApiSchema(
                                    type = "object",
                                    properties =
                                        mapOf(
                                            "target" to OpenApiSchema(type = "string"),
                                        ),
                                ),
                        ),
                    required = listOf("method"),
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
                "runtimeRoot" to OpenApiSchema(type = "string"),
                "cache" to OpenApiSchema(type = "string"),
                "mods" to OpenApiSchema(type = "string"),
                "config" to OpenApiSchema(type = "string"),
                "saves" to OpenApiSchema(type = "string"),
                "resourcePacks" to OpenApiSchema(type = "string"),
                "shaderPacks" to OpenApiSchema(type = "string"),
                "screenshots" to OpenApiSchema(type = "string"),
                "logs" to OpenApiSchema(type = "string"),
                "artifacts" to OpenApiSchema(type = "string"),
            ),
        required =
            listOf(
                "root",
                "gameRoot",
                "runtimeRoot",
                "cache",
                "mods",
                "config",
                "saves",
                "resourcePacks",
                "shaderPacks",
                "screenshots",
                "logs",
                "artifacts",
            ),
    )

private fun jsonContent(schema: OpenApiSchema): Map<String, OpenApiMediaType> = mapOf("application/json" to OpenApiMediaType(schema))

private fun OpenApiActionResult.toOpenApiSchema(): OpenApiSchema =
    OpenApiSchema(
        type = "object",
        properties = properties.mapValues { (_, schema) -> schema.toOpenApiSchema() },
        required = required,
    )

private fun OpenApiActionSchema.toOpenApiSchema(): OpenApiSchema =
    OpenApiSchema(
        type = type,
        properties = properties.mapValues { (_, schema) -> schema.toOpenApiSchema() },
        items = items?.toOpenApiSchema(),
    )

private fun OpenApiActionArgument.toOpenApiSchema(): OpenApiSchema =
    OpenApiSchema(
        type = type,
        properties = properties.mapValues { (_, argument) -> argument.toOpenApiSchema() },
        required = properties.filterValues { argument -> argument.required }.keys.toList(),
        items = items?.toOpenApiSchema(),
    )

private fun RuntimeSchema.toOpenApiActionArgument(): OpenApiActionArgument =
    OpenApiActionArgument(
        type = type,
        required = required,
        properties = properties.mapValues { (_, schema) -> schema.toOpenApiActionArgument() },
        items = items?.toOpenApiActionArgument(),
    )

private fun RuntimeSchema.toOpenApiActionSchema(): OpenApiActionSchema =
    OpenApiActionSchema(
        type = type,
        properties = properties.mapValues { (_, schema) -> schema.toOpenApiActionSchema() },
        items = items?.toOpenApiActionSchema(),
    )

private fun defaultOpenApiActionResultProperties(): Map<String, OpenApiActionSchema> =
    mapOf(
        "action" to OpenApiActionSchema("string"),
        "status" to OpenApiActionSchema("string"),
        "message" to OpenApiActionSchema("string"),
        "data" to OpenApiActionSchema("object"),
    )
