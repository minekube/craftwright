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
import java.io.IOException
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

        fun blockedAndWrite(blocker: String): PublicAgentGameplayResult =
            blocked(blocker)
                .also { result -> artifactsDir?.let { writeArtifacts(it, result) } }

        suspend fun invokeGenerated(
            action: String,
            args: JsonObject = JsonObject(emptyMap()),
        ): PublicAgentActionLog {
            val invocationResult =
                try {
                    http
                        .post("$baseUrl/clients/$clientId:run") {
                            contentType(ContentType.Application.Json)
                            setBody(publicAgentInvocation(action, args))
                        }.bodyAsText()
                } catch (failure: IOException) {
                    val blocker = "action-request-failed:$action"
                    val failedResponse =
                        publicAgentJson.encodeToString(
                            JsonObject.serializer(),
                            buildJsonObject {
                                put("action", JsonPrimitive(action))
                                put("status", JsonPrimitive("FAILED"))
                                put("message", JsonPrimitive(failure.message ?: failure::class.simpleName.orEmpty()))
                                put("blocker", JsonPrimitive(blocker))
                            },
                        )
                    PublicAgentActionLog(action = action, response = failedResponse)
                        .also(actionLog::add)
                    throw PublicAgentActionRequestFailure(blocker, failure)
                }
            return PublicAgentActionLog(action = action, response = invocationResult)
                .also(actionLog::add)
        }

        suspend fun queryMaterialTarget(): PublicBlockTarget? =
            invokeGenerated(
                action = "world.block.query",
                args =
                    buildJsonObject {
                        put("radius", JsonPrimitive(32.0))
                        put("limit", JsonPrimitive(16))
                        put("category", JsonPrimitive("log"))
                    },
            ).responseObject()?.materialBlockTarget()

        suspend fun navigateTo(
            position: JsonObject,
            radius: Double,
        ): String? {
            val plan =
                invokeGenerated(
                    action = "navigation.plan",
                    args =
                        buildJsonObject {
                            put(
                                "goal",
                                buildJsonObject {
                                    put("kind", JsonPrimitive("block"))
                                    put("position", position)
                                    put("radius", JsonPrimitive(radius))
                                },
                            )
                        },
                )
            val planId = plan.responseObject()?.planId() ?: return "insufficient-public-evidence:navigation.plan"
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
            return null
        }

        try {
            invokeGenerated("inventory.query")
            var materialTarget = queryMaterialTarget()
            if (materialTarget == null) {
                val player = invokeGenerated("player.query")
                val origin =
                    player.responseObject()?.playerPosition()
                        ?: return blockedAndWrite("insufficient-public-evidence:player.query.position")
                for (waypoint in origin.explorationWaypoints()) {
                    navigateTo(position = waypoint.toJsonObject(), radius = 4.0)
                        ?.let { blocker -> return blockedAndWrite(blocker) }
                    materialTarget = queryMaterialTarget()
                    if (materialTarget != null) break
                }
            }
            val discoveredMaterialTarget =
                materialTarget
                    ?: return blockedAndWrite("insufficient-public-evidence:world.block.query.log")
            val discoveredMaterialPosition = discoveredMaterialTarget.position
            val materialPoint =
                discoveredMaterialPosition.toCraftlessPoint()
                    ?: return blockedAndWrite("insufficient-public-evidence:world.block.query.position")
            navigateTo(position = discoveredMaterialPosition, radius = 2.0)
                ?.let { blocker -> return blockedAndWrite(blocker) }
            val player =
                invokeGenerated("player.query")
            val playerPosition =
                player.responseObject()?.playerPosition()
                    ?: return blockedAndWrite("insufficient-public-evidence:player.query.position")
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
            val breakResult =
                invokeGenerated(
                    action = "world.block.break",
                    args =
                        buildJsonObject {
                            put("max-distance", JsonPrimitive(6.0))
                            put("include-fluids", JsonPrimitive(false))
                            put("ticks", JsonPrimitive(80))
                            put("target", discoveredMaterialTarget.toJsonObject())
                        },
                )
            if (breakResult.responseObject()?.dataBoolean("changed") == false) {
                return blockedAndWrite("insufficient-public-evidence:world.block.break.changed")
            }
            navigateTo(position = discoveredMaterialPosition, radius = 1.5)
                ?.let { blocker -> return blockedAndWrite(blocker) }
            var finalInventory: PublicAgentActionLog? = null
            for (attempt in 1..PICKUP_EVIDENCE_ATTEMPTS) {
                val materialDrop =
                    invokeGenerated(
                        action = "entity.query",
                        args =
                            buildJsonObject {
                                put("radius", JsonPrimitive(16.0))
                                put("limit", JsonPrimitive(20))
                            },
                    )
                materialDrop.responseObject()?.materialDropPosition()?.let { dropPosition ->
                    navigateTo(position = dropPosition, radius = 1.0)
                        ?.let { blocker -> return blockedAndWrite(blocker) }
                }
                finalInventory = invokeGenerated("inventory.query")
                if (finalInventory.responseObject()?.hasLogItem() == true) {
                    break
                }
                if (attempt < PICKUP_EVIDENCE_ATTEMPTS) {
                    navigateTo(position = discoveredMaterialPosition, radius = 1.0)
                        ?.let { blocker -> return blockedAndWrite(blocker) }
                }
            }
            val finalInventoryObject = finalInventory?.responseObject()
            if (finalInventoryObject?.hasLogItem() != true) {
                return blockedAndWrite("insufficient-public-evidence:inventory.query.log")
            }
            val logSlot =
                finalInventoryObject.logHotbarSlot()
                    ?: return blockedAndWrite("insufficient-public-evidence:inventory.query.hotbar-log")
            invokeGenerated(
                action = "inventory.equip",
                args =
                    buildJsonObject {
                        put("slot", JsonPrimitive(logSlot))
                    },
            )
            val equippedInventory = invokeGenerated("inventory.query")
            if (equippedInventory.responseObject()?.selectedSlot() != logSlot) {
                return blockedAndWrite("insufficient-public-evidence:inventory.equip.selected-slot")
            }
            if (actions.actionSupportsArgument("world.block.interact", "target")) {
                val supportTarget =
                    invokeGenerated(
                        action = "world.block.query",
                        args =
                            buildJsonObject {
                                put("radius", JsonPrimitive(8.0))
                                put("limit", JsonPrimitive(16))
                                put("category", JsonPrimitive("block"))
                            },
                    ).responseObject()?.supportBlockTarget()
                if (supportTarget != null) {
                    navigateTo(position = supportTarget.position, radius = 2.5)
                        ?.let { blocker -> return blockedAndWrite(blocker) }
                    val interactResult =
                        invokeGenerated(
                            action = "world.block.interact",
                            args =
                                buildJsonObject {
                                    put("max-distance", JsonPrimitive(6.0))
                                    put("side", JsonPrimitive("up"))
                                    put("target", supportTarget.toJsonObject())
                                },
                        )
                    if (interactResult.responseObject()?.dataBoolean("changed") == false) {
                        return blockedAndWrite("insufficient-public-evidence:world.block.interact.changed")
                    }
                }
            }
            val finalEntityQuery = invokeGenerated("entity.query")
            if ("entity.attack" in actionIds) {
                val attackTarget = finalEntityQuery.responseObject()?.attackTarget()
                if (attackTarget != null) {
                    attackTarget.position?.let { position ->
                        navigateTo(position = position, radius = 2.5)
                            ?.let { blocker -> return blockedAndWrite(blocker) }
                        val combatPlayer =
                            invokeGenerated("player.query")
                        val combatPlayerPosition =
                            combatPlayer.responseObject()?.playerPosition()
                                ?: return blockedAndWrite("insufficient-public-evidence:player.query.position")
                        position.toCraftlessPoint()?.let { targetPoint ->
                            val combatLook = combatPlayerPosition.lookAt(targetPoint)
                            invokeGenerated(
                                action = "player.look",
                                args =
                                    buildJsonObject {
                                        put("yaw", JsonPrimitive(combatLook.yaw))
                                        put("pitch", JsonPrimitive(combatLook.pitch))
                                    },
                            )
                        }
                    }
                    val attackResult =
                        invokeGenerated(
                            action = "entity.attack",
                            args =
                                buildJsonObject {
                                    put(
                                        "target",
                                        buildJsonObject {
                                            put("handle", JsonPrimitive(attackTarget.handle))
                                        },
                                    )
                                    put("max-distance", JsonPrimitive(4.5))
                                },
                        )
                    if (attackResult.responseObject()?.dataBoolean("hit") == false) {
                        return blockedAndWrite("insufficient-public-evidence:entity.attack.hit")
                    }
                }
            }
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
        } catch (failure: PublicAgentActionRequestFailure) {
            return blockedAndWrite(failure.blocker)
        }
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

private class PublicAgentActionRequestFailure(
    val blocker: String,
    cause: Throwable,
) : RuntimeException(blocker, cause)

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

private fun String.actionSupportsArgument(
    action: String,
    argument: String,
): Boolean =
    when (val parsed = publicAgentJson.parseToJsonElement(this)) {
        is JsonArray ->
            parsed.any { element ->
                val descriptor = element.jsonObject
                descriptor["id"]?.jsonPrimitive?.content == action &&
                    descriptor["args"]?.jsonObject?.containsKey(argument) == true
            }
        else -> false
    }

private val requiredActions =
    listOf(
        "entity.query",
        "inventory.query",
        "inventory.equip",
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

private fun JsonObject.materialBlockTarget(): PublicBlockTarget? {
    val data = this["data"] as? JsonObject ?: return null
    val blocks = data["blocks"]?.jsonArray ?: return null
    return blocks
        .mapNotNull { element ->
            val block = element as? JsonObject ?: return@mapNotNull null
            val position = block["position"] as? JsonObject ?: return@mapNotNull null
            PublicBlockTarget(
                handle = block["handle"]?.jsonPrimitive?.contentOrNull,
                position = position,
                distance = block.doubleField("distance"),
            )
        }.minWithOrNull(
            compareBy<PublicBlockTarget> { target ->
                target.position.toCraftlessPoint()?.y ?: Double.MAX_VALUE
            }.thenBy { target ->
                target.distance ?: Double.MAX_VALUE
            },
        )
}

private fun JsonObject.supportBlockTarget(): PublicBlockTarget? = materialBlockTarget()

private fun JsonObject.playerPosition(): CraftlessPoint? {
    val data = this["data"] as? JsonObject ?: return null
    val position = data["position"] as? JsonObject ?: return null
    return position.toCraftlessPoint()
}

private fun JsonObject.materialDropPosition(): JsonObject? {
    val data = this["data"] as? JsonObject ?: return null
    val entities = data["entities"]?.jsonArray ?: return null
    return entities
        .mapNotNull { element ->
            val entity = element as? JsonObject ?: return@mapNotNull null
            val position = entity["position"] as? JsonObject ?: return@mapNotNull null
            val label = entity["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!label.contains("log", ignoreCase = true)) {
                return@mapNotNull null
            }
            val distance = entity.doubleField("distance") ?: Double.MAX_VALUE
            distance to position
        }.minByOrNull { (distance, _) -> distance }
        ?.second
}

private fun JsonObject.attackTarget(): PublicEntityTarget? {
    val data = this["data"] as? JsonObject ?: return null
    val entities = data["entities"]?.jsonArray ?: return null
    return entities
        .mapNotNull { element ->
            val entity = element as? JsonObject ?: return@mapNotNull null
            val handle =
                entity["handle"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
            val category = entity["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (category !in attackableEntityCategories) {
                return@mapNotNull null
            }
            val alive =
                entity["alive"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
                    ?: true
            if (!alive) {
                return@mapNotNull null
            }
            PublicEntityTarget(
                handle = handle,
                position = entity["position"] as? JsonObject,
                distance = entity.doubleField("distance"),
            )
        }.minByOrNull { target -> target.distance ?: Double.MAX_VALUE }
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
        slot.containsItemName("log")
    }
}

private fun JsonObject.logHotbarSlot(): Int? {
    val data = this["data"] as? JsonObject ?: return null
    val slots = data["slots"]?.jsonArray ?: return null
    return slots.firstNotNullOfOrNull { element ->
        val slot = element as? JsonObject ?: return@firstNotNullOfOrNull null
        val slotNumber =
            slot["slot"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
                ?: return@firstNotNullOfOrNull null
        val empty =
            slot["empty"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toBooleanStrictOrNull()
                ?: true
        if (slotNumber in 0..8 && !empty && slot.containsItemName("log")) {
            slotNumber
        } else {
            null
        }
    }
}

private fun JsonObject.selectedSlot(): Int? {
    val data = this["data"] as? JsonObject ?: return null
    return data["selected-slot"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toIntOrNull()
}

private fun JsonObject.dataBoolean(name: String): Boolean? {
    val data = this["data"] as? JsonObject ?: return null
    return data[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toBooleanStrictOrNull()
}

private fun JsonObject.containsItemName(part: String): Boolean =
    this["item-name"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.contains(part, ignoreCase = true) == true

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

    fun toJsonObject(): JsonObject =
        buildJsonObject {
            put("x", JsonPrimitive(x))
            put("y", JsonPrimitive(y))
            put("z", JsonPrimitive(z))
        }

    fun explorationWaypoints(): List<CraftlessPoint> =
        listOf(
            copy(x = x + MATERIAL_EXPLORATION_STEP),
            copy(z = z + MATERIAL_EXPLORATION_STEP),
            copy(x = x - MATERIAL_EXPLORATION_STEP),
            copy(z = z - MATERIAL_EXPLORATION_STEP),
            copy(x = x + MATERIAL_EXPLORATION_STEP, z = z + MATERIAL_EXPLORATION_STEP),
            copy(x = x - MATERIAL_EXPLORATION_STEP, z = z + MATERIAL_EXPLORATION_STEP),
            copy(x = x + MATERIAL_EXPLORATION_STEP, z = z - MATERIAL_EXPLORATION_STEP),
            copy(x = x - MATERIAL_EXPLORATION_STEP, z = z - MATERIAL_EXPLORATION_STEP),
        )

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

private const val MATERIAL_EXPLORATION_STEP = 24.0
private const val PICKUP_EVIDENCE_ATTEMPTS = 4

private data class CraftlessLook(
    val yaw: Double,
    val pitch: Double,
)

private data class PublicBlockTarget(
    val handle: String?,
    val position: JsonObject,
    val distance: Double?,
) {
    fun toJsonObject(): JsonObject =
        buildJsonObject {
            handle?.let { put("handle", JsonPrimitive(it)) }
            put("position", position)
        }
}

private data class PublicEntityTarget(
    val handle: String,
    val position: JsonObject?,
    val distance: Double?,
)

private val attackableEntityCategories = setOf("passive", "hostile", "living")

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
