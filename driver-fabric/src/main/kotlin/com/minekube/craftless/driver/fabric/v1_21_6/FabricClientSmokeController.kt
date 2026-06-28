package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.daemon.ActionInvocationRequest
import com.minekube.craftless.daemon.ConnectRequest
import com.minekube.craftless.daemon.DriverSessionFactory
import com.minekube.craftless.daemon.LocalSessionApiServer
import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata
import com.minekube.craftless.driver.runtime.BackendDriverSession
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.Profile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
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
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private data class FinalGameplayConfirmationEvidence(
    val player: String,
    val message: String,
    val line: Int = 0,
)

data class FabricClientSmokeController(
    val enabled: Boolean,
    val target: ConnectionTarget = ConnectionTarget("127.0.0.1", 25565),
    val chatMessage: String = "hello from Craftless Fabric smoke",
    val equipItemName: String = "Iron Sword",
    val requireEquipItem: Boolean = false,
    val connectTimeout: Duration = 30_000.milliseconds,
    val actionTimeout: Duration = 30_000.milliseconds,
    val publicAgentCommandTimeout: Duration = actionTimeout,
    val startupSettleDelay: Duration = 0.milliseconds,
    val holdAfterActions: Duration = 0.milliseconds,
    val artifactsDir: Path? = null,
    val publicAgentCommand: List<String> = emptyList(),
    val readyNotificationCommand: List<String> = emptyList(),
    val readyNotificationReminder: Duration = 0.milliseconds,
    val confirmationChatContains: String? = null,
    val activityExtendsHold: Duration = 0.milliseconds,
) {
    init {
        require(!startupSettleDelay.isNegative()) { "fabric smoke startup settle delay must not be negative" }
        require(!holdAfterActions.isNegative()) { "fabric smoke hold after actions delay must not be negative" }
        require(!readyNotificationReminder.isNegative()) { "fabric smoke ready reminder delay must not be negative" }
        require(!activityExtendsHold.isNegative()) { "fabric smoke activity hold extension must not be negative" }
        require(actionTimeout.isPositive()) { "fabric smoke action timeout must be positive" }
        require(publicAgentCommandTimeout.isPositive()) { "fabric smoke public agent command timeout must be positive" }
        require(publicAgentCommand.none { it.isBlank() }) { "fabric smoke public agent command entries must not be blank" }
        require(readyNotificationCommand.none { it.isBlank() }) { "fabric smoke ready notification command entries must not be blank" }
        require(confirmationChatContains == null || confirmationChatContains.isNotBlank()) {
            "fabric smoke confirmation chat phrase must not be blank"
        }
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
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = actionTimeout.inWholeMilliseconds
                        connectTimeoutMillis = connectTimeout.inWholeMilliseconds
                        socketTimeoutMillis = actionTimeout.inWholeMilliseconds
                    }
                }.use { http ->
                    http.postJson(
                        api.url("/clients"),
                        CreateClientRequest(
                            id = SMOKE_CLIENT_ID,
                            version = FabricCompiledLaneMetadata.MINECRAFT_VERSION,
                            loader = Loader.FABRIC,
                            profile = Profile.offline(SMOKE_PROFILE),
                        ),
                    )
                    val openApi = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/openapi.json"))
                    val actions = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/actions"))
                    writeArtifact("client-openapi.json", openApi)
                    writeArtifact("client-actions.json", actions)
                    writeArtifact("client-resources.json", openApi.smokeResourceArtifactFromOpenApi())
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
                        writeArtifact("client-openapi-connected.json", connectedOpenApi)
                        writeArtifact("client-actions-connected.json", connectedActions)
                        writeArtifact("client-resources-connected.json", connectedOpenApi.smokeResourceArtifactFromOpenApi())

                        val chatResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.PLAYER_CHAT,
                                args = mapOf("message" to JsonPrimitive(chatMessage)),
                            )
                        val moveResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.PLAYER_MOVE,
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
                                action = FabricBootstrapOperationIds.SCREEN_QUERY,
                            )
                        val worldTimeResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.WORLD_TIME_QUERY,
                            )
                        val playerResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.PLAYER_QUERY,
                            )
                        val entityResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.ENTITY_QUERY,
                                args =
                                    mapOf(
                                        "radius" to JsonPrimitive(16.0),
                                        "limit" to JsonPrimitive(25),
                                    ),
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
                                action = FabricBootstrapOperationIds.INVENTORY_EQUIP,
                                args = mapOf("slot" to JsonPrimitive(equipSlot)),
                            )
                        val lookResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.PLAYER_LOOK,
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
                                action = FabricBootstrapOperationIds.WORLD_BLOCK_BREAK,
                                args = mapOf("max-distance" to JsonPrimitive(4.0)),
                            )
                        val blockInteractResult =
                            http.runAvailableAction(
                                api = api,
                                clientId = SMOKE_CLIENT_ID,
                                openApi = connectedOpenApi,
                                action = FabricBootstrapOperationIds.WORLD_BLOCK_INTERACT,
                                args = mapOf("max-distance" to JsonPrimitive(4.0)),
                            )
                        val inventorySelectionEvidence =
                            if (targetItemSlot == null) {
                                """{"event":"craftless-smoke-inventory-fallback","message":"target item $equipItemName was not observed; selected slot $equipSlot"}"""
                            } else {
                                """{"event":"craftless-smoke-inventory-select","message":"selected slot $equipSlot for $equipItemName"}"""
                            }
                        val generatedGameplayResults =
                            listOfNotNull(
                                chatResult,
                                moveResult,
                                screenResult,
                                worldTimeResult,
                                playerResult,
                                entityResult,
                                inventoryResult,
                                targetItemSlot?.let {
                                    """{"event":"craftless-smoke-target-item-observed","message":"observed $equipItemName in slot $it"}"""
                                },
                                inventorySelectionEvidence,
                                equipResult,
                                lookResult,
                                blockBreakResult,
                                blockInteractResult,
                            )
                        writeLinesArtifact(
                            "public-agent-gameplay-results.jsonl",
                            publicAgentGameplayResults(generatedGameplayResults),
                        )
                        writeLinesArtifact(
                            "public-agent-state.jsonl",
                            publicAgentStateResults(connectedOpenApi),
                        )
                        runPublicAgentCommand(api.url(""))
                        writeLinesArtifact("gameplay-results.jsonl", generatedGameplayResults)
                    }
                    val events = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/events"))
                    writeJsonArrayLinesArtifact("client-events.jsonl", events)
                    val eventStream = http.getText(api.url("/clients/$SMOKE_CLIENT_ID/events:stream"))
                    writeArtifact("client-events-stream.sse", eventStream)
                    if (holdAfterActions.isPositive()) {
                        waitForFinalGameplayConfirmation(api.url(""), pollInterval)
                    }
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

    private fun appendArtifact(
        name: String,
        content: String,
    ) {
        val dir = artifactsDir ?: return
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve(name),
            content + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
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

    private fun runPublicAgentCommand(baseUrl: String) {
        if (publicAgentCommand.isEmpty()) {
            return
        }
        val dir = artifactsDir ?: error("public agent command requires CRAFTLESS_SMOKE_ARTIFACTS_DIR")
        Files.createDirectories(dir)
        val log = dir.resolve("public-agent-command.log")
        val process =
            ProcessBuilder(publicAgentCommand)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .also { builder ->
                    builder.environment().removeInheritedSmokeOwnerEnvironment()
                    builder.environment()["CRAFTLESS_PUBLIC_AGENT_BASE_URL"] = baseUrl
                    builder.environment()["CRAFTLESS_PUBLIC_AGENT_CLIENT_ID"] = SMOKE_CLIENT_ID
                    builder.environment()["CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR"] = dir.toString()
                    builder.environment()["CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS"] =
                        actionTimeout.inWholeMilliseconds.toString()
                }.start()
        val exited = process.waitFor(publicAgentCommandTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
            error("public agent command timed out after ${publicAgentCommandTimeout.inWholeMilliseconds}ms; log=$log")
        }
        check(process.exitValue() == 0) {
            "public agent command exited with ${process.exitValue()}; log=$log"
        }
        publicAgentBlockedArtifact()?.let { blocker ->
            writeArtifact("public-agent-blocked.json", publicAgentBlockedArtifactContent(blocker))
            error("public agent command reported blocked: $blocker; log=$log")
        }
    }

    private fun publicAgentBlockedArtifact(): String? {
        val artifact = artifactsDir?.resolve("public-agent-gameplay-results.jsonl") ?: return null
        if (!Files.isRegularFile(artifact)) {
            return null
        }
        return Files.readAllLines(artifact).firstNotNullOfOrNull { line ->
            runCatching {
                val entry = smokeJson.parseToJsonElement(line).jsonObject
                if (entry["event"]?.jsonPrimitive?.content != "public-agent-blocked") {
                    return@runCatching null
                }
                entry["blocker"]?.jsonPrimitive?.content ?: "unknown-blocker"
            }.getOrNull()
        }
    }

    private fun publicAgentBlockedArtifactContent(blocker: String): String =
        smokeJson.encodeToString(
            mapOf(
                "event" to "public-agent-blocked",
                "client-id" to SMOKE_CLIENT_ID,
                "blocker" to blocker,
                "artifacts-dir" to (artifactsDir?.toString() ?: ""),
            ),
        )

    private fun runReadyNotificationCommand(baseUrl: String) {
        writeArtifact("final-gameplay-ready.json", readyNotificationArtifact(baseUrl))
        writeArtifact("final-gameplay-join-instructions.txt", finalGameplayJoinInstructions(baseUrl))
        if (readyNotificationCommand.isEmpty()) {
            return
        }
        val dir = artifactsDir ?: error("ready notification command requires CRAFTLESS_SMOKE_ARTIFACTS_DIR")
        Files.createDirectories(dir)
        val log = dir.resolve("final-gameplay-ready-command.log")
        val process =
            ProcessBuilder(readyNotificationCommand)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .also { builder ->
                    builder.environment().removeInheritedSmokeOwnerEnvironment()
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_BASE_URL"] = baseUrl
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_CLIENT_ID"] = SMOKE_CLIENT_ID
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_SERVER_HOST"] = target.host
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_SERVER_PORT"] = target.port.toString()
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_ARTIFACTS_DIR"] = dir.toString()
                    builder.environment()["CRAFTLESS_FABRIC_SMOKE_READY_HOLD_MS"] =
                        holdAfterActions.inWholeMilliseconds.toString()
                }.start()
        val exited = process.waitFor(connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
            error("ready notification command timed out after ${connectTimeout.inWholeMilliseconds}ms; log=$log")
        }
        check(process.exitValue() == 0) {
            "ready notification command exited with ${process.exitValue()}; log=$log"
        }
    }

    private suspend fun waitForFinalGameplayConfirmation(
        baseUrl: String,
        pollInterval: Duration,
    ) {
        runReadyNotificationCommand(baseUrl)
        val phrase = confirmationChatContains?.takeIf { it.isNotBlank() }
        if (phrase == null && !readyNotificationReminder.isPositive()) {
            delay(holdAfterActions)
            return
        }
        val pollDelay = pollInterval.takeIf { it.isPositive() } ?: 250.milliseconds
        var deadline = System.nanoTime() + holdAfterActions.inWholeNanoseconds
        val reminderIntervalNanos = readyNotificationReminder.takeIf { it.isPositive() }?.inWholeNanoseconds
        var nextReminderAt = reminderIntervalNanos?.let { System.nanoTime() + it }
        var observedChatLine = latestChatEvidenceLine()
        while (System.nanoTime() < deadline) {
            if (phrase != null) {
                findConfirmationEvidence(phrase)?.let { evidence ->
                    writeArtifact("final-gameplay-confirmation.json", finalGameplayConfirmationArtifact(baseUrl, evidence))
                    return
                }
            }
            if (activityExtendsHold.isPositive()) {
                val activity = latestNonConfirmationChatAfter(observedChatLine, phrase)
                if (activity != null) {
                    observedChatLine = activity.line
                    val extendedDeadline = System.nanoTime() + activityExtendsHold.inWholeNanoseconds
                    if (extendedDeadline > deadline) {
                        deadline = extendedDeadline
                        appendArtifact(
                            "final-gameplay-activity.jsonl",
                            finalGameplayActivityArtifact(baseUrl, activity),
                        )
                    }
                }
            }
            val now = System.nanoTime()
            if (nextReminderAt != null && now >= nextReminderAt) {
                runReadyNotificationCommand(baseUrl)
                nextReminderAt = now + requireNotNull(reminderIntervalNanos)
            }
            delay(pollDelay)
        }
        writeArtifact("final-gameplay-confirmation-timeout.json", finalGameplayConfirmationTimeoutArtifact(baseUrl, phrase))
    }

    private fun findConfirmationEvidence(phrase: String): FinalGameplayConfirmationEvidence? {
        val evidenceLog = artifactsDir?.resolve("server-evidence.jsonl") ?: return null
        if (!Files.isRegularFile(evidenceLog)) {
            return null
        }
        return readChatEvidence(evidenceLog)
            .asSequence()
            .firstOrNull { evidence -> evidence.message.contains(phrase, ignoreCase = true) }
    }

    private fun latestChatEvidenceLine(): Int {
        val evidenceLog = artifactsDir?.resolve("server-evidence.jsonl") ?: return 0
        if (!Files.isRegularFile(evidenceLog)) {
            return 0
        }
        return readChatEvidence(evidenceLog).maxOfOrNull { it.line } ?: 0
    }

    private fun latestNonConfirmationChatAfter(
        line: Int,
        phrase: String?,
    ): FinalGameplayConfirmationEvidence? {
        val evidenceLog = artifactsDir?.resolve("server-evidence.jsonl") ?: return null
        if (!Files.isRegularFile(evidenceLog)) {
            return null
        }
        return readChatEvidence(evidenceLog)
            .asSequence()
            .filter { it.line > line }
            .filterNot { evidence -> phrase != null && evidence.message.contains(phrase, ignoreCase = true) }
            .lastOrNull()
    }

    private fun readChatEvidence(evidenceLog: Path): List<FinalGameplayConfirmationEvidence> =
        Files
            .readAllLines(evidenceLog)
            .mapIndexedNotNull { index, line -> line.toFinalGameplayConfirmationEvidenceOrNull(index + 1) }

    private fun readyNotificationArtifact(baseUrl: String): String =
        smokeJson.encodeToString(
            mapOf(
                "event" to "final-gameplay-ready",
                "base-url" to baseUrl,
                "client-id" to SMOKE_CLIENT_ID,
                "server" to "${target.host}:${target.port}",
                "artifacts-dir" to (artifactsDir?.toString() ?: ""),
                "hold-ms" to holdAfterActions.inWholeMilliseconds.toString(),
                "confirmation-contains" to (confirmationChatContains ?: ""),
                "activity-extends-hold-ms" to activityExtendsHold.inWholeMilliseconds.toString(),
            ),
        )

    private fun finalGameplayJoinInstructions(baseUrl: String): String =
        buildString {
            appendLine("Craftless final gameplay is ready.")
            appendLine("Server: ${target.host}:${target.port}")
            appendLine("Client id: $SMOKE_CLIENT_ID")
            appendLine("Base URL: $baseUrl")
            appendLine("Artifacts: ${artifactsDir?.toString() ?: ""}")
            appendLine("Hold ms: ${holdAfterActions.inWholeMilliseconds}")
            appendLine("Activity extends hold ms: ${activityExtendsHold.inWholeMilliseconds}")
            appendLine("Confirmation phrase: ${confirmationChatContains ?: ""}")
        }

    private fun finalGameplayConfirmationArtifact(
        baseUrl: String,
        evidence: FinalGameplayConfirmationEvidence,
    ): String =
        smokeJson.encodeToString(
            mapOf(
                "event" to "final-gameplay-confirmed",
                "base-url" to baseUrl,
                "client-id" to SMOKE_CLIENT_ID,
                "server" to "${target.host}:${target.port}",
                "player" to evidence.player,
                "message" to evidence.message,
                "artifacts-dir" to (artifactsDir?.toString() ?: ""),
            ),
        )

    private fun finalGameplayActivityArtifact(
        baseUrl: String,
        evidence: FinalGameplayConfirmationEvidence,
    ): String =
        smokeJson.encodeToString(
            mapOf(
                "event" to "final-gameplay-activity-extended",
                "base-url" to baseUrl,
                "client-id" to SMOKE_CLIENT_ID,
                "server" to "${target.host}:${target.port}",
                "player" to evidence.player,
                "message" to evidence.message,
                "line" to evidence.line.toString(),
                "activity-extends-hold-ms" to activityExtendsHold.inWholeMilliseconds.toString(),
                "artifacts-dir" to (artifactsDir?.toString() ?: ""),
            ),
        )

    private fun finalGameplayConfirmationTimeoutArtifact(
        baseUrl: String,
        phrase: String?,
    ): String =
        smokeJson.encodeToString(
            mapOf(
                "event" to "final-gameplay-confirmation-timeout",
                "base-url" to baseUrl,
                "client-id" to SMOKE_CLIENT_ID,
                "server" to "${target.host}:${target.port}",
                "hold-ms" to holdAfterActions.inWholeMilliseconds.toString(),
                "confirmation-contains" to (phrase ?: ""),
                "activity-extends-hold-ms" to activityExtendsHold.inWholeMilliseconds.toString(),
                "artifacts-dir" to (artifactsDir?.toString() ?: ""),
            ),
        )

    private fun String.toFinalGameplayConfirmationEvidenceOrNull(line: Int): FinalGameplayConfirmationEvidence? =
        runCatching {
            val entry = smokeJson.parseToJsonElement(this).jsonObject
            val type = entry["type"]?.jsonPrimitive?.content ?: return null
            if (!type.equals("CHAT", ignoreCase = true)) {
                return null
            }
            FinalGameplayConfirmationEvidence(
                player = entry["player"]?.jsonPrimitive?.content ?: return null,
                message = entry["message"]?.jsonPrimitive?.content ?: return null,
                line = line,
            )
        }.getOrNull()

    companion object {
        private const val ENABLED = "CRAFTLESS_FABRIC_CLIENT_SMOKE"
        private const val HOST = "CRAFTLESS_SMOKE_SERVER_HOST"
        private const val PORT = "CRAFTLESS_SMOKE_SERVER_PORT"
        private const val CHAT_MESSAGE = "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE"
        private const val EQUIP_ITEM = "CRAFTLESS_FABRIC_SMOKE_EQUIP_ITEM"
        private const val REQUIRE_EQUIP_ITEM = "CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM"
        private const val CONNECT_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS"
        private const val ACTION_TIMEOUT = "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS"
        private const val FABRIC_ACTION_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS"
        private const val STARTUP_SETTLE = "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS"
        private const val HOLD_AFTER_ACTIONS = "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS"
        private const val ARTIFACTS_DIR = "CRAFTLESS_SMOKE_ARTIFACTS_DIR"
        private const val PUBLIC_AGENT_COMMAND_JSON = "CRAFTLESS_PUBLIC_AGENT_COMMAND_JSON"
        private const val PUBLIC_AGENT_COMMAND_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS"
        private const val PUBLIC_AGENT_COMMAND_TIMEOUT_LEGACY = "CRAFTLESS_PUBLIC_AGENT_COMMAND_TIMEOUT_MS"
        private const val READY_COMMAND_JSON = "CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON"
        private const val READY_REMINDER = "CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS"
        private const val CONFIRM_CHAT_CONTAINS = "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS"
        private const val ACTIVITY_EXTENDS_HOLD = "CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS"
        private const val SMOKE_CLIENT_ID = "fabric-smoke"
        private const val SMOKE_PROFILE = "CraftlessSmoke"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): FabricClientSmokeController {
            val actionTimeoutName =
                if (!env[FABRIC_ACTION_TIMEOUT].isNullOrBlank()) {
                    FABRIC_ACTION_TIMEOUT
                } else {
                    ACTION_TIMEOUT
                }
            val actionTimeout = (env[actionTimeoutName]?.toLongStrict(actionTimeoutName) ?: 30_000).milliseconds
            val publicAgentCommandTimeout =
                env[PUBLIC_AGENT_COMMAND_TIMEOUT]
                    ?.takeIf { it.isNotBlank() }
                    ?.toLongStrict(PUBLIC_AGENT_COMMAND_TIMEOUT)
                    ?.milliseconds
                    ?: env[PUBLIC_AGENT_COMMAND_TIMEOUT_LEGACY]
                        ?.takeIf { it.isNotBlank() }
                        ?.toLongStrict(PUBLIC_AGENT_COMMAND_TIMEOUT_LEGACY)
                        ?.milliseconds
                    ?: env[ACTION_TIMEOUT]
                        ?.takeIf { it.isNotBlank() }
                        ?.toLongStrict(ACTION_TIMEOUT)
                        ?.milliseconds
                    ?: actionTimeout
            return FabricClientSmokeController(
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
                actionTimeout = actionTimeout,
                publicAgentCommandTimeout = publicAgentCommandTimeout,
                startupSettleDelay = (env[STARTUP_SETTLE]?.toLongStrict(STARTUP_SETTLE) ?: 0).milliseconds,
                holdAfterActions = (env[HOLD_AFTER_ACTIONS]?.toLongStrict(HOLD_AFTER_ACTIONS) ?: 0).milliseconds,
                artifactsDir = env[ARTIFACTS_DIR]?.takeIf { it.isNotBlank() }?.let(Path::of),
                publicAgentCommand =
                    env[PUBLIC_AGENT_COMMAND_JSON]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { smokeJson.decodeFromString<List<String>>(it) }
                        ?: emptyList(),
                readyNotificationCommand =
                    env[READY_COMMAND_JSON]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { smokeJson.decodeFromString<List<String>>(it) }
                        ?: emptyList(),
                readyNotificationReminder = (env[READY_REMINDER]?.toLongStrict(READY_REMINDER) ?: 0).milliseconds,
                confirmationChatContains = env[CONFIRM_CHAT_CONTAINS]?.takeIf { it.isNotBlank() },
                activityExtendsHold =
                    (env[ACTIVITY_EXTENDS_HOLD]?.toLongStrict(ACTIVITY_EXTENDS_HOLD) ?: 0).milliseconds,
            )
        }

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
                action = FabricBootstrapOperationIds.INVENTORY_QUERY,
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

internal fun String.smokeResourceArtifactFromOpenApi(): String =
    smokeJson.encodeToString(
        smokeJson
            .decodeFromString<OpenApiDocument>(this)
            .resources,
    )

internal fun MutableMap<String, String>.removeInheritedSmokeOwnerEnvironment() {
    inheritedSmokeOwnerEnvironmentKeys.forEach(::remove)
}

private val inheritedSmokeOwnerEnvironmentKeys =
    setOf(
        "CRAFTLESS_LOCAL_SERVER_SMOKE",
        "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT",
        "CRAFTLESS_LOCAL_SERVER_SMOKE_ACTION_TIMEOUT_MS",
        "CRAFTLESS_FABRIC_CLIENT_SMOKE",
        "CRAFTLESS_FINAL_GAMEPLAY",
        "CRAFTLESS_SMOKE_MINECRAFT_VERSION",
        "CRAFTLESS_SMOKE_RUNTIME_LANE_JSON",
        "CRAFTLESS_SMOKE_RUNTIME_LANE_FILE",
        "CRAFTLESS_SMOKE_JAVA_EXECUTABLE",
        "CRAFTLESS_SMOKE_JAVA_SELECTION_JSON",
        "CRAFTLESS_SMOKE_JAVA_SELECTION_FILE",
        "CRAFTLESS_SMOKE_SERVER_HOST",
        "CRAFTLESS_SMOKE_SERVER_PORT",
        "CRAFTLESS_SMOKE_ARTIFACTS_DIR",
        "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON",
        "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS",
        "CRAFTLESS_SMOKE_EXPECT_PLAYER",
        "CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE",
        "CRAFTLESS_SMOKE_EXPECT_DISCONNECT",
        "CRAFTLESS_SMOKE_PROVISION_ITEM_ID",
        "CRAFTLESS_SMOKE_PROVISION_ITEM_NAME",
        "CRAFTLESS_SMOKE_PROVISION_ITEM_COUNT",
        "CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS",
        "CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS",
        "CRAFTLESS_SMOKE_MIN_HEAP",
        "CRAFTLESS_SMOKE_MAX_HEAP",
        "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE",
        "CRAFTLESS_FABRIC_SMOKE_EQUIP_ITEM",
        "CRAFTLESS_FABRIC_SMOKE_REQUIRE_EQUIP_ITEM",
        "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS",
        "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS",
        "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS",
        "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS",
        "CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS",
        "CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON",
        "CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS",
        "CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS",
        "CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS",
    )

private fun publicAgentGameplayResults(generatedGameplayResults: List<String>): List<String> =
    listOf(
        """{"event":"public-agent-gameplay","message":"invoked generated Craftless actions through POST /clients/{id}:run"}""",
    ) +
        generatedGameplayResults.filterNot { result ->
            result.contains("task.survival") ||
                result.contains("\"action\":\"task.run\"") ||
                result.contains("\"action\":\"task.status\"")
        }

private fun publicAgentStateResults(openApi: String): List<String> {
    val availableActions =
        smokeJson
            .decodeFromString<OpenApiDocument>(openApi)
            .actions
            .filter { action -> action.availability == OpenApiActionAvailability.AVAILABLE }
            .map { action -> action.id }
            .toSet()
    val missing = PUBLIC_AGENT_REQUIRED_ACTIONS.filter { action -> action !in availableActions }
    val availableActionIds = smokeJson.encodeToString(availableActions.sorted())
    val missingActionIds = smokeJson.encodeToString(missing)
    return listOf(
        """{"event":"public-agent-discovery","message":"fetched live per-client OpenAPI and action projection",""" +
            """"available-actions":$availableActionIds}""",
        """{"event":"public-agent-missing-generic-primitive-check","message":"validated public-agent primitive set",""" +
            """"missing-generic-primitives":$missingActionIds}""",
    )
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

private val PUBLIC_AGENT_REQUIRED_ACTIONS =
    listOf(
        FabricBootstrapOperationIds.ENTITY_QUERY,
        FabricBootstrapOperationIds.INVENTORY_QUERY,
        FabricNavigationOperationIds.PLAN,
        FabricNavigationOperationIds.FOLLOW,
        FabricBootstrapOperationIds.PLAYER_LOOK,
        FabricBootstrapOperationIds.PLAYER_RAYCAST,
        FabricBootstrapOperationIds.WORLD_BLOCK_BREAK,
    )

internal fun FabricClientGateway.awaitConnected(
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

internal fun FabricClientGateway.awaitReadyToConnect(
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
