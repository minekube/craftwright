package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.driver.api.FakeDriverSession
import com.minekube.craftless.protocol.ApiRoute
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Instance
import com.minekube.craftless.protocol.MinecraftVersion
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiActionArgument
import com.minekube.craftless.protocol.OpenApiDocument

class ClientSessionService private constructor(
    private val driverFactory: DriverSessionFactory,
) {
    private val clients = linkedMapOf<String, Client>()
    private val drivers = linkedMapOf<String, DriverSession>()

    fun createClient(request: CreateClientRequest): Client {
        require(request.id.isCraftlessClientId()) { "client id must be a route-safe segment" }
        require(request.version.isNotBlank()) { "minecraft version is required" }
        require(request.profile.name.length <= 16) { "offline profile name must be 16 characters or fewer" }
        require(!clients.containsKey(request.id)) { "client ${request.id} already exists" }

        val instance = Instance(
            id = "${request.id}-${request.version}-${request.loader.name.lowercase()}",
            version = MinecraftVersion(request.version),
            loader = request.loader,
        )
        val client = Client(
            id = request.id,
            instance = instance,
            profile = request.profile,
            state = ClientState.RUNNING,
        )
        clients[request.id] = client
        drivers[request.id] = driverFactory.create(request)
        return client
    }

    fun listClients(): List<Client> =
        clients.values.toList()

    fun hasClient(clientId: String): Boolean =
        clients.containsKey(clientId)

    fun client(clientId: String): Client =
        clients[clientId] ?: error("client $clientId not found")

    fun driverFor(clientId: String): DriverSession =
        drivers[clientId] ?: error("client $clientId not found")

    fun connectClient(clientId: String, target: ConnectionTarget): Client {
        val snapshot = driverFor(clientId).connect(target)
        return updateState(clientId, snapshot.state)
    }

    fun stopClient(clientId: String): Client {
        val snapshot = driverFor(clientId).stop()
        return updateState(clientId, snapshot.state)
    }

    fun routesFor(clientId: String): List<ApiRoute> {
        require(clients.containsKey(clientId)) { "client $clientId not found" }
        val actionAliases = driverFor(clientId).sortedActions().mapNotNull { it.toActionAliasRoute(clientId) }
        return listOf(
            route("GET", "/clients/$clientId", "getClient", "clients", "get", "route"),
            route("GET", "/clients/$clientId/openapi.json", "getClientOpenapiJson", "clients", "openapi", "route"),
            route("POST", "/clients/$clientId:connect", "clientConnect", "clients", "connect", "method"),
            route("POST", "/clients/$clientId:stop", "stopClient", "clients", "stop", "method"),
            route("GET", "/clients/$clientId/actions", "listClientActions", "clients", "actions", "action"),
            route("POST", "/clients/$clientId:run", "runClientAction", "clients", "run", "action"),
            route("GET", "/clients/$clientId/events", "getClientEvents", "clients", "events", "route"),
        ) + actionAliases
    }

    fun openApiFor(clientId: String): OpenApiDocument {
        val client = client(clientId)
        val driver = driverFor(clientId)
        val actions = driver.sortedActions()
        val runtimeMetadata = RuntimeOpenApiMetadata.forClient(
            client = client,
            actions = actions,
            metadata = driver.runtimeMetadata(),
        )
        return OpenApiDocument.from(
            catalog = ApiRouteCatalog(routesFor(clientId)),
            extensions = runtimeMetadata.extensions,
            actions = actions.map { action ->
                OpenApiAction(
                    id = action.id,
                    schemaVersion = action.schemaVersion,
                    arguments = action.arguments.mapValues { (_, argument) ->
                        OpenApiActionArgument(
                            type = argument.type,
                            required = argument.required,
                        )
                    },
                )
            },
        )
    }

    companion object {
        fun inMemory(
            driverFactory: DriverSessionFactory = DriverSessionFactory { request ->
                FakeDriverSession(
                    clientId = request.id,
                )
            },
        ): ClientSessionService = ClientSessionService(driverFactory)
    }

    private fun updateState(clientId: String, state: ClientState): Client {
        val updated = client(clientId).copy(state = state)
        clients[clientId] = updated
        return updated
    }
}

private fun DriverSession.sortedActions(): List<DriverActionDescriptor> {
    val actions = actions()
    val duplicateAction = actions
        .groupBy { it.id }
        .entries
        .firstOrNull { (_, matches) -> matches.size > 1 }
    if (duplicateAction != null) {
        throw IllegalArgumentException("duplicate action id ${duplicateAction.key}")
    }
    return actions.sortedBy { it.id }
}

private fun String.isCraftlessClientId(): Boolean =
    matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))

fun interface DriverSessionFactory {
    fun create(request: CreateClientRequest): DriverSession
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
            val actionSchemaVersions = actions
                .map { it.schemaVersion }
                .distinct()
                .sorted()
                .ifEmpty { listOf("none") }
                .joinToString(",")
            val extensions = linkedMapOf(
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

        private fun runtimeFingerprint(
            client: Client,
            metadata: DriverRuntimeMetadata,
            actionFingerprint: String,
        ): String = listOf(
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
    val argumentFingerprint = arguments.entries
        .sortedBy { it.key }
        .joinToString(",") { (name, argument) ->
            val required = if (argument.required) "!" else ""
            "$name:${argument.type}$required"
        }
    return "$id:$schemaVersion($argumentFingerprint)"
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
): ApiRoute = ApiRoute(
    method = method,
    path = path,
    operationId = operationId,
    tag = tag,
    owner = "clients",
    member = member,
    target = if (source == "action") "client" else "supervisor",
    source = source,
    returnKind = returnKind,
    actionId = actionId,
)

private fun DriverActionDescriptor.toActionAliasRoute(clientId: String): ApiRoute? {
    val parts = id.split(".")
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    val (resource, action) = parts
    return route(
        method = "POST",
        path = "/clients/$clientId/$resource:$action",
        operationId = "run${resource.toPascalIdentifier()}${action.toPascalIdentifier()}",
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
