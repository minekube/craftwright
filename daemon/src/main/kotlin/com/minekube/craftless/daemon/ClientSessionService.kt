package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ApiRoute
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Instance
import com.minekube.craftless.protocol.MinecraftVersion
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiActionArgument
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiActionResult
import com.minekube.craftless.protocol.OpenApiActionSchema
import com.minekube.craftless.protocol.OpenApiActionSource
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiResource
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.isCraftlessClientId

class ClientSessionService private constructor(
    private val driverFactory: DriverSessionFactory,
    private val fileStore: InstanceFileStore?,
) {
    private val clients = linkedMapOf<String, Client>()
    private val drivers = linkedMapOf<String, DriverSession>()

    fun createClient(request: CreateClientRequest): Client {
        require(request.id.isCraftlessClientId()) { "client id must be a route-safe segment" }
        require(request.version.isNotBlank()) { "minecraft version is required" }
        require(request.profile.name.length <= 16) { "offline profile name must be 16 characters or fewer" }
        require(!clients.containsKey(request.id)) { "client ${request.id} already exists" }

        val instance =
            Instance(
                id = "${request.id}-${request.version}-${request.loader.name.lowercase()}",
                version = MinecraftVersion(request.version),
                loader = request.loader,
            )
        fileStore?.prepare(instance.files)
        val client =
            Client(
                id = request.id,
                instance = instance,
                profile = request.profile,
                state = ClientState.RUNNING,
            )
        val driver = driverFactory.create(request)
        clients[request.id] = client
        drivers[request.id] = driver
        return client
    }

    fun listClients(): List<Client> = clients.values.toList()

    fun client(clientId: String): Client = clients[clientId] ?: error("client $clientId not found")

    fun driverFor(clientId: String): DriverSession = drivers[clientId] ?: error("client $clientId not found")

    fun connectClient(
        clientId: String,
        target: ConnectionTarget,
    ): Client {
        val snapshot = driverFor(clientId).connect(target)
        return updateState(clientId, snapshot.state)
    }

    fun stopClient(clientId: String): Client {
        val snapshot = driverFor(clientId).stop()
        return updateState(clientId, snapshot.state)
    }

    fun routesFor(clientId: String): List<ApiRoute> {
        require(clients.containsKey(clientId)) { "client $clientId not found" }
        return routesFor(clientId, driverFor(clientId).sortedActions())
    }

    fun resourcesFor(clientId: String): List<OpenApiResource> = openApiFor(clientId).resources

    fun openApiFor(clientId: String): OpenApiDocument {
        val client = client(clientId)
        val driver = driverFor(clientId)
        val graph = driver.runtimeGraph()
        if (graph.hasProjectionNodes()) {
            val metadata =
                RuntimeOpenApiMetadata.forGraph(
                    client = client,
                    graph = graph,
                    metadata = driver.runtimeMetadata(),
                )
            return OpenApiDocument
                .fromRuntimeGraph(
                    graph = graph,
                    extensions = metadata.extensions,
                ).withConcreteClientId(clientId)
        }
        val actions = driver.sortedActions()
        val runtimeMetadata =
            RuntimeOpenApiMetadata.forClient(
                client = client,
                actions = actions,
                metadata = driver.runtimeMetadata(),
            )
        return OpenApiDocument.from(
            catalog = ApiRouteCatalog(routesFor(clientId, actions)),
            extensions = runtimeMetadata.extensions,
            actions =
                actions.map { action ->
                    OpenApiAction(
                        id = action.id,
                        schemaVersion = action.schemaVersion,
                        arguments =
                            action.arguments.mapValues { (_, argument) ->
                                argument.toOpenApiActionArgument()
                            },
                        result =
                            OpenApiActionResult(
                                properties =
                                    action.result.properties.mapValues { (_, property) ->
                                        property.toOpenApiActionSchema()
                                    },
                                required = action.result.required,
                            ),
                        source = action.source.toOpenApiActionSource(),
                        availability = action.availability.toOpenApiActionAvailability(),
                        availabilityReason = action.availabilityReason,
                    )
                },
        )
    }

    private fun routesFor(
        clientId: String,
        actions: List<DriverActionDescriptor>,
    ): List<ApiRoute> {
        val actionAliases = actions.mapNotNull { it.toActionAliasRoute(clientId) }
        return listOf(
            route("GET", "/clients/$clientId", "getClient", "clients", "get", "route"),
            route("GET", "/clients/$clientId/openapi.json", "getClientOpenapiJson", "clients", "openapi", "route"),
            route("POST", "/clients/$clientId:connect", "clientConnect", "clients", "connect", "method"),
            route("POST", "/clients/$clientId:stop", "stopClient", "clients", "stop", "method"),
            route("GET", "/clients/$clientId/actions", "listClientActions", "clients", "actions", "action"),
            route("GET", "/clients/$clientId/resources", "listClientResources", "clients", "resources", "resource"),
            route("POST", "/clients/$clientId:run", "runClientAction", "clients", "run", "action"),
            route("GET", "/clients/$clientId/events", "getClientEvents", "clients", "events", "route"),
            route("GET", "/clients/$clientId/events:stream", "streamClientEvents", "clients", "events", "route"),
        ) + actionAliases
    }

    companion object {
        fun inMemory(driverFactory: DriverSessionFactory): ClientSessionService = ClientSessionService(driverFactory, fileStore = null)

        fun inMemory(
            driverFactory: DriverSessionFactory = DriverSessionFactory.unavailable(),
            fileStore: InstanceFileStore? = null,
        ): ClientSessionService = ClientSessionService(driverFactory, fileStore)
    }

    private fun updateState(
        clientId: String,
        state: ClientState,
    ): Client {
        val updated = client(clientId).copy(state = state)
        clients[clientId] = updated
        return updated
    }
}

private fun RuntimeCapabilityGraph.hasProjectionNodes(): Boolean =
    resources.isNotEmpty() || operations.isNotEmpty() || handles.isNotEmpty() || events.isNotEmpty()

private fun OpenApiDocument.withConcreteClientId(clientId: String): OpenApiDocument =
    copy(
        paths =
            paths.mapKeys { (path, _) ->
                path.replace("/clients/{id}", "/clients/$clientId")
            },
    )

private fun DriverSession.sortedActions(): List<DriverActionDescriptor> {
    val actions = actions()
    val duplicateAction =
        actions
            .groupBy { it.id }
            .entries
            .firstOrNull { (_, matches) -> matches.size > 1 }
    if (duplicateAction != null) {
        throw IllegalArgumentException("duplicate action id ${duplicateAction.key}")
    }
    return actions.sortedBy { it.id }
}

fun interface DriverSessionFactory {
    fun create(request: CreateClientRequest): DriverSession

    companion object {
        fun unavailable(): DriverSessionFactory =
            DriverSessionFactory { request ->
                error("no Craftless driver runtime configured for client ${request.id}")
            }
    }
}

private data class RuntimeOpenApiMetadata(
    val extensions: Map<String, String>,
) {
    companion object {
        fun forClient(
            client: Client,
            actions: List<DriverActionDescriptor>,
            metadata: DriverRuntimeMetadata,
        ): RuntimeOpenApiMetadata {
            val actionFingerprint = actions.joinToString(",") { it.fingerprintPart() }
            val actionSchemaVersions =
                actions
                    .map { it.schemaVersion }
                    .distinct()
                    .sorted()
                    .ifEmpty { listOf("none") }
                    .joinToString(",")
            val extensions =
                linkedMapOf(
                    "x-craftless-client-id" to client.id,
                    "x-craftless-minecraft-version" to client.instance.version.id,
                    "x-craftless-loader" to client.instance.loader.name,
                    "x-craftless-loader-version" to metadata.loaderVersion,
                    "x-craftless-driver" to metadata.driver,
                    "x-craftless-driver-version" to metadata.driverVersion,
                    "x-craftless-mappings-fingerprint" to metadata.mappings,
                    "x-craftless-installed-mods-fingerprint" to metadata.installedModsFingerprint,
                    "x-craftless-registry-fingerprint" to metadata.registryFingerprint,
                    "x-craftless-server-feature-fingerprint" to metadata.serverFeatureFingerprint,
                    "x-craftless-permissions-fingerprint" to metadata.permissionsFingerprint,
                    "x-craftless-action-schema-versions" to actionSchemaVersions,
                    "x-craftless-action-fingerprint" to actionFingerprint,
                )
            extensions["x-craftless-runtime-fingerprint"] = runtimeFingerprint(client, metadata, actionFingerprint)
            return RuntimeOpenApiMetadata(extensions)
        }

        fun forGraph(
            client: Client,
            graph: RuntimeCapabilityGraph,
            metadata: DriverRuntimeMetadata,
        ): RuntimeOpenApiMetadata {
            val graphFingerprint = graph.fingerprint()
            val extensions =
                linkedMapOf(
                    "x-craftless-client-id" to client.id,
                    "x-craftless-minecraft-version" to client.instance.version.id,
                    "x-craftless-loader" to client.instance.loader.name,
                    "x-craftless-loader-version" to metadata.loaderVersion,
                    "x-craftless-driver" to metadata.driver,
                    "x-craftless-driver-version" to metadata.driverVersion,
                    "x-craftless-mappings-fingerprint" to metadata.mappings,
                    "x-craftless-installed-mods-fingerprint" to metadata.installedModsFingerprint,
                    "x-craftless-registry-fingerprint" to metadata.registryFingerprint,
                    "x-craftless-server-feature-fingerprint" to metadata.serverFeatureFingerprint,
                    "x-craftless-permissions-fingerprint" to metadata.permissionsFingerprint,
                    "x-craftless-action-schema-versions" to graphFingerprint,
                    "x-craftless-action-fingerprint" to graphFingerprint,
                    "runtimeGraphFingerprint" to graphFingerprint,
                    "x-craftless-runtime-fingerprint" to graphFingerprint,
                )
            return RuntimeOpenApiMetadata(extensions)
        }

        private fun runtimeFingerprint(
            client: Client,
            metadata: DriverRuntimeMetadata,
            actionFingerprint: String,
        ): String =
            listOf(
                "minecraft=${client.instance.version.id}",
                "loader=${client.instance.loader.name}",
                "loaderVersion=${metadata.loaderVersion}",
                "driver=${metadata.driver}",
                "driverVersion=${metadata.driverVersion}",
                "mappings=${metadata.mappings}",
                "mods=${metadata.installedModsFingerprint}",
                "registries=${metadata.registryFingerprint}",
                "serverFeatures=${metadata.serverFeatureFingerprint}",
                "permissions=${metadata.permissionsFingerprint}",
                "actions=$actionFingerprint",
            ).joinToString(";")
    }
}

private fun DriverActionDescriptor.fingerprintPart(): String {
    val argumentFingerprint =
        arguments.entries
            .sortedBy { it.key }
            .joinToString(",") { (name, argument) ->
                val required = if (argument.required) "!" else ""
                "$name:${argument.fingerprintPart()}$required"
            }
    val resultFingerprint =
        result.properties.entries
            .sortedBy { it.key }
            .joinToString(",") { (name, property) ->
                val required = if (name in result.required) "!" else ""
                "$name:${property.fingerprintPart()}$required"
            }
    val availabilityFingerprint =
        when (availability) {
            DriverActionAvailability.AVAILABLE -> availability.fingerprintValue()
            DriverActionAvailability.UNAVAILABLE -> "${availability.fingerprintValue()}:${availabilityReason.orEmpty()}"
        }
    return "$id:$schemaVersion:${source.fingerprintValue()}:$availabilityFingerprint($argumentFingerprint)->($resultFingerprint)"
}

private fun DriverActionResultProperty.toOpenApiActionSchema(): OpenApiActionSchema =
    OpenApiActionSchema(
        type = type,
        properties = properties.mapValues { (_, property) -> property.toOpenApiActionSchema() },
        items = items?.toOpenApiActionSchema(),
    )

private fun DriverActionArgument.toOpenApiActionArgument(): OpenApiActionArgument =
    OpenApiActionArgument(
        type = type,
        required = required,
        properties = properties.mapValues { (_, argument) -> argument.toOpenApiActionArgument() },
        items = items?.toOpenApiActionArgument(),
    )

private fun DriverActionArgument.fingerprintPart(): String {
    if (properties.isEmpty() && items == null) {
        return type
    }
    val propertyFingerprint =
        properties.entries
            .sortedBy { it.key }
            .joinToString(",") { (name, argument) ->
                val required = if (argument.required) "!" else ""
                "$name=${argument.fingerprintPart()}$required"
            }
    val itemFingerprint = items?.fingerprintPart().orEmpty()
    return "$type:{$propertyFingerprint}:[$itemFingerprint]"
}

private fun DriverActionResultProperty.fingerprintPart(): String {
    if (properties.isEmpty() && items == null) {
        return type
    }
    val propertyFingerprint =
        properties.entries
            .sortedBy { it.key }
            .joinToString(",") { (name, property) -> "$name=${property.fingerprintPart()}" }
    val itemFingerprint = items?.fingerprintPart().orEmpty()
    return "$type:{$propertyFingerprint}:[$itemFingerprint]"
}

private fun DriverActionSource.fingerprintValue(): String =
    when (this) {
        DriverActionSource.BINDING -> "binding"
        DriverActionSource.RUNTIME_PROBE -> "runtime-probe"
    }

private fun DriverActionAvailability.fingerprintValue(): String =
    when (this) {
        DriverActionAvailability.AVAILABLE -> "available"
        DriverActionAvailability.UNAVAILABLE -> "unavailable"
    }

private fun DriverActionSource.toOpenApiActionSource(): OpenApiActionSource =
    when (this) {
        DriverActionSource.BINDING -> OpenApiActionSource.BINDING
        DriverActionSource.RUNTIME_PROBE -> OpenApiActionSource.RUNTIME_PROBE
    }

private fun DriverActionAvailability.toOpenApiActionAvailability(): OpenApiActionAvailability =
    when (this) {
        DriverActionAvailability.AVAILABLE -> OpenApiActionAvailability.AVAILABLE
        DriverActionAvailability.UNAVAILABLE -> OpenApiActionAvailability.UNAVAILABLE
    }

private fun route(
    method: String,
    path: String,
    operationId: String,
    tag: String,
    member: String,
    source: String,
    returnKind: String = "value",
    actionId: String? = null,
): ApiRoute =
    ApiRoute(
        method = method,
        path = path,
        operationId = operationId,
        tag = tag,
        owner = "clients",
        member = member,
        target = if (source == "action" || source == "resource") "client" else "supervisor",
        source = source,
        returnKind = returnKind,
        actionId = actionId,
    )

private fun DriverActionDescriptor.toActionAliasRoute(clientId: String): ApiRoute? {
    val parts = id.split(".")
    if (parts.size < 2 || parts.any { it.isBlank() }) return null
    val resource = parts.dropLast(1).joinToString("/")
    val action = parts.last()
    return route(
        method = "POST",
        path = "/clients/$clientId/$resource:$action",
        operationId = "run${parts.joinToString("") { it.toPascalIdentifier() }}",
        tag = "clients",
        member = "run",
        source = "action",
        actionId = id,
    )
}

private fun String.toPascalIdentifier(): String =
    split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }
