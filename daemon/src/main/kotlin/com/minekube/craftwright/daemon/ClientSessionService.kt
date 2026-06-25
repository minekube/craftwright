package com.minekube.craftwright.daemon

import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverActionDescriptor
import com.minekube.craftwright.driver.api.DriverRuntimeMetadata
import com.minekube.craftwright.driver.api.DriverSession
import com.minekube.craftwright.driver.api.FakeDriverSession
import com.minekube.craftwright.protocol.ApiRoute
import com.minekube.craftwright.protocol.ApiRouteCatalog
import com.minekube.craftwright.protocol.Client
import com.minekube.craftwright.protocol.ClientState
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.Instance
import com.minekube.craftwright.protocol.MinecraftVersion
import com.minekube.craftwright.protocol.OpenApiAction
import com.minekube.craftwright.protocol.OpenApiActionArgument
import com.minekube.craftwright.protocol.OpenApiDocument

class ClientSessionService private constructor(
    private val driverFactory: DriverSessionFactory,
) {
    private val clients = linkedMapOf<String, Client>()
    private val drivers = linkedMapOf<String, DriverSession>()

    fun createClient(request: CreateClientRequest): Client {
        require(request.id.isNotBlank()) { "client id is required" }
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
        val actionAliases = driverFor(clientId).actions().mapNotNull { it.toActionAliasRoute(clientId) }
        return listOf(
            route("GET", "/clients/$clientId", "clientsGet", "clients", "get", "route"),
            route("GET", "/clients/$clientId/openapi.json", "clientsOpenApi", "clients", "openapi", "route"),
            route("POST", "/clients/$clientId:connect", "clientsConnect", "clients", "connect", "method"),
            route("POST", "/clients/$clientId/stop", "clientsStop", "clients", "stop", "method"),
            route("GET", "/clients/$clientId/actions", "clientsActions", "clients", "actions", "action"),
            route("POST", "/clients/$clientId:run", "clientsRunAction", "clients", "run", "action"),
            route("GET", "/clients/$clientId/events", "clientsEvents", "clients", "events", "route"),
        ) + actionAliases
    }

    fun openApiFor(clientId: String): OpenApiDocument {
        val client = client(clientId)
        val driver = driverFor(clientId)
        val actions = driver.actions()
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
            val actionFingerprint = actions.joinToString(",") { "${it.id}:${it.schemaVersion}" }
            val extensions = linkedMapOf(
                "x-craftwright-client-id" to client.id,
                "x-craftwright-minecraft-version" to client.instance.version.id,
                "x-craftwright-loader" to client.instance.loader.name,
                "x-craftwright-loader-version" to metadata.loaderVersion,
                "x-craftwright-driver" to metadata.driver,
                "x-craftwright-driver-version" to metadata.driverVersion,
                "x-craftwright-mappings" to metadata.mappings,
                "x-craftwright-installed-mods-fingerprint" to metadata.installedModsFingerprint,
                "x-craftwright-registry-fingerprint" to metadata.registryFingerprint,
                "x-craftwright-server-feature-fingerprint" to metadata.serverFeatureFingerprint,
                "x-craftwright-permissions-fingerprint" to metadata.permissionsFingerprint,
                "x-craftwright-action-schema-version" to (actions.firstOrNull()?.schemaVersion ?: "none"),
                "x-craftwright-action-fingerprint" to actionFingerprint,
            )
            extensions["x-craftwright-runtime-fingerprint"] = runtimeFingerprint(client, metadata, actionFingerprint)
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
    javaClass = "com.minekube.craftwright.client",
    javaMember = member,
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
