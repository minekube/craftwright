package com.minekube.craftwright.daemon

import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverSession
import com.minekube.craftwright.driver.api.FakeDriverSession
import com.minekube.craftwright.protocol.ApiRoute
import com.minekube.craftwright.protocol.ApiRouteCatalog
import com.minekube.craftwright.protocol.Client
import com.minekube.craftwright.protocol.ClientState
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.Instance
import com.minekube.craftwright.protocol.MinecraftVersion
import com.minekube.craftwright.protocol.OpenApiCapability
import com.minekube.craftwright.protocol.OpenApiCapabilityArgument
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
        return listOf(
            route("GET", "/clients/$clientId/openapi.json", "clientsOpenApi", "clients", "openapi", "route"),
            route("POST", "/clients/$clientId/connection/connect", "clientsConnect", "clients", "connection", "method"),
            route("POST", "/clients/$clientId/stop", "clientsStop", "clients", "stop", "method"),
            route("GET", "/clients/$clientId/actions", "clientsActions", "clients", "actions", "action"),
            route("POST", "/clients/$clientId:run", "clientsRunAction", "clients", "run", "action"),
            route("GET", "/clients/$clientId/player", "clientsPlayer", "clients", "player", "root", "handle"),
            route("GET", "/clients/$clientId/player/position", "clientsPlayerPosition", "clients", "position", "getter"),
            route("GET", "/clients/$clientId/events", "clientsEvents", "clients", "events", "route"),
        )
    }

    fun openApiFor(clientId: String): OpenApiDocument {
        val client = client(clientId)
        val capabilities = driverFor(clientId).capabilities()
        return OpenApiDocument.from(
            catalog = ApiRouteCatalog(routesFor(clientId)),
            extensions = mapOf(
                "x-craftwright-client-id" to client.id,
                "x-craftwright-minecraft-version" to client.instance.version.id,
                "x-craftwright-loader" to client.instance.loader.name,
                "x-craftwright-driver" to "craftwright-daemon",
                "x-craftwright-capability-schema-version" to (capabilities.firstOrNull()?.schemaVersion ?: "none"),
                "x-craftwright-capability-fingerprint" to capabilities.joinToString(",") { "${it.id}:${it.schemaVersion}" },
            ),
            capabilities = capabilities.map { capability ->
                OpenApiCapability(
                    id = capability.id,
                    schemaVersion = capability.schemaVersion,
                    arguments = capability.arguments.mapValues { (_, argument) ->
                        OpenApiCapabilityArgument(
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
                    profileName = request.profile.name,
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

private fun route(
    method: String,
    path: String,
    operationId: String,
    tag: String,
    member: String,
    source: String,
    returnKind: String = "value",
): ApiRoute = ApiRoute(
    method = method,
    path = path,
    operationId = operationId,
    tag = tag,
    javaClass = "com.minekube.craftwright.client",
    javaMember = member,
    source = source,
    returnKind = returnKind,
)
