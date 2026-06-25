package com.minekube.craftwright.daemon

import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverActionInvocation
import com.minekube.craftwright.protocol.ApiRouteCatalog
import com.minekube.craftwright.protocol.Client
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.OpenApiDocument
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.ServerSocket
import java.time.Instant

class LocalSessionApiServer private constructor(
    private val service: ClientSessionService,
    private val host: String,
    requestedPort: Int,
) : AutoCloseable {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val events = mutableListOf<SessionEvent>()
    private val port = if (requestedPort == 0) allocateLoopbackPort() else requestedPort
    private val server = embeddedServer(CIO, host = host, port = port) {
        installRoutes()
    }

    fun start() {
        server.start()
    }

    fun url(path: String): String =
        "http://$host:$port$path"

    override fun close() {
        server.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
    }

    private fun Application.installRoutes() {
        routing {
            get("/version") {
                call.respondJson(HttpStatusCode.OK, RuntimeVersion.current())
            }
            get("/openapi.json") {
                call.respondJson(HttpStatusCode.OK, OpenApiDocument.from(ApiRouteCatalog.sessionDefaults()))
            }
            get("/events") {
                call.respondJson(HttpStatusCode.OK, events)
            }
            get("/clients/{id}/openapi.json") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondJson(HttpStatusCode.OK, service.openApiFor(clientId))
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "client not found"))
                }
            }
            post("/clients") {
                runCatching {
                    val request = json.decodeFromString<CreateClientRequest>(call.receiveText())
                    val client = service.createClient(request)
                    events += SessionEvent(
                        type = "client.created",
                        client = client.id,
                        message = "created client ${client.id}",
                    )
                    call.respondJson(HttpStatusCode.Created, client)
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            get("/clients") {
                call.respondJson(HttpStatusCode.OK, service.listClients())
            }
            get("/clients/{id}") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondJson(HttpStatusCode.OK, service.client(clientId))
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "client not found"))
                }
            }
            get("/clients/{id}/events") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    service.routesFor(clientId)
                    call.respondJson(HttpStatusCode.OK, events.filter { it.client == clientId })
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "client not found"))
                }
            }
            post("/clients/{id}:connect") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val request = json.decodeFromString<ConnectRequest>(call.receiveText())
                    val client = service.connectClient(
                        clientId,
                        ConnectionTarget(
                            host = request.host,
                            port = request.port,
                        )
                    )
                    events += SessionEvent(
                        type = "client.connected",
                        client = client.id,
                        message = "connected ${client.id} to ${request.host}:${request.port}",
                    )
                    call.respondJson(HttpStatusCode.OK, client)
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            get("/clients/{id}/actions") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondJson(HttpStatusCode.OK, service.openApiFor(clientId).actions)
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "client not found"))
                }
            }
            post("/clients/{id}:run") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val request = json.decodeFromString<ActionInvocationRequest>(call.receiveText())
                    val result = service.driverFor(clientId).invoke(
                        DriverActionInvocation(
                            action = request.action,
                            arguments = request.args,
                        )
                    )
                    if (request.action == "player.chat" && result.message != null) {
                        events += SessionEvent(
                            type = "chat",
                            client = clientId,
                            message = result.message,
                        )
                    }
                    call.respondJson(
                        HttpStatusCode.OK,
                        ActionInvocationResponse(
                            action = result.action,
                            status = result.status.name,
                            message = result.message,
                        )
                    )
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            post("/clients/{id}/stop") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val client = service.stopClient(clientId)
                    events += SessionEvent(
                        type = "client.stopped",
                        client = client.id,
                        message = "stopped client ${client.id}",
                    )
                    call.respondJson(HttpStatusCode.OK, client)
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "client not found"))
                }
            }
            post("/clients/{id}/{actionAlias}") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                val actionAlias = requireNotNull(call.parameters["actionAlias"]) { "action alias is required" }
                runCatching {
                    val actionId = actionAlias.toActionId()
                    val driver = runCatching { service.driverFor(clientId) }.getOrElse { error ->
                        throw GeneratedActionRouteNotFound(error.message ?: "client not found")
                    }
                    if (driver.actions().none { it.id == actionId }) {
                        throw GeneratedActionRouteNotFound("action $actionId is not available for client $clientId")
                    }
                    val result = driver.invoke(
                        DriverActionInvocation(
                            action = actionId,
                            arguments = call.receiveActionArguments(),
                        )
                    )
                    if (actionId == "player.chat" && result.message != null) {
                        events += SessionEvent(
                            type = "chat",
                            client = clientId,
                            message = result.message,
                        )
                    }
                    call.respondJson(
                        HttpStatusCode.OK,
                        ActionInvocationResponse(
                            action = result.action,
                            status = result.status.name,
                            message = result.message,
                        )
                    )
                }.getOrElse { error ->
                    if (error is GeneratedActionRouteNotFound) {
                        call.respondJson(HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", error.message ?: "action not found"))
                    } else {
                        call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                    }
                }
            }
        }
    }

    companion object {
        fun inMemory(port: Int = 0): LocalSessionApiServer =
            LocalSessionApiServer(
                service = ClientSessionService.inMemory(),
                host = "127.0.0.1",
                requestedPort = port,
            )
    }

    private suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.respondJson(
        status: HttpStatusCode,
        value: T,
    ) {
        respondText(json.encodeToString(value), ContentType.Application.Json, status)
    }
}

private fun allocateLoopbackPort(): Int =
    ServerSocket(0).use { socket ->
        socket.reuseAddress = true
        socket.localPort
    }

private suspend fun ApplicationCall.receiveActionArguments(): Map<String, JsonElement> {
    val body = receiveText()
    return if (body.isBlank()) emptyMap() else Json.decodeFromString(body)
}

private fun String.toActionId(): String {
    val parts = split(":", limit = 2)
    require(parts.size == 2 && parts.none { it.isBlank() }) { "action alias must use resource:action syntax" }
    return "${parts[0]}.${parts[1]}"
}

private class GeneratedActionRouteNotFound(message: String) : RuntimeException(message)

@Serializable
data class RuntimeVersion(
    val minecraft: String,
    val loader: String,
    val loaderVersion: String,
    val driver: String,
    val driverVersion: String,
    val java: String,
    val mappings: String,
    val openapiGeneratedAt: String,
) {
    companion object {
        fun current(now: Instant = Instant.now()): RuntimeVersion =
            RuntimeVersion(
                minecraft = "fake",
                loader = "none",
                loaderVersion = "none",
                driver = "craftwright-daemon",
                driverVersion = "0.1.0-SNAPSHOT",
                java = Runtime.version().feature().toString(),
                mappings = "none",
                openapiGeneratedAt = now.toString(),
            )
    }
}

@Serializable
data class SessionEvent(
    val type: String,
    val client: String? = null,
    val message: String? = null,
    val time: String = Instant.now().toString(),
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
)

@Serializable
data class ConnectRequest(
    val host: String,
    val port: Int,
)

@Serializable
data class ActionInvocationRequest(
    val action: String,
    val args: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ActionInvocationResponse(
    val action: String,
    val status: String,
    val message: String? = null,
)
