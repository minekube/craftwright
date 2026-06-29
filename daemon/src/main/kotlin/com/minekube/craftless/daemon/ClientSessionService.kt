package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ApiRoute
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Instance
import com.minekube.craftless.protocol.MAX_OFFLINE_PROFILE_NAME_LENGTH
import com.minekube.craftless.protocol.MinecraftVersion
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiOperation
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
        val profile = request.resolvedProfile()
        require(profile.name.length <= MAX_OFFLINE_PROFILE_NAME_LENGTH) { "offline profile name must be 16 characters or fewer" }
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
                profile = profile,
                presentation = request.presentation,
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

    fun attachDriver(
        clientId: String,
        driver: DriverSession,
    ): Client {
        require(clients.containsKey(clientId)) { "client $clientId not found" }
        require(driver.clientId == clientId) { "attached driver client id must match $clientId" }
        drivers[clientId] = driver
        return updateState(clientId, driver.snapshot().state)
    }

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
        return openApiFor(clientId).toApiRoutes(clientId)
    }

    fun resourcesFor(clientId: String): List<OpenApiResource> = openApiFor(clientId).resources

    fun openApiFor(clientId: String): OpenApiDocument {
        val client = client(clientId)
        val driver = driverFor(clientId)
        val graph = driver.runtimeGraph()
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

private fun OpenApiDocument.withConcreteClientId(clientId: String): OpenApiDocument =
    copy(
        paths =
            paths.mapKeys { (path, _) ->
                path.replace("/clients/{id}", "/clients/$clientId")
            },
    )

private fun OpenApiDocument.toApiRoutes(clientId: String): List<ApiRoute> {
    val prefix = "/clients/$clientId"
    return paths
        .filterKeys { path -> path == prefix || path.startsWith("$prefix/") || path.startsWith("$prefix:") }
        .flatMap { (path, operations) ->
            listOfNotNull(
                operations.get?.toApiRoute(method = "GET", path = path),
                operations.post?.toApiRoute(method = "POST", path = path),
            )
        }
}

private fun OpenApiOperation.toApiRoute(
    method: String,
    path: String,
): ApiRoute {
    val owner = requireNotNull(extensions["x-craftless-owner"]) { "openapi operation $operationId is missing owner metadata" }
    val target = requireNotNull(extensions["x-craftless-target"]) { "openapi operation $operationId is missing target metadata" }
    val source = requireNotNull(extensions["x-craftless-source"]) { "openapi operation $operationId is missing source metadata" }
    val returnKind = requireNotNull(extensions["x-craftless-return"]) { "openapi operation $operationId is missing return metadata" }
    return ApiRoute(
        method = method,
        path = path,
        operationId = operationId,
        tag = tags.firstOrNull() ?: owner,
        owner = owner,
        member = extensions["x-craftless-member"],
        target = target,
        source = source,
        returnKind = returnKind,
        actionId = extensions["x-craftless-action"],
    )
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
    }
}
