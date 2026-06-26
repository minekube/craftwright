package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.daemon.ActionInvocationRequest
import com.minekube.craftless.daemon.ConnectRequest
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.daemon.LocalSessionApiServer
import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.runtime.BackendDriverSession
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.Profile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class FabricClientSmokeController(
    val enabled: Boolean,
    val target: ConnectionTarget = ConnectionTarget("127.0.0.1", 25565),
    val chatMessage: String = "hello from Craftless Fabric smoke",
    val equipItemName: String = "Iron Sword",
    val requireEquipItem: Boolean = false,
    val connectTimeout: Duration = 30_000.milliseconds,
    val startupSettleDelay: Duration = 0.milliseconds,
    val artifactsDir: Path? = null,
) {
    init {
        require(!startupSettleDelay.isNegative()) { "fabric smoke startup settle delay must not be negative" }
    }

    fun start(
        backend: FabricDriverBackend,
        gateway: FabricClientGateway,
        pollInterval: Duration = 250.milliseconds,
    ): Boolean {
        if (!enabled) {
            return false
        }
        thread(name = "craftless-fabric-smoke", isDaemon = true) {
            runBlocking {
                runDaemonBackedSmoke(
                    backend = backend,
                    gateway = gateway,
                    pollInterval = pollInterval,
                )
            }
        }
        return true
    }

    private suspend fun runDaemonBackedSmoke(
        backend: FabricDriverBackend,
        gateway: FabricClientGateway,
        pollInterval: Duration,
    ) {
        LocalSessionApiServer
            .inMemory(
                driverFactory =
                    DriverSessionFactory { request ->
                        BackendDriverSession(clientId = request.id, backend = backend)
                    },
            ).use { api ->
                api.start()
                HttpClient(CIO).use { http ->
                    http.postJson(
                        api.url("/clients"),
                        CreateClientRequest(
                            id = SMOKE_CLIENT_ID,
                            version = MINECRAFT_VERSION,
                            loader = Loader.FABRIC,
                            profile = Profile.offline(SMOKE_PROFILE),
                        ),
                    )
                    val openApi = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/openapi.json"))
                    val actions = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/actions"))
                    val resources = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/resources"))
                    writeArtifact("client-openapi.json", openApi)
                    writeArtifact("client-actions.json", actions)
                    writeArtifact("client-resources.json", resources)
                    writeArtifact("runtime-metadata.json", smokeJson.encodeToString(backend.runtimeMetadata(SMOKE_CLIENT_ID)))

                    if (gateway.awaitReadyToConnect(connectTimeout, pollInterval)) {
                        Thread.sleep(startupSettleDelay.inWholeMilliseconds)
                        http.postJson(
                            api.url("/clients/$SMOKE_CLIENT_ID:connect"),
                            ConnectRequest(host = target.host, port = target.port),
                        )
                    }
                    if (gateway.awaitConnected(connectTimeout, pollInterval)) {
                        val connectedOpenApi = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/openapi.json"))
                        val connectedActions = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/actions"))
                        val connectedResources = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/resources"))
                        writeArtifact("client-openapi-connected.json", connectedOpenApi)
                        writeArtifact("client-actions-connected.json", connectedActions)
                        writeArtifact("client-resources-connected.json", connectedResources)

                        val chatResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "player.chat",
                                args = mapOf("message" to JsonPrimitive(chatMessage)),
                            )
                        val moveResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "player.move",
                                args =
                                    mapOf(
                                        "forward" to JsonPrimitive(true),
                                        "ticks" to JsonPrimitive(20),
                                    ),
                            )
                        val screenResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "screen.query",
                            )
                        val playerResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "player.query",
                            )
                        val inventoryResult =
                            http.runInventoryQuery(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                itemName = equipItemName,
                                requireItem = requireEquipItem,
                                timeout = connectTimeout,
                                pollInterval = pollInterval,
                            )
                        val targetItemSlot = inventoryResult.findHotbarSlotForItem(equipItemName)
                        val equipSlot = targetItemSlot ?: inventoryResult.selectedInventorySlot() ?: 0
                        val equipResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "inventory.equip",
                                args = mapOf("slot" to JsonPrimitive(equipSlot)),
                            )
                        val lookResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "player.look",
                                args =
                                    mapOf(
                                        "yaw" to JsonPrimitive(0.0),
                                        "pitch" to JsonPrimitive(0.0),
                                    ),
                            )
                        val blockBreakResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = "world.block.break",
                                args = mapOf("max-distance" to JsonPrimitive(4.0)),
                            )
                        val smokeResults =
                            listOfNotNull(
                                chatResult,
                                moveResult,
                                screenResult,
                                playerResult,
                                inventoryResult,
                                targetItemSlot?.let {
                                    """{"event":"craftless-smoke-target-item-observed","message":"observed $equipItemName in slot $it"}"""
                                },
                                """{"event":"craftless-smoke-inventory-select","message":"selected slot $equipSlot for $equipItemName"}""",
                                equipResult,
                                lookResult,
                                blockBreakResult,
                            )
                        writeLinesArtifact("gameplay-results.jsonl", smokeResults)
                    }
                    val events = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/events"))
                    writeJsonArrayLinesArtifact("client-events.jsonl", events)
                    http.post(api.url("/clients/$SMOKE_CLIENT_ID:stop")).expectSuccess()
                }
            }
    }

    private fun writeArtifact(
        name: String,
        content: String,
    ) {
        val dir = artifactsDir ?: return
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(name), content)
    }

    private fun writeJsonArrayLinesArtifact(
        name: String,
        content: String,
    ) {
        val lines =
            smokeJson
                .parseToJsonElement(content)
                .jsonArray
                .joinToString(separator = "\n", postfix = "\n") { it.toString() }
        writeArtifact(name, lines)
    }

    private fun writeLinesArtifact(
        name: String,
        lines: List<String>,
    ) {
        writeArtifact(name, lines.joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        private const val ENABLED = "CRAFTLESS_FABRIC_CLIENT_SMOKE"
        private const val HOST = "CRAFTLESS_SMOKE_SERVER_HOST"
        private const val PORT = "CRAFTLESS_SMOKE_SERVER_PORT"
        private const val CHAT_MESSAGE = "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE"
        private const val EQUIP_ITEM = "CRAFTLESS_FABRIC_SMOKE_EQUIP_ITEM"
        private const val REQUIRE_EQUIP_ITEM = "CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM"
        private const val CONNECT_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS"
        private const val STARTUP_SETTLE = "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS"
        private const val ARTIFACTS_DIR = "CRAFTLESS_SMOKE_ARTIFACTS_DIR"
        private const val SMOKE_CLIENT_ID = "fabric-smoke"
        private const val SMOKE_PROFILE = "CraftlessSmoke"
        private const val MINECRAFT_VERSION = "1.21.6"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): FabricClientSmokeController =
            FabricClientSmokeController(
                enabled = env.isEnabled(ENABLED),
                target =
                    ConnectionTarget(
                        host = env[HOST]?.takeIf { it.isNotBlank() } ?: "127.0.0.1",
                        port = env[PORT]?.toIntStrict(PORT) ?: 25565,
                    ),
                chatMessage =
                    env[CHAT_MESSAGE]?.takeIf { it.isNotBlank() }
                        ?: "hello from Craftless Fabric smoke",
                equipItemName = env[EQUIP_ITEM]?.takeIf { it.isNotBlank() } ?: "Iron Sword",
                requireEquipItem = env.isEnabled(REQUIRE_EQUIP_ITEM),
                connectTimeout = (env[CONNECT_TIMEOUT]?.toLongStrict(CONNECT_TIMEOUT) ?: 30_000).milliseconds,
                startupSettleDelay = (env[STARTUP_SETTLE]?.toLongStrict(STARTUP_SETTLE) ?: 0).milliseconds,
                artifactsDir = env[ARTIFACTS_DIR]?.takeIf { it.isNotBlank() }?.let(Path::of),
            )

        private fun Map<String, String>.isEnabled(name: String): Boolean = this[name] == "1" || this[name].equals("true", ignoreCase = true)

        private fun String.toIntStrict(name: String): Int = toIntOrNull() ?: error("$name must be an integer")

        private fun String.toLongStrict(name: String): Long = toLongOrNull() ?: error("$name must be a long integer")
    }
}

private suspend inline fun <reified T> HttpClient.postJson(
    url: String,
    value: T,
): String {
    val response =
        post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(smokeJson.encodeToString(value))
        }
    response.expectSuccess()
    return response.bodyAsText()
}

private suspend fun HttpClient.getText(url: String): String {
    val response = get(url)
    response.expectSuccess()
    return response.bodyAsText()
}

private suspend fun HttpClient.runAvailableAction(
    api: LocalSessionApiServer,
    clientId: String,
    openApi: String,
    action: String,
    args: Map<String, JsonElement> = emptyMap(),
): String {
    openApi.requireAvailableSmokeAction(action)
    return postJson(
        api.url("/clients/$clientId:run"),
        ActionInvocationRequest(
            action = action,
            args = args,
        ),
    )
}

private suspend fun HttpClient.runInventoryQuery(
    api: LocalSessionApiServer,
    clientId: String,
    openApi: String,
    itemName: String,
    requireItem: Boolean,
    timeout: Duration,
    pollInterval: Duration,
): String {
    require(timeout.isPositive()) { "fabric smoke inventory wait timeout must be positive" }
    require(pollInterval.isPositive()) { "fabric smoke inventory wait poll interval must be positive" }
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    var inventoryResult = ""
    do {
        inventoryResult =
            runAvailableAction(
                api = api,
                clientId = clientId,
                openApi = openApi,
                action = "inventory.query",
            )
        if (!requireItem || inventoryResult.findHotbarSlotForItem(itemName) != null) {
            return inventoryResult
        }
        delay(pollInterval)
    } while (System.nanoTime() < deadline)

    check(inventoryResult.findHotbarSlotForItem(itemName) != null) {
        "fabric smoke did not observe $itemName in inventory before timeout"
    }
    return inventoryResult
}

internal fun String.requireAvailableSmokeAction(action: String) {
    val available =
        smokeJson
            .decodeFromString<OpenApiDocument>(this)
            .actions
            .any { descriptor ->
                descriptor.id == action &&
                    descriptor.availability == OpenApiActionAvailability.AVAILABLE
            }
    check(available) { "fabric smoke action $action is not available in connected client OpenAPI" }
}

private fun String.findHotbarSlotForItem(itemName: String): Int? =
    inventorySlots()
        .firstOrNull { slot ->
            slot["item-name"]?.jsonPrimitive?.content == itemName &&
                slot["slot"]?.jsonPrimitive?.content?.toIntOrNull() in 0..8
        }?.get("slot")
        ?.jsonPrimitive
        ?.content
        ?.toIntOrNull()

private fun String.selectedInventorySlot(): Int? =
    smokeJson
        .parseToJsonElement(this)
        .jsonObject["data"]
        ?.jsonObject
        ?.get("selected-slot")
        ?.jsonPrimitive
        ?.content
        ?.toIntOrNull()
        ?.takeIf { it in 0..8 }

private fun String.inventorySlots(): List<kotlinx.serialization.json.JsonObject> =
    smokeJson
        .parseToJsonElement(this)
        .jsonObject["data"]
        ?.jsonObject
        ?.get("slots")
        ?.jsonArray
        ?.map { it.jsonObject }
        .orEmpty()

private suspend fun HttpResponse.expectSuccess() {
    check(status.value in 200..299) {
        "fabric smoke API request failed with ${status.value}: ${bodyAsText()}"
    }
}

private val smokeJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

private fun FabricClientGateway.awaitConnected(
    timeout: Duration,
    pollInterval: Duration,
): Boolean {
    require(timeout.isPositive()) { "fabric smoke connect timeout must be positive" }
    require(pollInterval.isPositive()) { "fabric smoke poll interval must be positive" }
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
        if (isConnected()) {
            return true
        }
        Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
    }
    return isConnected()
}

private fun FabricClientGateway.awaitReadyToConnect(
    timeout: Duration,
    pollInterval: Duration,
): Boolean {
    require(timeout.isPositive()) { "fabric smoke connect timeout must be positive" }
    require(pollInterval.isPositive()) { "fabric smoke poll interval must be positive" }
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
        if (isReadyToConnect()) {
            return true
        }
        Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
    }
    return isReadyToConnect()
}
