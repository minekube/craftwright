package dev.minekube.craftwright.daemon

import dev.minekube.craftwright.protocol.ApiRoute
import dev.minekube.craftwright.protocol.Client
import dev.minekube.craftwright.protocol.ClientState
import dev.minekube.craftwright.protocol.CreateClientRequest
import dev.minekube.craftwright.protocol.Instance
import dev.minekube.craftwright.protocol.MinecraftVersion

class ClientSessionService private constructor() {
    private val clients = linkedMapOf<String, Client>()

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
        return client
    }

    fun client(clientId: String): Client =
        clients[clientId] ?: error("client $clientId not found")

    fun connectClient(clientId: String): Client =
        updateState(clientId, ClientState.CONNECTED)

    fun stopClient(clientId: String): Client =
        updateState(clientId, ClientState.STOPPED)

    fun routesFor(clientId: String): List<ApiRoute> {
        require(clients.containsKey(clientId)) { "client $clientId not found" }
        return listOf(
            route("POST", "/clients/$clientId/connection/connect", "clientsConnect", "clients", "connection", "method"),
            route("POST", "/clients/$clientId/stop", "clientsStop", "clients", "stop", "method"),
            route("POST", "/clients/$clientId/player/sendChat", "clientsPlayerSendChat", "clients", "sendChat", "method"),
            route("POST", "/clients/$clientId/actions/move", "clientsActionsMove", "clients", "move", "method"),
            route("POST", "/clients/$clientId/actions/jump", "clientsActionsJump", "clients", "jump", "method"),
            route("POST", "/clients/$clientId/actions/look", "clientsActionsLook", "clients", "look", "method"),
            route("GET", "/clients/$clientId/player", "clientsPlayer", "clients", "player", "root", "handle"),
            route("GET", "/clients/$clientId/player/position", "clientsPlayerPosition", "clients", "position", "getter"),
            route("GET", "/clients/$clientId/perception/raycast", "clientsPerceptionRaycast", "clients", "raycast", "method"),
            route("GET", "/clients/$clientId/world/blocks/nearby", "clientsWorldBlocksNearby", "clients", "nearbyBlocks", "method"),
            route("GET", "/clients/$clientId/entities/nearby", "clientsEntitiesNearby", "clients", "nearbyEntities", "method"),
            route("GET", "/clients/$clientId/events", "clientsEvents", "clients", "events", "route"),
        )
    }

    companion object {
        fun inMemory(): ClientSessionService = ClientSessionService()
    }

    private fun updateState(clientId: String, state: ClientState): Client {
        val updated = client(clientId).copy(state = state)
        clients[clientId] = updated
        return updated
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
): ApiRoute = ApiRoute(
    method = method,
    path = path,
    operationId = operationId,
    tag = tag,
    javaClass = "dev.minekube.craftwright.client",
    javaMember = member,
    source = source,
    returnKind = returnKind,
)
