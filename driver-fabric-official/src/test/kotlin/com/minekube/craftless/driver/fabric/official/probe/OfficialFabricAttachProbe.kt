package com.minekube.craftless.driver.fabric.official.probe

import com.minekube.craftless.daemon.ConnectRequest
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.daemon.LocalSessionApiServer
import com.minekube.craftless.daemon.SessionEvent
import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.JsonRpcMethod
import com.minekube.craftless.protocol.JsonRpcRequest
import com.minekube.craftless.protocol.JsonRpcResponse
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.Profile
import com.minekube.craftless.testkit.LocalServerFixture
import com.minekube.craftless.testkit.MinecraftServerJarProvisioner
import com.minekube.craftless.testkit.provisionMinecraftServerJar
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private val probeJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

fun main() {
    val config = OfficialFabricAttachProbeConfig.fromEnvironment()
    config.artifactsDir.createDirectories()
    val result =
        if (!config.enabled) {
            OfficialFabricAttachProbeResult(
                status = OfficialFabricAttachProbeStatus.SKIPPED,
                clientId = config.clientId,
                daemonUrl = null,
                message = "set CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 to run the official Fabric attach probe",
            )
        } else {
            runBlocking { OfficialFabricAttachProbe(config).run() }
        }
    config.artifactsDir.resolve("probe-result.json").writeText(probeJson.encodeToString(result) + "\n")
    println(result.message)
    if (config.enabled && result.status !in setOf(OfficialFabricAttachProbeStatus.ATTACHED, OfficialFabricAttachProbeStatus.CONNECTED)) {
        exitProcess(1)
    }
}

private class OfficialFabricAttachProbe(
    private val config: OfficialFabricAttachProbeConfig,
) {
    suspend fun run(): OfficialFabricAttachProbeResult {
        LocalSessionApiServer
            .inMemory(driverFactory = DriverSessionFactory { request -> ProbePreparedDriverSession(request.id) })
            .use { server ->
                server.start()
                val daemonUrl = server.url("")
                HttpClient(CIO).use { http ->
                    createClient(http, daemonUrl)
                    val localServer = if (config.connectEnabled) startLocalServer(http) else null
                    val command = startClientCommand(daemonUrl)
                    try {
                        val attached = awaitAttach(http, daemonUrl)
                        val connected =
                            attached &&
                                localServer != null &&
                                connectAndAwaitClientState(
                                    http = http,
                                    daemonUrl = daemonUrl,
                                    target = ConnectionTarget("127.0.0.1", config.connectPort),
                                )
                        val eventsText = http.get("$daemonUrl/events").bodyAsText()
                        val openApiText = http.get("$daemonUrl/clients/${config.clientId}/openapi.json").bodyAsText()
                        val actionsText = http.get("$daemonUrl/clients/${config.clientId}/actions").bodyAsText()
                        val resourcesText = http.get("$daemonUrl/clients/${config.clientId}/resources").bodyAsText()
                        val rpcOpenApiText = queryJsonRpc(http, daemonUrl, "openapi")
                        val rpcActionsText = queryJsonRpc(http, daemonUrl, "actions")
                        val rpcResourcesText = queryJsonRpc(http, daemonUrl, "resources")
                        val clientEventsStreamText = http.get("$daemonUrl/clients/${config.clientId}/events:stream").bodyAsText()
                        config.artifactsDir.resolve("daemon-events.json").writeText(eventsText + "\n")
                        config.artifactsDir.resolve("client-openapi.json").writeText(openApiText + "\n")
                        config.artifactsDir.resolve("client-actions.json").writeText(actionsText + "\n")
                        config.artifactsDir.resolve("client-resources.json").writeText(resourcesText + "\n")
                        config.artifactsDir.resolve("client-rpc-openapi.json").writeText(rpcOpenApiText + "\n")
                        config.artifactsDir.resolve("client-rpc-actions.json").writeText(rpcActionsText + "\n")
                        config.artifactsDir.resolve("client-rpc-resources.json").writeText(rpcResourcesText + "\n")
                        config.artifactsDir.resolve("client-events-stream.sse").writeText(clientEventsStreamText + "\n")
                        if (connected) {
                            config.artifactsDir.resolve("client-openapi-connected.json").writeText(openApiText + "\n")
                        }
                        return OfficialFabricAttachProbeResult(
                            status =
                                when {
                                    connected -> OfficialFabricAttachProbeStatus.CONNECTED
                                    attached && !config.connectEnabled -> OfficialFabricAttachProbeStatus.ATTACHED
                                    else -> OfficialFabricAttachProbeStatus.TIMEOUT
                                },
                            clientId = config.clientId,
                            daemonUrl = daemonUrl,
                            connectTarget = if (config.connectEnabled) "127.0.0.1:${config.connectPort}" else null,
                            connectedResources = connectedResourceIds(openApiText),
                            streamedEventTypes = sseEventTypes(clientEventsStreamText),
                            publicActionCount = jsonArraySize(actionsText),
                            publicResourceIds = publicResourceIds(resourcesText),
                            rpcQueryTargets = listOf("openapi", "actions", "resources"),
                            rpcActionCount = rpcResultArraySize(rpcActionsText),
                            rpcResourceIds = rpcResourceIds(rpcResourcesText),
                            message =
                                if (connected) {
                                    "official Fabric probe observed connected client state for ${config.clientId}"
                                } else if (attached && !config.connectEnabled) {
                                    "official Fabric probe observed client attach for ${config.clientId}"
                                } else {
                                    "official Fabric probe timed out waiting for ${if (config.connectEnabled) "connected client state" else "client attach"} for ${config.clientId}"
                                },
                        )
                    } finally {
                        localServer?.stopAndCollect()
                        command.stopAndWriteLog(config.artifactsDir.resolve("client-command.log"))
                    }
                }
            }
    }

    private suspend fun createClient(
        http: HttpClient,
        daemonUrl: String,
    ) {
        http.post("$daemonUrl/clients") {
            contentType(ContentType.Application.Json)
            setBody(
                probeJson.encodeToString(
                    CreateClientRequest(
                        id = config.clientId,
                        version = "26.2",
                        loader = Loader.FABRIC,
                        profile = Profile.offline("CraftlessProbe"),
                        loaderVersion = "0.19.3",
                    ),
                ),
            )
        }
    }

    private suspend fun startLocalServer(http: HttpClient) =
        LocalServerFixture(
            root = config.artifactsDir.resolve("minecraft-server"),
            port = config.connectPort,
        ).prepare().let { layout ->
            val serverJar =
                layout.provisionMinecraftServerJar(
                    version = "26.2",
                    provisioner = MinecraftServerJarProvisioner(http),
                )
            layout.startMinecraftServer(serverJar = serverJar)
        }

    private fun startClientCommand(daemonUrl: String): RunningProbeCommand {
        val output = mutableListOf<String>()
        val process =
            ProcessBuilder(config.clientCommand)
                .directory(config.artifactsDir.toFile())
                .also { builder ->
                    builder.environment()["CRAFTLESS_CLIENT_ID"] = config.clientId
                    builder.environment()["CRAFTLESS_DAEMON_URL"] = daemonUrl
                }.redirectErrorStream(true)
                .start()
        val reader =
            Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> synchronized(output) { output += line } }
                    }
                }.onFailure { error ->
                    if (error !is IOException) {
                        synchronized(output) {
                            output += "craftless official attach probe output reader failed: ${error.message}"
                        }
                    }
                }
            }.also { thread ->
                thread.name = "craftless-official-attach-probe-output"
                thread.isDaemon = true
                thread.start()
            }
        return RunningProbeCommand(process = process, output = output, reader = reader)
    }

    private suspend fun awaitAttach(
        http: HttpClient,
        daemonUrl: String,
    ): Boolean {
        val deadline = System.nanoTime() + config.timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            val events =
                probeJson.decodeFromString(
                    ListSerializer(SessionEvent.serializer()),
                    http.get("$daemonUrl/events").bodyAsText(),
                )
            if (events.any { event -> event.type == "client.attached" && event.client == config.clientId }) {
                return true
            }
            delay(250)
        }
        return false
    }

    private suspend fun connectAndAwaitClientState(
        http: HttpClient,
        daemonUrl: String,
        target: ConnectionTarget,
    ): Boolean {
        http.post("$daemonUrl/clients/${config.clientId}:connect") {
            contentType(ContentType.Application.Json)
            setBody(probeJson.encodeToString(ConnectRequest(host = target.host, port = target.port)))
        }
        val deadline = System.nanoTime() + config.timeoutMillis * 1_000_000
        while (System.nanoTime() < deadline) {
            val openApiText = http.get("$daemonUrl/clients/${config.clientId}/openapi.json").bodyAsText()
            if (hasConnectedClientState(openApiText)) {
                return true
            }
            delay(250)
        }
        return false
    }

    private suspend fun queryJsonRpc(
        http: HttpClient,
        daemonUrl: String,
        target: String,
    ): String =
        http
            .post("$daemonUrl/clients/${config.clientId}:rpc") {
                contentType(ContentType.Application.Json)
                setBody(
                    probeJson.encodeToString(
                        JsonRpcRequest(
                            id = "rpc:official_probe:$target",
                            method = JsonRpcMethod.QUERY,
                            params =
                                buildJsonObject {
                                    put("target", target)
                                },
                        ),
                    ),
                )
            }.bodyAsText()

    private fun hasConnectedClientState(openApiText: String): Boolean {
        val available = connectedResourceIds(openApiText).toSet()
        return CONNECTED_CLIENT_STATE_RESOURCES.all(available::contains)
    }

    private fun connectedResourceIds(openApiText: String): List<String> =
        (probeJson.parseToJsonElement(openApiText).jsonObject["x-craftless-resources"] as? JsonArray)
            .orEmpty()
            .mapNotNull { element ->
                val resource = element.jsonObject
                val id = resource["id"]?.jsonPrimitive?.content
                val availability = resource["availability"]?.jsonPrimitive?.content
                id?.takeIf { availability == "available" }
            }

    private fun sseEventTypes(sse: String): List<String> =
        sse
            .lineSequence()
            .mapNotNull { line ->
                line.removePrefix("event: ").takeIf { eventType -> eventType != line }
            }.toList()

    private fun jsonArraySize(text: String): Int = (probeJson.parseToJsonElement(text) as? JsonArray).orEmpty().size

    private fun publicResourceIds(resourcesText: String): List<String> =
        (probeJson.parseToJsonElement(resourcesText) as? JsonArray)
            .orEmpty()
            .mapNotNull { element -> element.jsonObject["id"]?.jsonPrimitive?.content }

    private fun rpcResultArraySize(text: String): Int =
        requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
            .jsonArray
            .size

    private fun rpcResourceIds(text: String): List<String> =
        requireNotNull(probeJson.decodeFromString<JsonRpcResponse>(text).result)
            .jsonArray
            .mapNotNull { element -> element.jsonObject["id"]?.jsonPrimitive?.content }

    private companion object {
        val CONNECTED_CLIENT_STATE_RESOURCES = setOf("client", "player", "inventory", "world")
    }
}

private data class OfficialFabricAttachProbeConfig(
    val enabled: Boolean,
    val artifactsDir: Path,
    val clientId: String,
    val clientCommand: List<String>,
    val timeoutMillis: Long,
    val connectEnabled: Boolean,
    val connectPort: Int,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): OfficialFabricAttachProbeConfig {
            val commandJson = env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_CLIENT_COMMAND_JSON"].orEmpty()
            return OfficialFabricAttachProbeConfig(
                enabled = env["CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE"].isEnabled(),
                artifactsDir =
                    env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_ARTIFACTS_DIR"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Path::of)
                        ?: Path.of("driver-fabric-official", "build", "craftless-official-attach-probe"),
                clientId = env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_CLIENT_ID"]?.takeIf { it.isNotBlank() } ?: "official-probe",
                clientCommand =
                    if (commandJson.isBlank()) {
                        emptyList()
                    } else {
                        probeJson.decodeFromString(ListSerializer(String.serializer()), commandJson)
                    },
                timeoutMillis = env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS"]?.toLongOrNull() ?: 120_000,
                connectEnabled = env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT"].isEnabled(),
                connectPort = env["CRAFTLESS_OFFICIAL_ATTACH_PROBE_SERVER_PORT"]?.toIntOrNull() ?: allocateLoopbackPort(),
            )
        }
    }
}

@Serializable
private data class OfficialFabricAttachProbeResult(
    val status: OfficialFabricAttachProbeStatus,
    val clientId: String,
    val daemonUrl: String?,
    val message: String,
    val connectTarget: String? = null,
    val connectedResources: List<String> = emptyList(),
    val streamedEventTypes: List<String> = emptyList(),
    val publicActionCount: Int = 0,
    val publicResourceIds: List<String> = emptyList(),
    val rpcQueryTargets: List<String> = emptyList(),
    val rpcActionCount: Int = 0,
    val rpcResourceIds: List<String> = emptyList(),
)

@Serializable
private enum class OfficialFabricAttachProbeStatus {
    SKIPPED,
    ATTACHED,
    CONNECTED,
    TIMEOUT,
}

private class RunningProbeCommand(
    private val process: Process,
    private val output: MutableList<String>,
    private val reader: Thread,
) {
    fun stopAndWriteLog(log: Path) {
        process.destroy()
        process.waitFor()
        reader.join(1_000)
        Files.createDirectories(log.parent)
        log.writeText(synchronized(output) { output.joinToString(separator = "\n", postfix = "\n") })
    }
}

private class ProbePreparedDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata = DriverRuntimeMetadata(driver = "craftless-official-probe-prepared")

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "official probe prepared session is waiting for in-client attach",
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}

private fun allocateLoopbackPort(): Int = ServerSocket(0).use { socket -> socket.localPort }

private fun String?.isEnabled(): Boolean = this == "1" || equals("true", ignoreCase = true)
