package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverOperationInvocation
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ApiRouteCatalog
import com.minekube.craftless.protocol.CacheCleanupRequest
import com.minekube.craftless.protocol.CacheExportRequest
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.Client
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.JavaRuntimeResolveRequest
import com.minekube.craftless.protocol.JsonRpcMethod
import com.minekube.craftless.protocol.JsonRpcRequest
import com.minekube.craftless.protocol.JsonRpcResponse
import com.minekube.craftless.protocol.LiveEvent
import com.minekube.craftless.protocol.LiveEventFilter
import com.minekube.craftless.protocol.OpenApiAction
import com.minekube.craftless.protocol.OpenApiActionArgument
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiJson
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.ServerSocket
import java.nio.file.Path
import java.time.Instant

class LocalSessionApiServer private constructor(
    private val service: ClientSessionService,
    private val cachePreparationService: CachePreparationService?,
    private val workspaceRuntimeFactory: WorkspaceClientRuntimeDriverFactory?,
    private val javaRuntimeService: JavaRuntimeService?,
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
    private val eventSubscriptions = linkedMapOf<String, EventSubscription>()
    private var eventSubscriptionSequence = 0
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
                call.respondOpenApi(HttpStatusCode.OK, OpenApiDocument.from(ApiRouteCatalog.sessionDefaults()))
            }
            get("/events") {
                call.respondJson(HttpStatusCode.OK, events)
            }
            get("/events:stream") {
                call.respondSse(events.toLiveEvents().filter { event -> call.liveEventFilter().matches(event) })
            }
            post("/cache:prepare") {
                runCatching {
                    val preparer = cachePreparationService ?: error("cache workspace is not configured")
                    val request = json.decodeFromString<CachePrepareRequest>(call.receiveText())
                    call.respondJson(HttpStatusCode.OK, preparer.prepare(request))
                }.getOrElse { error ->
                    when (error) {
                        is MissingClient -> call.respondMissingClient(error)
                        else -> call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                    }
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
            get("/runtimes/java") {
                runCatching {
                    val runtimes = javaRuntimeService ?: error("runtime workspace is not configured")
                    call.respondJson(HttpStatusCode.OK, runtimes.list())
                }.getOrElse { error ->
                    call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                }
            }
            post("/runtimes/java:resolve") {
                runCatching {
                    val runtimes = javaRuntimeService ?: error("runtime workspace is not configured")
                    val request = json.decodeFromString<JavaRuntimeResolveRequest>(call.receiveText())
                    call.respondJson(HttpStatusCode.OK, runtimes.resolve(request))
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
                    workspaceRuntimeFactory?.prepare(
                        request = request,
                        cachePreparationService = cachePreparationService ?: error("cache workspace is not configured"),
                        attachEnvironment = ClientDriverAttachEnvironment(request.id, url("")),
                    )
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
            post("/clients/{id}:attach") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    service.requireActiveClient(clientId)
                    val request = json.decodeFromString<DriverAttachRequest>(call.receiveText())
                    require(request.endpoint.isNotBlank()) { "driver attach endpoint is required" }
                    val client =
                        service.attachDriver(
                            clientId = clientId,
                            driver = HttpDriverSession(clientId = clientId, endpoint = request.endpoint),
                        )
                    events +=
                        SessionEvent(
                            type = "client.attached",
                            client = client.id,
                            message = "attached driver for client ${client.id}",
                        )
                    call.respondJson(HttpStatusCode.OK, client)
                }.getOrElse { error ->
                    when (error) {
                        is MissingClient -> call.respondMissingClient(error)
                        else -> call.respondJson(HttpStatusCode.BadRequest, ErrorResponse("BAD_REQUEST", error.message ?: "bad request"))
                    }
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
            get("/clients/{id}/events:stream") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    service.routesFor(clientId)
                    val filter = call.subscriptionFilter(clientId) ?: call.liveEventFilter(clientId = clientId)
                    call.respondSse(
                        events
                            .filter { it.client == clientId }
                            .toLiveEvents()
                            .filter { event -> filter.matches(event) },
                    )
                }.getOrElse { error ->
                    when (error) {
                        is RouteFailure -> call.respondRouteFailure(error)
                        else -> call.respondMissingClient(error)
                    }
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
                    if (client.state == ClientState.CONNECTED) {
                        events +=
                            SessionEvent(
                                type = "client.connected",
                                client = client.id,
                                message = "connected ${client.id} to ${request.host}:${request.port}",
                            )
                    }
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
                    val openApi = service.openApiFor(clientId)
                    call.respondLiveClientProjection(openApi, openApi.actions)
                }.getOrElse { error ->
                    call.respondMissingClient(error)
                }
            }
            get("/clients/{id}/resources") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val openApi = service.openApiFor(clientId)
                    call.respondLiveClientProjection(openApi, openApi.resources)
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
                    val result = driver.invokeGeneratedOperation(clientId, request.action, request.args)
                    if (result.status == DriverActionStatus.UNSUPPORTED) {
                        throw UnsupportedAction(result.message ?: "action ${request.action} is not available for client $clientId")
                    }
                    action.requireResult(result)
                    result.toSessionEvent(clientId, operationId = request.action)?.let { events += it }
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
            post("/clients/{id}:rpc") {
                val clientId = requireNotNull(call.parameters["id"]) { "client id is required" }
                runCatching {
                    val request = json.decodeFromString<JsonRpcRequest>(call.receiveText())
                    val response =
                        when (request.method) {
                            JsonRpcMethod.INVOKE -> {
                                val actionId =
                                    requireNotNull(request.params["action"]?.jsonPrimitive?.content) {
                                        "json rpc invoke requires action"
                                    }
                                if (!actionId.isCraftlessActionId()) {
                                    throw InvalidActionInput("invalid action id $actionId")
                                }
                                val args = request.params["args"]?.jsonObject ?: buildJsonObject {}
                                val driver = service.requireActiveDriver(clientId)
                                val openApi = service.openApiFor(clientId)
                                val action =
                                    openApi.actionDescriptor(actionId)
                                        ?: throw UnsupportedAction("action $actionId is not available for client $clientId")
                                action.requireAvailable(clientId)
                                action.requireArguments(args)
                                val result = driver.invokeGeneratedOperation(clientId, actionId, args)
                                if (result.status == DriverActionStatus.UNSUPPORTED) {
                                    throw UnsupportedAction(result.message ?: "action $actionId is not available for client $clientId")
                                }
                                action.requireResult(result)
                                result
                                    .toSessionEvent(clientId, operationId = actionId, correlationId = request.id)
                                    ?.let { events += it }
                                JsonRpcResponse.result(id = request.id, result = result.toJson())
                            }

                            JsonRpcMethod.SUBSCRIBE -> subscribeJsonRpc(clientId, request)

                            JsonRpcMethod.UNSUBSCRIBE -> unsubscribeJsonRpc(clientId, request)

                            JsonRpcMethod.QUERY -> queryJsonRpc(clientId, request)

                            else -> error("unsupported json rpc method ${request.method}")
                        }
                    call.respondJson(HttpStatusCode.OK, response)
                }.getOrElse { error ->
                    when (error) {
                        is RouteFailure -> call.respondRouteFailure(error)
                        else ->
                            call.respondJson(
                                HttpStatusCode.BadRequest,
                                JsonRpcResponse.error(id = null, code = "invalid-request", message = error.message ?: "invalid request"),
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
                    val result = driver.invokeGeneratedOperation(clientId, actionId, arguments)
                    if (result.status == DriverActionStatus.UNSUPPORTED) {
                        throw UnsupportedAction(result.message ?: "action $actionId is not available for client $clientId")
                    }
                    action.requireResult(result)
                    result.toSessionEvent(clientId, operationId = actionId)?.let { events += it }
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

    private fun subscribeJsonRpc(
        clientId: String,
        request: JsonRpcRequest,
    ): JsonRpcResponse {
        service.requireActiveClient(clientId)
        val subscription =
            EventSubscription(
                id = nextSubscriptionId(clientId),
                clientId = clientId,
                filter = request.params.toLiveEventFilter(clientId),
            )
        eventSubscriptions[subscription.id] = subscription
        return JsonRpcResponse.result(
            id = request.id,
            result =
                buildJsonObject {
                    put("subscriptionId", subscription.id)
                    put("filter", jsonElement(subscription.filter))
                    put("createdAt", subscription.createdAt)
                },
        )
    }

    private fun unsubscribeJsonRpc(
        clientId: String,
        request: JsonRpcRequest,
    ): JsonRpcResponse {
        service.requireActiveClient(clientId)
        val subscriptionId =
            requireNotNull(request.params["subscriptionId"]?.jsonPrimitive?.content) {
                "json rpc unsubscribe requires subscriptionId"
            }
        val subscription =
            eventSubscriptions[subscriptionId]
                ?: throw InvalidActionInput("subscription $subscriptionId is not active for client $clientId")
        if (subscription.clientId != clientId) {
            throw InvalidActionInput("subscription $subscriptionId is not active for client $clientId")
        }
        eventSubscriptions.remove(subscriptionId)
        return JsonRpcResponse.result(
            id = request.id,
            result =
                buildJsonObject {
                    put("subscriptionId", subscriptionId)
                    put("unsubscribed", true)
                },
        )
    }

    private fun queryJsonRpc(
        clientId: String,
        request: JsonRpcRequest,
    ): JsonRpcResponse {
        val target = request.params["target"]?.jsonPrimitive?.content ?: "openapi"
        val openApi by lazy { service.openApiFor(clientId) }
        val result =
            when (target) {
                "openapi" -> jsonElement(openApi)
                "actions" -> jsonElement(openApi.actions)
                "resources" -> jsonElement(openApi.resources)
                "handles" -> jsonElement(openApi.handles)
                "events" -> jsonElement(events.filter { event -> event.client == clientId }.toLiveEvents())
                "subscriptions" -> jsonElement(eventSubscriptions.values.filter { subscription -> subscription.clientId == clientId })
                else -> error("unsupported json rpc query target $target")
            }
        return JsonRpcResponse.result(id = request.id, result = result)
    }

    private inline fun <reified T> jsonElement(value: T): JsonElement = json.parseToJsonElement(json.encodeToString(value))

    private fun nextSubscriptionId(clientId: String): String {
        eventSubscriptionSequence += 1
        return "subscription:$clientId:${eventSubscriptionSequence.toString().padStart(4, '0')}"
    }

    private fun ApplicationCall.subscriptionFilter(clientId: String): LiveEventFilter? {
        val subscriptionId = request.queryParameters["subscriptionId"] ?: return null
        val subscription =
            eventSubscriptions[subscriptionId]
                ?: throw InvalidActionInput("subscription $subscriptionId is not active for client $clientId")
        if (subscription.clientId != clientId) {
            throw InvalidActionInput("subscription $subscriptionId is not active for client $clientId")
        }
        return subscription.filter
    }

    companion object {
        fun inMemory(
            host: String = "127.0.0.1",
            port: Int = 0,
            driverFactory: DriverSessionFactory? = null,
            workspaceRoot: Path? = null,
            cacheMetadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
            clientRuntimeLauncher: ClientRuntimeLauncher = ProcessClientRuntimeLauncher(),
            clientRuntimeDriverModProvider: ClientRuntimeDriverModProvider = ConfiguredClientRuntimeDriverModProvider(),
        ): LocalSessionApiServer {
            val workspaceRuntimeFactory =
                if (driverFactory == null && workspaceRoot != null) {
                    WorkspaceClientRuntimeDriverFactory(
                        workspaceRoot = workspaceRoot,
                        launcher = clientRuntimeLauncher,
                        driverModProvider = clientRuntimeDriverModProvider,
                    )
                } else {
                    null
                }
            val effectiveDriverFactory =
                driverFactory
                    ?: workspaceRuntimeFactory
                    ?: DriverSessionFactory.unavailable()
            return LocalSessionApiServer(
                service =
                    ClientSessionService.inMemory(
                        driverFactory = effectiveDriverFactory,
                        fileStore = workspaceRoot?.let(::InstanceFileStore),
                    ),
                cachePreparationService = workspaceRoot?.let { CachePreparationService(it, cacheMetadataFetcher) },
                workspaceRuntimeFactory = workspaceRuntimeFactory,
                javaRuntimeService = workspaceRoot?.let { JavaRuntimeService(it, cacheMetadataFetcher) },
                host = host,
                requestedPort = port,
            )
        }
    }

    private suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.respondJson(
        status: HttpStatusCode,
        value: T,
    ) {
        respondText(json.encodeToString(value), ContentType.Application.Json, status)
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondOpenApi(
        status: HttpStatusCode,
        value: OpenApiDocument,
    ) {
        respondText(OpenApiJson.encodeToString(value), ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.respondClientOpenApi(document: OpenApiDocument) {
        if (respondNotModifiedWhenFingerprintMatches(document)) {
            return
        }
        respondOpenApi(HttpStatusCode.OK, document)
    }

    private suspend inline fun <reified T> ApplicationCall.respondLiveClientProjection(
        document: OpenApiDocument,
        value: T,
    ) {
        if (respondNotModifiedWhenFingerprintMatches(document)) {
            return
        }
        respondJson(HttpStatusCode.OK, value)
    }

    private suspend fun ApplicationCall.respondSse(events: List<LiveEvent>) {
        val body =
            events.joinToString(separator = "") { event ->
                "id: ${event.id}\n" +
                    "event: ${event.type}\n" +
                    "data: ${json.encodeToString(event)}\n\n"
            }
        respondText(body, ContentType.Text.EventStream, HttpStatusCode.OK)
    }

    private suspend fun ApplicationCall.respondNotModifiedWhenFingerprintMatches(document: OpenApiDocument): Boolean {
        val runtimeFingerprint = document.extensions["x-craftless-runtime-fingerprint"]
        if (runtimeFingerprint != null) {
            val etag = "\"$runtimeFingerprint\""
            response.header("X-Craftless-Runtime-Fingerprint", runtimeFingerprint)
            response.header(HttpHeaders.ETag, etag)
            response.header(HttpHeaders.CacheControl, "no-cache")
            if (request.headers[HttpHeaders.IfNoneMatch] == etag) {
                respondText("", status = HttpStatusCode.NotModified)
                return true
            }
        }
        return false
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

private fun DriverSession.invokeGeneratedOperation(
    clientId: String,
    actionId: String,
    arguments: Map<String, JsonElement>,
): DriverActionResult {
    val operation = runtimeGraph().operations.firstOrNull { it.id == actionId }
    val adapters = operationAdapters()
    return if (operation != null && operation.adapter in adapters.adapterKeys()) {
        adapters.invoke(
            DriverOperationInvocation(
                clientId = clientId,
                operation = operation,
                arguments = arguments,
            ),
        )
    } else {
        invoke(
            DriverActionInvocation(
                action = actionId,
                arguments = arguments,
            ),
        )
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
        throw (availabilityReason ?: "action $id is not available for client $clientId").toUnavailableActionFailure()
    }
}

private fun String.toUnavailableActionFailure(): RouteFailure =
    when (this) {
        "permission-denied" -> PermissionDenied(this)
        "stale-handle" -> StaleHandle(this)
        "runtime-mismatch" -> RuntimeMismatch(this)
        else -> UnsupportedAction(this)
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

private fun DriverActionResult.toJson(): JsonObject =
    buildJsonObject {
        put("action", action)
        put("status", status.name)
        message?.let { put("message", it) }
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

private fun DriverActionResult.toSessionEvent(
    clientId: String,
    operationId: String,
    correlationId: String? = null,
): SessionEvent? {
    if (status != DriverActionStatus.ACCEPTED) {
        val eventMessage = message ?: return null
        return SessionEvent(
            type = "error",
            client = clientId,
            message = eventMessage,
            operationId = operationId,
            resourceId = operationId.substringBeforeLast("."),
            correlationId = correlationId,
        )
    }

    return SessionEvent(
        type = operationId,
        client = clientId,
        message = message,
        operationId = operationId,
        resourceId = operationId.substringBeforeLast("."),
        correlationId = correlationId,
        payload = toJson(),
    )
}

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

private class PermissionDenied(
    message: String,
) : RouteFailure(HttpStatusCode.Forbidden, "PERMISSION_DENIED", message)

private class StaleHandle(
    message: String,
) : RouteFailure(HttpStatusCode.Conflict, "STALE_HANDLE", message)

private class RuntimeMismatch(
    message: String,
) : RouteFailure(HttpStatusCode.Conflict, "RUNTIME_MISMATCH", message)

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
    val resourceId: String? = null,
    val operationId: String? = null,
    val correlationId: String? = null,
    val payload: JsonObject = buildJsonObject {},
    val time: String = Instant.now().toString(),
)

@Serializable
data class EventSubscription(
    val id: String,
    val clientId: String,
    val filter: LiveEventFilter,
    val createdAt: String = Instant.now().toString(),
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
data class DriverAttachRequest(
    val endpoint: String,
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

private fun ApplicationCall.liveEventFilter(clientId: String? = null): LiveEventFilter =
    LiveEventFilter(
        types = request.queryParameters.getAll("type").orEmpty(),
        clientId = clientId ?: request.queryParameters["clientId"],
        resourceId = request.queryParameters["resourceId"],
        operationId = request.queryParameters["operationId"],
        correlationId = request.queryParameters["correlationId"],
    )

private fun JsonObject.toLiveEventFilter(clientId: String): LiveEventFilter =
    LiveEventFilter(
        types = eventTypes(),
        clientId = clientId,
        resourceId = stringParam("resourceId"),
        operationId = stringParam("operationId"),
        correlationId = stringParam("correlationId"),
    )

private fun JsonObject.eventTypes(): List<String> =
    buildList {
        stringParam("type")?.let(::add)
        this@eventTypes["types"]?.jsonArray?.forEach { type ->
            add(type.jsonPrimitive.content)
        }
    }.distinct()

private fun JsonObject.stringParam(name: String): String? = this[name]?.jsonPrimitive?.content

private fun LiveEventFilter.matches(event: LiveEvent): Boolean =
    matches(
        type = event.type,
        clientId = event.clientId,
        resourceId = event.resourceId,
        operationId = event.operationId,
        correlationId = event.correlationId,
    )

private fun List<SessionEvent>.toLiveEvents(): List<LiveEvent> =
    mapIndexed { index, event ->
        event.toLiveEvent(index + 1)
    }

private fun SessionEvent.toLiveEvent(sequence: Int): LiveEvent {
    val liveType = toLiveEventType()
    val liveResourceId = resourceId ?: operationId?.substringBeforeLast(".")
    val payload =
        if (payload.isNotEmpty()) {
            payload
        } else {
            buildJsonObject {
                message?.let { put("message", it) }
            }
        }
    return LiveEvent(
        id = "event:${client ?: "supervisor"}:${sequence.toString().padStart(4, '0')}",
        type = liveType,
        clientId = client,
        resourceId = liveResourceId,
        operationId = operationId,
        correlationId = correlationId,
        payload = payload,
        timestamp = time,
    )
}

private fun SessionEvent.toLiveEventType(): String =
    when {
        type.isCraftlessActionId() -> type
        type == "error" -> "system.error"
        operationId != null -> operationId
        else -> "system.event"
    }
