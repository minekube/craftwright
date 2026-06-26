package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import kotlin.math.atan2
import kotlin.math.sqrt

class PublicAgentGameplayRunner(
    private val baseUrl: String,
    private val clientId: String,
    private val http: HttpClient = HttpClient(CIO),
) {
    suspend fun runOnce(artifactsDir: Path? = null): PublicAgentGameplayResult {
        val supervisorSpec = http.get("$baseUrl/openapi.json").bodyAsText()
        val clientSpec = http.get("$baseUrl/clients/$clientId/openapi.json").bodyAsText()
        val actions = http.get("$baseUrl/clients/$clientId/actions").bodyAsText()
        val eventStream = http.get("$baseUrl/clients/$clientId/events:stream").bodyAsText()
        val actionIds = actions.actionIds()
        val missingAction = requiredActions.firstOrNull { it !in actionIds }
        if (missingAction != null) {
            val result =
                PublicAgentGameplayResult(
                    state = PublicAgentGameplayState.BLOCKED,
                    blocker = "missing-generic-primitive:$missingAction",
                    supervisorSpec = supervisorSpec,
                    clientSpec = clientSpec,
                    actions = actions,
                    eventStream = eventStream,
                    availableActions = actionIds.sorted(),
                )
            artifactsDir?.let { writeArtifacts(it, result) }
            return result
        }
        val actionLog = mutableListOf<PublicAgentActionLog>()

        fun blocked(blocker: String): PublicAgentGameplayResult =
            PublicAgentGameplayResult(
                state = PublicAgentGameplayState.BLOCKED,
                blocker = blocker,
                supervisorSpec = supervisorSpec,
                clientSpec = clientSpec,
                actions = actions,
                eventStream = eventStream,
                actionLog = actionLog.toList(),
                availableActions = actionIds.sorted(),
            )

        suspend fun invokeGenerated(
            action: String,
            args: JsonObject = JsonObject(emptyMap()),
        ): PublicAgentActionLog {
            val invocationResult =
                http
                    .post("$baseUrl/clients/$clientId:run") {
                        contentType(ContentType.Application.Json)
                        setBody(publicAgentInvocation(action, args))
                    }.bodyAsText()
            return PublicAgentActionLog(action = action, response = invocationResult)
                .also(actionLog::add)
        }

        invokeGenerated("inventory.query")
        val blockQuery =
            invokeGenerated(
                action = "world.block.query",
                args =
                    buildJsonObject {
                        put("radius", JsonPrimitive(32.0))
                        put("limit", JsonPrimitive(16))
                        put("category", JsonPrimitive("log"))
                    },
            )
        val materialPosition =
            blockQuery.responseObject()?.firstBlockPosition()
                ?: return blocked("insufficient-public-evidence:world.block.query.log")
                    .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }
        val materialPoint =
            materialPosition.toCraftlessPoint()
                ?: return blocked("insufficient-public-evidence:world.block.query.position")
                    .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }
        val plan =
            invokeGenerated(
                action = "navigation.plan",
                args =
                    buildJsonObject {
                        put(
                            "goal",
                            buildJsonObject {
                                put("kind", JsonPrimitive("block"))
                                put("position", materialPosition)
                                put("radius", JsonPrimitive(2.0))
                            },
                        )
                    },
            )
        val planId =
            plan.responseObject()?.planId()
                ?: return blocked("insufficient-public-evidence:navigation.plan")
                    .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }
        invokeGenerated(
            action = "navigation.follow",
            args =
                buildJsonObject {
                    put(
                        "plan",
                        buildJsonObject {
                            put("id", JsonPrimitive(planId))
                        },
                    )
                },
        )
        val player =
            invokeGenerated("player.query")
        val playerPosition =
            player.responseObject()?.playerPosition()
                ?: return blocked("insufficient-public-evidence:player.query.position")
                    .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }
        val look = playerPosition.lookAt(materialPoint.centered())
        invokeGenerated(
            action = "player.look",
            args =
                buildJsonObject {
                    put("yaw", JsonPrimitive(look.yaw))
                    put("pitch", JsonPrimitive(look.pitch))
                },
        )
        invokeGenerated(
            action = "player.raycast",
            args =
                buildJsonObject {
                    put("max-distance", JsonPrimitive(6.0))
                    put("include-fluids", JsonPrimitive(false))
                },
        )
        invokeGenerated(
            action = "world.block.break",
            args =
                buildJsonObject {
                    put("max-distance", JsonPrimitive(6.0))
                    put("include-fluids", JsonPrimitive(false))
                },
        )
        val finalInventory = invokeGenerated("inventory.query")
        if (finalInventory.responseObject()?.hasLogItem() != true) {
            return blocked("insufficient-public-evidence:inventory.query.log")
                .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }
        }
        invokeGenerated("entity.query")
        val result =
            PublicAgentGameplayResult(
                state = PublicAgentGameplayState.RAN,
                supervisorSpec = supervisorSpec,
                clientSpec = clientSpec,
                actions = actions,
                eventStream = eventStream,
                actionLog = actionLog,
                availableActions = actionIds.sorted(),
            )
        artifactsDir?.let { writeArtifacts(it, result) }
        return result
    }

    private fun writeArtifacts(
        artifactsDir: Path,
        result: PublicAgentGameplayResult,
    ) {
        Files.createDirectories(artifactsDir)
        Files.writeString(
            artifactsDir.resolve("public-agent-state.jsonl"),
            stateArtifactLines(result).joinToString(separator = "\n", postfix = "\n"),
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )
        Files.writeString(
            artifactsDir.resolve("public-agent-gameplay-results.jsonl"),
            gameplayArtifactLines(result).joinToString(separator = "\n", postfix = "\n"),
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )
    }

    private fun stateArtifactLines(result: PublicAgentGameplayResult): List<String> =
        listOf(
            artifactLine(
                "event" to JsonPrimitive("public-agent-discovery"),
                "clientId" to JsonPrimitive(clientId),
                "request" to JsonPrimitive("GET /openapi.json"),
            ),
            artifactLine(
                "event" to JsonPrimitive("public-agent-discovery"),
                "clientId" to JsonPrimitive(clientId),
                "request" to JsonPrimitive("GET /clients/$clientId/openapi.json"),
            ),
            artifactLine(
                "event" to JsonPrimitive("public-agent-discovery"),
                "clientId" to JsonPrimitive(clientId),
                "request" to JsonPrimitive("GET /clients/$clientId/actions"),
                "availableActions" to JsonArray(result.availableActions.map(::JsonPrimitive)),
            ),
            artifactLine(
                "event" to JsonPrimitive("public-agent-stream"),
                "clientId" to JsonPrimitive(clientId),
                "request" to JsonPrimitive("GET /clients/$clientId/events:stream"),
                "bytes" to JsonPrimitive(result.eventStream.length),
            ),
        )

    private fun gameplayArtifactLines(result: PublicAgentGameplayResult): List<String> =
        when (result.state) {
            PublicAgentGameplayState.BLOCKED ->
                result.actionLog.toArtifactLines() +
                    listOf(
                        artifactLine(
                            "event" to JsonPrimitive("public-agent-blocked"),
                            "clientId" to JsonPrimitive(clientId),
                            "blocker" to JsonPrimitive(result.blocker ?: "unknown-blocker"),
                        ),
                    )
            PublicAgentGameplayState.RAN ->
                result.actionLog.toArtifactLines()
        }

    private fun List<PublicAgentActionLog>.toArtifactLines(): List<String> =
        map { action ->
            artifactLine(
                "event" to JsonPrimitive("public-agent-action"),
                "clientId" to JsonPrimitive(clientId),
                "action" to JsonPrimitive(action.action),
                "response" to JsonPrimitive(action.response),
            )
        }

    private fun artifactLine(vararg entries: Pair<String, JsonElement>): String =
        publicAgentJson.encodeToString(
            JsonObject.serializer(),
            JsonObject(entries.toMap()),
        )

    private fun publicAgentInvocation(
        action: String,
        args: JsonObject = JsonObject(emptyMap()),
    ): String =
        publicAgentJson.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("action", JsonPrimitive(action))
                put("args", args)
            },
        )
}

data class PublicAgentGameplayRunnerConfig(
    val baseUrl: String,
    val clientId: String = "fabric-smoke",
    val artifactsDir: Path = Path.of("build", "craftless-public-agent-gameplay", "artifacts"),
) {
    init {
        require(baseUrl.isNotBlank()) { "public agent base URL is required" }
        require(clientId.isNotBlank()) { "public agent client id is required" }
    }

    companion object {
        private const val BASE_URL = "CRAFTLESS_PUBLIC_AGENT_BASE_URL"
        private const val CLIENT_ID = "CRAFTLESS_PUBLIC_AGENT_CLIENT_ID"
        private const val ARTIFACTS_DIR = "CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): PublicAgentGameplayRunnerConfig =
            PublicAgentGameplayRunnerConfig(
                baseUrl = env[BASE_URL]?.takeIf { it.isNotBlank() } ?: error("$BASE_URL is required"),
                clientId = env[CLIENT_ID]?.takeIf { it.isNotBlank() } ?: "fabric-smoke",
                artifactsDir =
                    env[ARTIFACTS_DIR]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Path::of)
                        ?: Path.of("build", "craftless-public-agent-gameplay", "artifacts"),
            )
    }
}

data class PublicAgentGameplayResult(
    val state: PublicAgentGameplayState,
    val blocker: String? = null,
    val supervisorSpec: String,
    val clientSpec: String,
    val actions: String,
    val eventStream: String,
    val actionLog: List<PublicAgentActionLog> = emptyList(),
    val availableActions: List<String> = emptyList(),
)

data class PublicAgentActionLog(
    val action: String,
    val response: String,
)

enum class PublicAgentGameplayState {
    RAN,
    BLOCKED,
}

private fun String.actionIds(): Set<String> =
    when (val parsed = publicAgentJson.parseToJsonElement(this)) {
        is JsonArray ->
            parsed
                .mapNotNull { element -> element.jsonObject["id"]?.jsonPrimitive?.content }
                .toSet()
        else -> emptySet()
    }

private val requiredActions =
    listOf(
        "entity.query",
        "inventory.query",
        "navigation.plan",
        "navigation.follow",
        "player.query",
        "player.look",
        "player.raycast",
        "world.block.query",
        "world.block.break",
    )

private val publicAgentJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private fun PublicAgentActionLog.responseObject(): JsonObject? =
    runCatching { publicAgentJson.parseToJsonElement(response).jsonObject }.getOrNull()

private fun JsonObject.firstBlockPosition(): JsonObject? {
    val data = this["data"] as? JsonObject ?: return null
    val blocks = data["blocks"]?.jsonArray ?: return null
    val block = blocks.firstOrNull() as? JsonObject ?: return null
    return block["position"] as? JsonObject
}

private fun JsonObject.playerPosition(): CraftlessPoint? {
    val data = this["data"] as? JsonObject ?: return null
    val position = data["position"] as? JsonObject ?: return null
    return position.toCraftlessPoint()
}

private fun JsonObject.toCraftlessPoint(): CraftlessPoint? {
    val x = doubleField("x") ?: return null
    val y = doubleField("y") ?: return null
    val z = doubleField("z") ?: return null
    return CraftlessPoint(x, y, z)
}

private fun JsonObject.hasLogItem(): Boolean {
    val data = this["data"] as? JsonObject ?: return false
    val slots = data["slots"]?.jsonArray ?: return false
    return slots.any { element ->
        val slot = element as? JsonObject ?: return@any false
        slot["item-name"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.contains("log", ignoreCase = true) == true
    }
}

private fun JsonObject.planId(): String? {
    val data = this["data"] as? JsonObject ?: return null
    return data["plan-id"]?.jsonPrimitive?.contentOrNull
        ?: data["id"]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.doubleField(name: String): Double? =
    this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toDoubleOrNull()

private data class CraftlessPoint(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    fun centered(): CraftlessPoint = copy(x = x + 0.5, y = y + 0.5, z = z + 0.5)

    fun lookAt(target: CraftlessPoint): CraftlessLook {
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(dz, dx)) - 90.0
        val pitch = -Math.toDegrees(atan2(dy, horizontal))
        return CraftlessLook(yaw = yaw, pitch = pitch.coerceIn(-90.0, 90.0))
    }
}

private data class CraftlessLook(
    val yaw: Double,
    val pitch: Double,
)

fun main() {
    val config = PublicAgentGameplayRunnerConfig.fromEnvironment()
    runBlocking {
        HttpClient(CIO).use { http ->
            val result =
                PublicAgentGameplayRunner(
                    baseUrl = config.baseUrl,
                    clientId = config.clientId,
                    http = http,
                ).runOnce(artifactsDir = config.artifactsDir)
            println("publicAgentState=${result.state}")
            result.blocker?.let { blocker -> println("publicAgentBlocker=$blocker") }
            println("publicAgentArtifacts=${config.artifactsDir}")
        }
    }
}
