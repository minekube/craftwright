package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.CacheCleanupRequest
import com.minekube.craftless.protocol.CacheExportRequest
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiActionArgument
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.isCraftlessActionId
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import java.nio.file.Path
import java.time.Instant

class LocalSessionApiServer private constructor(
    private val service: ClientSessionService,
    private val cachePreparationService: CachePreparationService?,
    private val host: String,
    requestedPort: Int,
) : AutoCloseable {
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    private val events = mutableListOf<SessionEvent>()
    private val port = if (requestedPort == 0) allocateLoopbackPort() else requestedPort
    private val server =
        embeddedServer(CIO, host = host, port = port) {
            installRoutes()
        }

    fun start() {
        server.start()
    }

    fun url(path: String): String = "http://$host:$port$path"

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
            post("/cache:prepare") {
                runCatching {
                    val preparer = cachePreparationService ?: error("cache workspace is not configured")
                    val request = json.decodeFromString<CachePrepareRequest>(call.receiveText())
                    call.respondJson(HttpStatusCode.OK, preparer.prepare(request))
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            post("/cache:export") {
                runCatching {
                    val preparer = cachePreparationService ?: error("cache workspace is not configured")
                    val request = json.decodeFromString<CacheExportRequest>(call.receiveText())
                    call.respondJson(HttpStatusCode.OK, preparer.export(request))
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            post("/cache:cleanup") {
                runCatching {
                    val preparer = cachePreparationService ?: error("cache workspace is not configured")
                    val request = json.decodeFromString<CacheCleanupRequest>(call.receiveText())
                    call.respondJson(HttpStatusCode.OK, preparer.cleanup(request))
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            get("/clients/{id}/openapi.json") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondClientOpenApi(service.openApiFor(clientId))
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            post("/clients") {
                runCatching {
                    val request = json.decodeFromString<CreateClientRequest>(call.receiveText())
                    val client = service.createClient(request)
                    events +=
                        SessionEvent(
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
                    call.respondMissingClient(error)
                }
            }
            get("/clients/{id}/events") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    service.routesFor(clientId)
                    call.respondJson(HttpStatusCode.OK, events.filter { it.client == clientId })
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            post("/clients/{id}:connect") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    service.requireActiveClient(clientId)
                    val request = json.decodeFromString<ConnectRequest>(call.receiveText())
                    val client =
                        service.connectClient(
                            clientId,
                            ConnectionTarget(
                                host = request.host,
                                port = request.port,
                            ),
                        )
                    events +=
                        SessionEvent(
                            type = "client.connected",
                            client = client.id,
                            message = "connected ${client.id} to ${request.host}:${request.port}",
                        )
                    call.respondJson(HttpStatusCode.OK, client)
                }.getOrElse { error ->
                    when (error) {
                        is RouteFailure -> call.respondRouteFailure(error)
                        else ->
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("BAD_REQUEST", error.message ?: "bad request"),
                            )
                    }
                }
            }
            get("/clients/{id}/actions") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondJson(HttpStatusCode.OK, service.openApiFor(clientId).actions)
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            get("/clients/{id}/resources") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    call.respondJson(HttpStatusCode.OK, service.resourcesFor(clientId))
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            post("/clients/{id}:run") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val request = json.decodeFromString<ActionInvocationRequest>(call.receiveText())
                    if (!request.action.isCraftlessActionId()) {
                        throw InvalidActionInput("invalid action id ${request.action}")
                    }
                    val driver = service.requireActiveDriver(clientId)
                    val openApi = service.openApiFor(clientId)
                    val action =
                        openApi.actionDescriptor(request.action)
                            ?: throw UnsupportedAction("action ${request.action} is not available for client $clientId")
                    action.requireAvailable(clientId)
                    action.requireArguments(request.args)
                    val result =
                        driver.invoke(
                            DriverActionInvocation(
                                action = request.action,
                                arguments = request.args,
                            ),
                        )
                    if (result.status == DriverActionStatus.UNSUPPORTED) {
                        throw UnsupportedAction(result.message ?: "action ${request.action} is not available for client $clientId")
                    }
                    action.requireResult(result)
                    result.toSessionEvent(clientId)?.let { events += it }
                    call.respondJson(
                        HttpStatusCode.OK,
                        ActionInvocationResponse(
                            action = result.action,
                            status = result.status.name,
                            message = result.message,
                            data = result.data.takeIf { it.isNotEmpty() },
                        ),
                    )
                }.getOrElse { error ->
                    when (error) {
                        is RouteFailure -> call.respondRouteFailure(error)
                        else ->
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("INVALID_ACTION_INPUT", error.message ?: "invalid action input"),
                            )
                    }
                }
            }
            post("/clients/{id}:stop") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val client = service.stopClient(clientId)
                    events +=
                        SessionEvent(
                            type = "client.stopped",
                            client = client.id,
                            message = "stopped client ${client.id}",
                        )
                    call.respondJson(HttpStatusCode.OK, client)
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            post(Regex("/clients/(?<id>[^/]+)/(?<actionAlias>.+:.+)")) {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                val actionAlias = requireNotNull(call.parameters["actionAlias"]) { "action alias is required" }
                runCatching {
                    val actionId = actionAlias.toActionId()
                    val driver = service.requireActiveDriver(clientId)
                    val openApi = service.openApiFor(clientId)
                    val action =
                        openApi.actionDescriptor(actionId)
                            ?: throw UnsupportedAction("action $actionId is not available for client $clientId")
                    val arguments = call.receiveActionArguments()
                    action.requireAvailable(clientId)
                    action.requireArguments(arguments)
                    val result =
                        driver.invoke(
                            DriverActionInvocation(
                                action = actionId,
                                arguments = arguments,
                            ),
                        )
                    if (result.status == DriverActionStatus.UNSUPPORTED) {
                        throw UnsupportedAction(result.message ?: "action $actionId is not available for client $clientId")
                    }
                    action.requireResult(result)
                    result.toSessionEvent(clientId)?.let { events += it }
                    call.respondJson(
                        HttpStatusCode.OK,
                        ActionInvocationResponse(
                            action = result.action,
                            status = result.status.name,
                            message = result.message,
                            data = result.data.takeIf { it.isNotEmpty() },
                        ),
                    )
                }.getOrElse { error ->
                    when (error) {
                        is RouteFailure -> call.respondRouteFailure(error)
                        else ->
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                ErrorResponse("INVALID_ACTION_INPUT", error.message ?: "invalid action input"),
                            )
                    }
                }
            }
        }
    }

    companion object {
        fun inMemory(
            port: Int = 0,
            driverFactory: DriverSessionFactory = DriverSessionFactory.unavailable(),
            workspaceRoot: Path? = null,
            cacheMetadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
        ): LocalSessionApiServer =
            LocalSessionApiServer(
                service =
                    ClientSessionService.inMemory(
                        driverFactory = driverFactory,
                        fileStore = workspaceRoot?.let(::InstanceFileStore),
                    ),
                cachePreparationService = workspaceRoot?.let { CachePreparationService(it, cacheMetadataFetcher) },
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

    private suspend fun ApplicationCall.respondClientOpenApi(document: OpenApiDocument) {
        val runtimeFingerprint = document.extensions["x-craftless-runtime-fingerprint"]
        if (runtimeFingerprint != null) {
            val etag = "\"$runtimeFingerprint\""
            response.header("X-Craftless-Runtime-Fingerprint", runtimeFingerprint)
            response.header(HttpHeaders.ETag, etag)
            response.header(HttpHeaders.CacheControl, "no-cache")
            if (request.headers[HttpHeaders.IfNoneMatch] == etag) {
                respondText("", status = HttpStatusCode.NotModified)
                return
            }
        }
        respondJson(HttpStatusCode.OK, document)
    }

    private suspend fun ApplicationCall.respondRouteFailure(error: RouteFailure) {
        respondJson(error.status, ErrorResponse(error.code, error.message ?: error.code))
    }

    private suspend fun ApplicationCall.respondMissingClient(error: Throwable) {
        val message = error.message ?: "client not found"
        respondJson(HttpStatusCode.NotFound, ErrorResponse("MISSING_CLIENT", message))
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

private fun OpenApiDocument.actionDescriptor(actionId: String): OpenApiAction? = actions.firstOrNull { it.id == actionId }

private fun ClientSessionService.requireActiveClient(clientId: String): Client {
    val client =
        runCatching { client(clientId) }.getOrElse { error ->
            throw MissingClient(error.message ?: "client $clientId not found")
        }
    if (client.state == ClientState.STOPPED) {
        throw StoppedClient("client $clientId is stopped")
    }
    return client
}

private fun ClientSessionService.requireActiveDriver(clientId: String): DriverSession {
    requireActiveClient(clientId)
    return runCatching { driverFor(clientId) }.getOrElse { error ->
        throw MissingClient(error.message ?: "client $clientId not found")
    }
}

private fun OpenApiAction.requireArguments(arguments: Map<String, JsonElement>) {
    val undeclared = arguments.keys.firstOrNull { it !in this.arguments }
    require(undeclared == null) { "action $id does not declare argument $undeclared" }
    val missingRequired =
        this.arguments
            .filterValues { it.required }
            .keys
            .firstOrNull { it !in arguments }
    require(missingRequired == null) { "action $id requires argument $missingRequired" }
    arguments.forEach { (name, value) ->
        this.arguments.getValue(name).requireValueType(id, name, value)
    }
}

private fun OpenApiAction.requireAvailable(clientId: String) {
    if (availability == OpenApiActionAvailability.UNAVAILABLE) {
        throw UnsupportedAction(availabilityReason ?: "action $id is not available for client $clientId")
    }
}

private fun OpenApiAction.requireResult(result: DriverActionResult) {
    if (result.action != id) {
        throw DriverResultMismatch("action $id returned result for ${result.action}")
    }

    val fields = result.responseFields()
    val undeclared = fields.keys.firstOrNull { it !in this.result.properties }
    if (undeclared != null) {
        throw DriverResultMismatch("action $id result does not declare property $undeclared")
    }

    val missingRequired = this.result.required.firstOrNull { it !in fields }
    if (missingRequired != null) {
        throw DriverResultMismatch("action $id result requires property $missingRequired")
    }

    fields.forEach { (name, value) ->
        val property = this.result.properties.getValue(name)
        if (!value.matchesActionArgumentType(property.type)) {
            throw DriverResultMismatch("action $id result property $name must be ${property.type}")
        }
    }
}

private fun DriverActionResult.responseFields(): Map<String, JsonElement> =
    buildMap {
        put("action", JsonPrimitive(action))
        put("status", JsonPrimitive(status.name))
        message?.let { put("message", JsonPrimitive(it)) }
        if (data.isNotEmpty()) {
            put("data", data)
        }
    }

private fun OpenApiActionArgument.requireValueType(
    actionId: String,
    name: String,
    value: JsonElement,
) {
    require(value.matchesActionArgumentType(type)) {
        "action $actionId argument $name must be $type"
    }
}

private fun JsonElement.matchesActionArgumentType(type: String): Boolean =
    when (type) {
        "boolean" -> this is JsonPrimitive && !jsonPrimitive.isJsonString() && booleanOrNull != null
        "integer" -> this is JsonPrimitive && !jsonPrimitive.isJsonString() && intOrNull != null
        "number" -> this is JsonPrimitive && !jsonPrimitive.isJsonString() && doubleOrNull != null
        "string" -> this is JsonPrimitive && jsonPrimitive.isJsonString()
        "object" -> this is JsonObject
        "array" -> this is JsonArray
        else -> false
    }

private fun JsonPrimitive.isJsonString(): Boolean = toString().startsWith("\"")

private fun String.toActionId(): String {
    val parts = split(":", limit = 2)
    if (parts.size != 2 || parts.any { it.isBlank() }) {
        throw UnsupportedAction("action alias must use resource:action syntax")
    }
    val resourceParts = parts[0].split("/")
    if (resourceParts.any { it.isBlank() }) {
        throw UnsupportedAction("action alias must use resource:action syntax")
    }
    return (resourceParts + parts[1]).joinToString(".")
}

private fun DriverActionResult.toSessionEvent(clientId: String): SessionEvent? {
    if (message == null) {
        return null
    }
    if (status != DriverActionStatus.ACCEPTED) {
        return SessionEvent(
            type = "error",
            client = clientId,
            message = message,
        )
    }

    return SessionEvent(
        type = eventType?.sessionEventType() ?: return null,
        client = clientId,
        message = message,
    )
}

private fun DriverEventType.sessionEventType(): String = name.lowercase().replace("_", ".")

private sealed class RouteFailure(
    val status: HttpStatusCode,
    val code: String,
    message: String,
) : RuntimeException(message)

private class MissingClient(
    message: String,
) : RouteFailure(HttpStatusCode.NotFound, "MISSING_CLIENT", message)

private class UnsupportedAction(
    message: String,
) : RouteFailure(HttpStatusCode.NotFound, "UNSUPPORTED_ACTION", message)

private class InvalidActionInput(
    message: String,
) : RouteFailure(HttpStatusCode.BadRequest, "INVALID_ACTION_INPUT", message)

private class DriverResultMismatch(
    message: String,
) : RouteFailure(HttpStatusCode.BadGateway, "DRIVER_RESULT_MISMATCH", message)

private class StoppedClient(
    message: String,
) : RouteFailure(HttpStatusCode.Conflict, "STOPPED_CLIENT", message)

@Serializable
data class RuntimeVersion(
    val minecraft: String,
    val loader: String,
    val loaderVersion: String,
    val driver: String,
    val driverVersion: String,
    val java: String,
    val mappingsFingerprint: String,
    val openapiGeneratedAt: String,
) {
    companion object {
        fun current(now: Instant = Instant.now()): RuntimeVersion =
            RuntimeVersion(
                minecraft = "fake",
                loader = "none",
                loaderVersion = "none",
                driver = "craftless-daemon",
                driverVersion = "0.1.0-SNAPSHOT",
                java = Runtime.version().feature().toString(),
                mappingsFingerprint = "none",
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
    val data: JsonObject? = null,
)
