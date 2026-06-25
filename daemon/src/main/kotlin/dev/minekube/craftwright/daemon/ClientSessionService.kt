package dev.minekube.craftwright.daemon

import dev.minekube.craftwright.protocol.ApiRoute
import dev.minekube.craftwright.protocol.Client
import dev.minekube.craftwright.protocol.ClientState
import dev.minekube.craftwright.protocol.CreateClientRequest
import dev.minekube.craftwright.protocol.HttpMethod
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

    fun routesFor(clientId: String): List<ApiRoute> {
        require(clients.containsKey(clientId)) { "client $clientId not found" }
        return listOf(
            route(HttpMethod.POST, "/clients/$clientId/connect", "clientsConnect", "clients", "connection", "method"),
            route(HttpMethod.POST, "/clients/$clientId/stop", "clientsStop", "clients", "stop", "method"),
            route(HttpMethod.POST, "/clients/$clientId/actions/chat", "clientsActionsChat", "clients", "chat", "method"),
            route(HttpMethod.POST, "/clients/$clientId/actions/move", "clientsActionsMove", "clients", "move", "method"),
            route(HttpMethod.POST, "/clients/$clientId/actions/jump", "clientsActionsJump", "clients", "jump", "method"),
            route(HttpMethod.POST, "/clients/$clientId/actions/look", "clientsActionsLook", "clients", "look", "method"),
            route(HttpMethod.GET, "/clients/$clientId/player", "clientsPlayer", "clients", "player", "root", "handle"),
            route(HttpMethod.GET, "/clients/$clientId/player/position", "clientsPlayerPosition", "clients", "position", "getter"),
            route(HttpMethod.GET, "/clients/$clientId/perception/raycast", "clientsPerceptionRaycast", "clients", "raycast", "method"),
            route(HttpMethod.GET, "/clients/$clientId/world/blocks/nearby", "clientsWorldBlocksNearby", "clients", "nearbyBlocks", "method"),
            route(HttpMethod.GET, "/clients/$clientId/entities/nearby", "clientsEntitiesNearby", "clients", "nearbyEntities", "method"),
            route(HttpMethod.GET, "/clients/$clientId/events", "clientsEvents", "clients", "events", "route"),
        )
    }

    companion object {
        fun inMemory(): ClientSessionService = ClientSessionService()
    }
}

private fun route(
    method: HttpMethod,
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
