package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class PublicAgentGameplayRunner(
    private val baseUrl: String,
    private val clientId: String,
    private val http: HttpClient = publicAgentHttpClient(DEFAULT_ACTION_REQUEST_TIMEOUT_MS),
    private val combatPause: suspend () -> Unit = {},
    private val combatEvidenceAttempts: Int = DEFAULT_COMBAT_EVIDENCE_ATTEMPTS,
) {
    init {
        require(combatEvidenceAttempts > 0) { "combat evidence attempts must be positive" }
    }

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

        suspend fun queryAttackTarget(
            radius: Double = 24.0,
            preferredHandle: String? = null,
        ): PublicEntityTarget? =
            invokeGenerated(
                action = "entity.query",
                args =
                    buildJsonObject {
                        put("radius", JsonPrimitive(radius))
                        put("limit", JsonPrimitive(32))
                    },
            ).responseObject()?.attackTarget(preferredHandle = preferredHandle)

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
            val follow =
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
            if (follow.responseObject()?.navigationSucceeded() != true) {
                val playerPosition =
                    invokeGenerated("player.query")
                        .responseObject()
                        ?.playerPosition()
                val goalPosition = position.toCraftlessPoint()
                if (playerPosition != null && goalPosition != null && playerPosition.distanceTo(goalPosition) <= radius) {
                    return null
                }
                return "insufficient-public-evidence:navigation.follow.succeeded"
            }
            return null
        }

        suspend fun moveToward(
            position: JsonObject,
            ticks: Int,
        ): String? {
            if ("player.move" !in actionIds) {
                return "missing-generic-primitive:player.move"
            }
            val playerPosition =
                invokeGenerated("player.query")
                    .responseObject()
                    ?.playerPosition()
                    ?: return "insufficient-public-evidence:player.query.position"
            position.toCraftlessPoint()?.let { targetPoint ->
                val look = playerPosition.lookAt(targetPoint)
                invokeGenerated(
                    action = "player.look",
                    args =
                        buildJsonObject {
                            put("yaw", JsonPrimitive(look.yaw))
                            put("pitch", JsonPrimitive(look.pitch))
                        },
                )
            }
            invokeGenerated(
                action = "player.move",
                args =
                    buildJsonObject {
                        put("forward", JsonPrimitive(true))
                        put("ticks", JsonPrimitive(ticks))
                    },
            )
            return null
        }

        suspend fun exploreAttackTarget(): PublicEntityTarget? {
            queryAttackTarget()?.let { target -> return target }
            val player = invokeGenerated("player.query")
            val origin =
                player.responseObject()?.playerPosition()
                    ?: return null
            for (waypoint in origin.explorationWaypoints(rings = ATTACK_EXPLORATION_RINGS)) {
                if (navigateTo(position = waypoint.toJsonObject(), radius = 6.0) != null) {
                    continue
                }
                queryAttackTarget()?.let { target -> return target }
            }
            return null
        }

        suspend fun focusAttackTarget(target: PublicEntityTarget): FocusedAttackTarget {
            var focusedTarget = target
            var combatPlayerPosition =
                invokeGenerated("player.query")
                    .responseObject()
                    ?.playerPosition()
                    ?: return FocusedAttackTarget(blocker = "insufficient-public-evidence:player.query.position")

            suspend fun closeDistanceToAttackTarget(position: JsonObject): String? {
                val navigationBlocker = navigateTo(position = position, radius = 2.5)
                if (navigationBlocker == null) {
                    return null
                }
                if ("player.move" !in actionIds) {
                    return navigationBlocker
                }
                moveToward(position = position, ticks = COMBAT_MOVE_TICKS)?.let { blocker -> return blocker }
                return null
            }

            var focusAttempts = 0
            var focusComplete = false
            while (focusAttempts < COMBAT_FOCUS_ATTEMPTS && !focusComplete) {
                val closeTarget = queryAttackTarget(radius = ATTACK_MAX_DISTANCE, preferredHandle = focusedTarget.handle)
                when {
                    closeTarget != null && closeTarget.isReachableForAttack(combatPlayerPosition) -> {
                        focusedTarget = closeTarget
                        focusComplete = true
                    }
                    closeTarget == null && focusedTarget.isReachableForAttack(combatPlayerPosition) -> {
                        focusComplete = true
                    }
                    else -> {
                        val movementTarget = closeTarget ?: focusedTarget
                        movementTarget.position
                            ?.let { position -> closeDistanceToAttackTarget(position) }
                            ?.let { blocker -> return FocusedAttackTarget(blocker = blocker) }
                        combatPlayerPosition =
                            invokeGenerated("player.query")
                                .responseObject()
                                ?.playerPosition()
                                ?: return FocusedAttackTarget(blocker = "insufficient-public-evidence:player.query.position")
                        focusedTarget =
                            queryAttackTarget(radius = ATTACK_MAX_DISTANCE, preferredHandle = movementTarget.handle)
                                ?: queryAttackTarget(radius = 24.0, preferredHandle = movementTarget.handle)
                                ?: movementTarget
                        focusAttempts += 1
                    }
                }
            }
            if (!focusedTarget.isReachableForAttack(combatPlayerPosition)) {
                return FocusedAttackTarget(blocker = "insufficient-public-evidence:entity.query.attack-target.reachable")
            }
            val focusedPosition =
                focusedTarget.position
                    ?: return FocusedAttackTarget(blocker = "insufficient-public-evidence:entity.query.attack-target")
            focusedPosition.toCraftlessPoint()?.let { targetPoint ->
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
            return FocusedAttackTarget(target = focusedTarget)
        }

        suspend fun collectVisibleCombatLoot(combatEntityState: JsonObject): String? {
            val lootPosition = combatEntityState.combatLootDropPosition() ?: return null
            val navigationBlocker = navigateTo(position = lootPosition, radius = 1.0)
            if (navigationBlocker == null) {
                return null
            }
            if ("player.move" !in actionIds) {
                return navigationBlocker
            }
            return moveToward(position = lootPosition, ticks = PICKUP_MOVE_TICKS)
        }

        suspend fun reachableMaterialTarget(): FocusedBlockTarget {
            var blocker: String? = null

            suspend fun tryMaterialTarget(target: PublicBlockTarget?): FocusedBlockTarget? {
                target ?: return null
                val navigationBlocker = navigateTo(position = target.position, radius = 2.0)
                if (navigationBlocker == null) {
                    return FocusedBlockTarget(target = target)
                }
                blocker = navigationBlocker
                return null
            }

            tryMaterialTarget(queryMaterialTarget())?.let { target -> return target }

            val player = invokeGenerated("player.query")
            val origin =
                player.responseObject()?.playerPosition()
                    ?: return FocusedBlockTarget(blocker = "insufficient-public-evidence:player.query.position")
            for (waypoint in origin.explorationWaypoints()) {
                val explorationBlocker = navigateTo(position = waypoint.toJsonObject(), radius = 4.0)
                if (explorationBlocker != null) {
                    blocker = explorationBlocker
                    continue
                }
                tryMaterialTarget(queryMaterialTarget())?.let { target -> return target }
            }

            return FocusedBlockTarget(blocker = blocker ?: "insufficient-public-evidence:world.block.query.log")
        }

        try {
            invokeGenerated("inventory.query")

            suspend fun collectMaterialInventory(minimumLogCountExclusive: Int): MaterialCollectionAttempt {
                val materialTarget = reachableMaterialTarget()
                materialTarget.blocker?.let { blocker -> return MaterialCollectionAttempt(blocker = blocker) }
                val discoveredMaterialTarget =
                    materialTarget.target
                        ?: return MaterialCollectionAttempt(blocker = "insufficient-public-evidence:world.block.query.log")
                val discoveredMaterialPosition = discoveredMaterialTarget.position
                val materialPoint =
                    discoveredMaterialPosition.toCraftlessPoint()
                        ?: return MaterialCollectionAttempt(blocker = "insufficient-public-evidence:world.block.query.position")
                val player =
                    invokeGenerated("player.query")
                val playerPosition =
                    player.responseObject()?.playerPosition()
                        ?: return MaterialCollectionAttempt(blocker = "insufficient-public-evidence:player.query.position")
                if (playerPosition.distanceTo(materialPoint.centered()) > MATERIAL_BREAK_REACH_DISTANCE) {
                    return MaterialCollectionAttempt(blocker = "insufficient-public-evidence:navigation.follow.succeeded")
                }
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
                    return MaterialCollectionAttempt(blocker = "insufficient-public-evidence:world.block.break.changed")
                }
                var pickupNavigationBlocker = navigateTo(position = discoveredMaterialPosition, radius = 1.5)
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
                        val dropNavigationBlocker = navigateTo(position = dropPosition, radius = 1.0)
                        pickupNavigationBlocker =
                            if (dropNavigationBlocker != null && "player.move" in actionIds) {
                                moveToward(position = dropPosition, ticks = PICKUP_MOVE_TICKS)
                            } else {
                                dropNavigationBlocker
                            }
                    }
                    finalInventory = invokeGenerated("inventory.query")
                    if ((finalInventory.responseObject()?.logItemCount() ?: 0) > minimumLogCountExclusive) {
                        break
                    }
                    if (attempt < PICKUP_EVIDENCE_ATTEMPTS) {
                        val retryNavigationBlocker = navigateTo(position = discoveredMaterialPosition, radius = 1.0)
                        pickupNavigationBlocker = retryNavigationBlocker
                    }
                }
                val finalInventoryObject = finalInventory?.responseObject()
                return if ((finalInventoryObject?.logItemCount() ?: 0) > minimumLogCountExclusive) {
                    MaterialCollectionAttempt(inventory = finalInventoryObject)
                } else {
                    MaterialCollectionAttempt(
                        blocker =
                            pickupNavigationBlocker
                                ?: if (minimumLogCountExclusive > 0) {
                                    "insufficient-public-evidence:inventory.query.recipe-material"
                                } else {
                                    "insufficient-public-evidence:inventory.query.log"
                                },
                    )
                }
            }

            val targetMaterialCount =
                if ("recipe.query" in actionIds && "recipe.craft" in actionIds) {
                    MIN_RECIPE_COMPOSITION_MATERIAL_ITEMS
                } else {
                    1
                }
            var finalInventoryObject: JsonObject? = null
            for (attempt in 1..MATERIAL_COLLECTION_ATTEMPTS) {
                val collection = collectMaterialInventory(finalInventoryObject?.logItemCount() ?: 0)
                collection.blocker?.let { blocker -> return blockedAndWrite(blocker) }
                finalInventoryObject = collection.inventory
                if ((finalInventoryObject?.logItemCount() ?: 0) >= targetMaterialCount) {
                    break
                }
                if (attempt == MATERIAL_COLLECTION_ATTEMPTS) {
                    return blockedAndWrite("insufficient-public-evidence:inventory.query.recipe-material")
                }
            }
            val collectedInventory =
                finalInventoryObject ?: return blockedAndWrite("insufficient-public-evidence:inventory.query.log")
            val logSlot =
                collectedInventory.logHotbarSlot()
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
            var combatReadySlot: Int? = null
            var latestInventoryObject = equippedInventory.responseObject()
            val openedStationLabels = mutableSetOf<String>()

            suspend fun equipVerifiedSlot(slot: Int): String? {
                invokeGenerated(
                    action = "inventory.equip",
                    args =
                        buildJsonObject {
                            put("slot", JsonPrimitive(slot))
                        },
                )
                val inventory = invokeGenerated("inventory.query")
                if (inventory.responseObject()?.selectedSlot() != slot) {
                    return "insufficient-public-evidence:inventory.equip.selected-slot"
                }
                latestInventoryObject = inventory.responseObject()
                return null
            }

            suspend fun openStationFromInventory(
                stationLabel: String,
                inventory: JsonObject?,
            ): String? {
                if ("screen.query" !in actionIds) {
                    return "missing-generic-primitive:screen.query"
                }
                val currentScreen = invokeGenerated("screen.query")
                if (currentScreen.responseObject()?.screenMatchesStation(stationLabel) == true) {
                    openedStationLabels += stationLabel
                    return null
                }
                val stationSlot =
                    inventory?.hotbarSlotContaining(stationLabel)
                        ?: return "insufficient-public-evidence:inventory.query.station-item"
                invokeGenerated(
                    action = "inventory.equip",
                    args =
                        buildJsonObject {
                            put("slot", JsonPrimitive(stationSlot))
                        },
                )
                val equippedStationInventory = invokeGenerated("inventory.query")
                if (equippedStationInventory.responseObject()?.selectedSlot() != stationSlot) {
                    return "insufficient-public-evidence:inventory.equip.selected-slot"
                }
                latestInventoryObject = equippedStationInventory.responseObject()
                val supportTargets =
                    invokeGenerated(
                        action = "world.block.query",
                        args =
                            buildJsonObject {
                                put("radius", JsonPrimitive(8.0))
                                put("limit", JsonPrimitive(16))
                                put("category", JsonPrimitive("block"))
                            },
                    ).responseObject()?.supportBlockTargets().orEmpty()
                if (supportTargets.isEmpty()) {
                    return "insufficient-public-evidence:world.block.query.station-support"
                }
                var stationTarget: PublicBlockTarget? = null
                var stationOpenOrigin: CraftlessPoint? = null
                val canVerifyPlacedBlock =
                    actions.actionSupportsArgument("world.block.query", "target")
                for (supportTarget in supportTargets.take(PLACEMENT_TARGET_ATTEMPTS)) {
                    navigateTo(position = supportTarget.position, radius = 2.5)?.let { blocker -> return blocker }
                    val refreshedSupportTarget =
                        invokeGenerated(
                            action = "world.block.query",
                            args =
                                buildJsonObject {
                                    put("radius", JsonPrimitive(4.0))
                                    put("limit", JsonPrimitive(16))
                                    put("category", JsonPrimitive("block"))
                                },
                        ).responseObject()?.supportBlockTarget() ?: supportTarget
                    val stationPlayer =
                        invokeGenerated("player.query")
                            .responseObject()
                            ?.playerPosition()
                            ?: return "insufficient-public-evidence:player.query.position"
                    refreshedSupportTarget.position.toCraftlessPoint()?.let { targetPoint ->
                        val look = stationPlayer.lookAt(targetPoint.centered())
                        invokeGenerated(
                            action = "player.look",
                            args =
                                buildJsonObject {
                                    put("yaw", JsonPrimitive(look.yaw))
                                    put("pitch", JsonPrimitive(look.pitch))
                                },
                        )
                    }
                    invokeGenerated(
                        action = "inventory.equip",
                        args =
                            buildJsonObject {
                                put("slot", JsonPrimitive(stationSlot))
                            },
                    )
                    val refreshedStationInventory = invokeGenerated("inventory.query")
                    if (refreshedStationInventory.responseObject()?.selectedSlot() != stationSlot) {
                        return "insufficient-public-evidence:inventory.equip.selected-slot"
                    }
                    latestInventoryObject = refreshedStationInventory.responseObject()
                    val placeResult =
                        invokeGenerated(
                            action = "world.block.interact",
                            args =
                                buildJsonObject {
                                    put("max-distance", JsonPrimitive(6.0))
                                    put("side", JsonPrimitive(refreshedSupportTarget.side))
                                    put("target", refreshedSupportTarget.toJsonObject())
                                },
                        )
                    if (placeResult.responseObject()?.dataBoolean("accepted") != false) {
                        val placedTarget = placeResult.responseObject()?.placedBlockTarget()
                        stationTarget =
                            if (placedTarget != null && canVerifyPlacedBlock) {
                                invokeGenerated(
                                    action = "world.block.query",
                                    args =
                                        buildJsonObject {
                                            put("limit", JsonPrimitive(1))
                                            put("target", placedTarget.toJsonObject())
                                        },
                                ).responseObject()?.confirmedPlacedBlockTarget(placedTarget)
                            } else {
                                placedTarget
                            }
                        if (stationTarget != null) {
                            stationOpenOrigin = stationPlayer
                            break
                        }
                    }
                }
                if (stationTarget == null) {
                    return "insufficient-public-evidence:world.block.interact.station-placed"
                }
                val canOpenWithEmptyHand = latestInventoryObject?.emptyHotbarSlot() != null
                val stationPoint = stationTarget.position.toCraftlessPoint()?.centered()
                val canOpenFromCurrentPosition =
                    stationOpenOrigin != null &&
                        stationPoint != null &&
                        stationOpenOrigin.distanceTo(stationPoint) <= STATION_OPEN_REACH_DISTANCE
                if (!canOpenFromCurrentPosition) {
                    navigateTo(position = stationTarget.position, radius = 2.5)?.let { blocker -> return blocker }
                }
                if (canOpenWithEmptyHand) {
                    val currentInventory = invokeGenerated("inventory.query")
                    val emptySlot = currentInventory.responseObject()?.emptyHotbarSlot()
                    if (emptySlot != null) {
                        invokeGenerated(
                            action = "inventory.equip",
                            args =
                                buildJsonObject {
                                    put("slot", JsonPrimitive(emptySlot))
                                },
                        )
                        val emptyHandInventory = invokeGenerated("inventory.query")
                        if (emptyHandInventory.responseObject()?.selectedSlot() != emptySlot) {
                            return "insufficient-public-evidence:inventory.equip.empty-slot"
                        }
                        latestInventoryObject = emptyHandInventory.responseObject()
                    } else {
                        latestInventoryObject = currentInventory.responseObject()
                    }
                }
                invokeGenerated(
                    action = "world.block.interact",
                    args =
                        buildJsonObject {
                            put("max-distance", JsonPrimitive(6.0))
                            put("side", JsonPrimitive(stationTarget.side))
                            put("target", stationTarget.toJsonObject())
                        },
                )
                val openedScreen = invokeGenerated("screen.query")
                if (openedScreen.responseObject()?.screenMatchesStation(stationLabel) != true) {
                    return "insufficient-public-evidence:screen.query.station-open"
                }
                openedStationLabels += stationLabel
                return null
            }

            if ("recipe.query" in actionIds && "recipe.craft" in actionIds) {
                val craftedRecipeHandles = mutableSetOf<String>()
                repeat(MAX_RECIPE_COMPOSITION_STEPS) {
                    if (combatReadySlot != null) {
                        return@repeat
                    }
                    val recipeQuery =
                        invokeGenerated(
                            action = "recipe.query",
                            args =
                                buildJsonObject {
                                    put("craftable", JsonPrimitive(true))
                                    put("limit", JsonPrimitive(16))
                                },
                        )
                    val recipe =
                        recipeQuery
                            .responseObject()
                            ?.usefulCraftableRecipe(
                                excludingHandles = craftedRecipeHandles,
                                inventory = latestInventoryObject,
                                openedStationLabels = openedStationLabels,
                            )
                            ?: return@repeat
                    if (recipe.priority != MATERIAL_RECIPE_PRIORITY) {
                        craftedRecipeHandles += recipe.handle
                    }
                    if (recipe.priority == COMBAT_RECIPE_PRIORITY) {
                        recipe.stationLabel?.let { station ->
                            openStationFromInventory(stationLabel = station, inventory = latestInventoryObject)
                                ?.let { blocker -> return blockedAndWrite(blocker) }
                        }
                    }
                    val craftResult =
                        invokeGenerated(
                            action = "recipe.craft",
                            args =
                                buildJsonObject {
                                    put(
                                        "target",
                                        buildJsonObject {
                                            put("handle", JsonPrimitive(recipe.handle))
                                        },
                                    )
                                    put("count", JsonPrimitive(1))
                                },
                        )
                    if (craftResult.responseObject()?.dataBoolean("changed") == false) {
                        return blockedAndWrite("insufficient-public-evidence:recipe.craft.changed")
                    }
                    val craftedInventory = invokeGenerated("inventory.query")
                    val craftedInventoryObject = craftedInventory.responseObject()
                    when {
                        recipe.priority == COMBAT_RECIPE_PRIORITY ->
                            combatReadySlot =
                                craftedInventoryObject?.combatReadyHotbarSlot()
                                    ?: return blockedAndWrite("insufficient-public-evidence:inventory.query.combat-item")
                        recipe.priority == STATION_RECIPE_PRIORITY -> Unit
                        craftedInventoryObject?.hasUsefulCraftedItem() != true ->
                            return blockedAndWrite("insufficient-public-evidence:inventory.query.crafted-output")
                    }
                    if (recipe.priority == STATION_RECIPE_PRIORITY) {
                        recipe.outputLabel
                            ?.let { station -> openStationFromInventory(stationLabel = station, inventory = craftedInventoryObject) }
                            ?.let { blocker -> return blockedAndWrite(blocker) }
                    }
                    latestInventoryObject = craftedInventoryObject
                    combatReadySlot = combatReadySlot ?: craftedInventoryObject?.combatReadyHotbarSlot()
                }
            }
            val placementSlot = latestInventoryObject?.logHotbarSlot()
            if (placementSlot != null && actions.actionSupportsArgument("world.block.interact", "target")) {
                val supportTargets =
                    invokeGenerated(
                        action = "world.block.query",
                        args =
                            buildJsonObject {
                                put("radius", JsonPrimitive(8.0))
                                put("limit", JsonPrimitive(16))
                                put("category", JsonPrimitive("block"))
                            },
                    ).responseObject()?.supportBlockTargets().orEmpty()
                if (supportTargets.isNotEmpty()) {
                    var placementChanged = false
                    for (supportTarget in supportTargets.take(PLACEMENT_TARGET_ATTEMPTS)) {
                        navigateTo(position = supportTarget.position, radius = 2.5)
                            ?.let { blocker -> return blockedAndWrite(blocker) }
                        val refreshedSupportTarget =
                            invokeGenerated(
                                action = "world.block.query",
                                args =
                                    buildJsonObject {
                                        put("radius", JsonPrimitive(4.0))
                                        put("limit", JsonPrimitive(16))
                                        put("category", JsonPrimitive("block"))
                                    },
                            ).responseObject()?.supportBlockTarget() ?: supportTarget
                        invokeGenerated(
                            action = "inventory.equip",
                            args =
                                buildJsonObject {
                                    put("slot", JsonPrimitive(placementSlot))
                                },
                        )
                        val placementInventory = invokeGenerated("inventory.query")
                        if (placementInventory.responseObject()?.selectedSlot() != placementSlot) {
                            return blockedAndWrite("insufficient-public-evidence:inventory.equip.selected-slot")
                        }
                        val placementPlayer = invokeGenerated("player.query")
                        val placementPlayerPosition =
                            placementPlayer.responseObject()?.playerPosition()
                                ?: return blockedAndWrite("insufficient-public-evidence:player.query.position")
                        refreshedSupportTarget.position.toCraftlessPoint()?.let { targetPoint ->
                            val placementLook = placementPlayerPosition.lookAt(targetPoint.centered())
                            invokeGenerated(
                                action = "player.look",
                                args =
                                    buildJsonObject {
                                        put("yaw", JsonPrimitive(placementLook.yaw))
                                        put("pitch", JsonPrimitive(placementLook.pitch))
                                    },
                            )
                        }
                        val interactResult =
                            invokeGenerated(
                                action = "world.block.interact",
                                args =
                                    buildJsonObject {
                                        put("max-distance", JsonPrimitive(6.0))
                                        put("side", JsonPrimitive(refreshedSupportTarget.side))
                                        put("target", refreshedSupportTarget.toJsonObject())
                                    },
                            )
                        if (interactResult.responseObject()?.dataBoolean("changed") == true) {
                            placementChanged = true
                            break
                        }
                    }
                    if (!placementChanged) {
                        return blockedAndWrite("insufficient-public-evidence:world.block.interact.changed")
                    }
                }
            }
            if ("entity.attack" in actionIds) {
                combatReadySlot?.let { slot ->
                    equipVerifiedSlot(slot)?.let { blocker -> return blockedAndWrite(blocker) }
                }
                val attackTarget =
                    exploreAttackTarget()
                        ?: return blockedAndWrite("insufficient-public-evidence:entity.query.attack-target")
                val firstFocusedAttackTarget = focusAttackTarget(attackTarget)
                firstFocusedAttackTarget.blocker?.let { blocker -> return blockedAndWrite(blocker) }
                var currentAttackTarget =
                    firstFocusedAttackTarget.target
                        ?: return blockedAndWrite("insufficient-public-evidence:entity.query.attack-target")
                var combatOutcomeProved = false
                var combatAttempts = 0
                while (!combatOutcomeProved && combatAttempts < combatEvidenceAttempts) {
                    combatAttempts += 1
                    combatReadySlot?.let { slot ->
                        equipVerifiedSlot(slot)?.let { blocker -> return blockedAndWrite(blocker) }
                    }
                    val attackResult =
                        invokeGenerated(
                            action = "entity.attack",
                            args =
                                buildJsonObject {
                                    put(
                                        "target",
                                        buildJsonObject {
                                            put("handle", JsonPrimitive(currentAttackTarget.handle))
                                        },
                                    )
                                    put("max-distance", JsonPrimitive(ATTACK_MAX_DISTANCE))
                                },
                        )
                    if (attackResult.responseObject()?.dataBoolean("hit") == false) {
                        return blockedAndWrite("insufficient-public-evidence:entity.attack.hit")
                    }
                    val combatEntityState = invokeGenerated("entity.query")
                    val combatInventoryState = invokeGenerated("inventory.query")
                    val combatEntityStateObject = combatEntityState.responseObject()
                    val combatInventoryStateObject = combatInventoryState.responseObject()
                    if (combatInventoryStateObject?.hasCombatLootItem() == true) {
                        combatOutcomeProved = true
                    } else if (combatEntityStateObject?.combatLootDropPosition() != null) {
                        val visibleLootBlocker = collectVisibleCombatLoot(combatEntityStateObject)
                        if (visibleLootBlocker != null) {
                            return blockedAndWrite(visibleLootBlocker)
                        }
                        val lootInventoryState = invokeGenerated("inventory.query")
                        if (lootInventoryState.responseObject()?.hasCombatLootItem() == true) {
                            combatOutcomeProved = true
                        }
                    }
                    if (!combatOutcomeProved && combatEntityStateObject?.entityNotAlive(currentAttackTarget.handle) == true) {
                        combatOutcomeProved = true
                    }
                    if (!combatOutcomeProved && combatAttempts < combatEvidenceAttempts) {
                        combatPause()
                        val refreshedAttackTarget =
                            combatEntityStateObject?.attackTarget(preferredHandle = currentAttackTarget.handle)
                                ?: queryAttackTarget(radius = 16.0, preferredHandle = currentAttackTarget.handle)
                                ?: combatEntityStateObject?.attackTarget()
                        if (refreshedAttackTarget != null) {
                            val focusedAttackTarget = focusAttackTarget(refreshedAttackTarget)
                            focusedAttackTarget.blocker?.let { blocker -> return blockedAndWrite(blocker) }
                            currentAttackTarget =
                                focusedAttackTarget.target
                                    ?: return blockedAndWrite("insufficient-public-evidence:entity.query.attack-target")
                        }
                    }
                }
                if (!combatOutcomeProved) {
                    return blockedAndWrite("insufficient-public-evidence:entity.attack.outcome")
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
    val actionRequestTimeoutMillis: Long = DEFAULT_ACTION_REQUEST_TIMEOUT_MS,
    val combatEvidenceAttempts: Int = DEFAULT_COMBAT_EVIDENCE_ATTEMPTS,
    val combatRetryDelayMillis: Long = DEFAULT_COMBAT_RETRY_DELAY_MS,
) {
    init {
        require(baseUrl.isNotBlank()) { "public agent base URL is required" }
        require(clientId.isNotBlank()) { "public agent client id is required" }
        require(actionRequestTimeoutMillis > 0L) { "public agent action request timeout must be positive" }
        require(combatEvidenceAttempts > 0) { "combat evidence attempts must be positive" }
        require(combatRetryDelayMillis >= 0L) { "combat retry delay must not be negative" }
    }

    companion object {
        private const val BASE_URL = "CRAFTLESS_PUBLIC_AGENT_BASE_URL"
        private const val CLIENT_ID = "CRAFTLESS_PUBLIC_AGENT_CLIENT_ID"
        private const val ARTIFACTS_DIR = "CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR"
        private const val ACTION_REQUEST_TIMEOUT_MS = "CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS"
        private const val SMOKE_ACTION_TIMEOUT_MS = "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS"
        private const val COMBAT_EVIDENCE_ATTEMPTS = "CRAFTLESS_PUBLIC_AGENT_COMBAT_EVIDENCE_ATTEMPTS"
        private const val COMBAT_RETRY_DELAY_MS = "CRAFTLESS_PUBLIC_AGENT_COMBAT_RETRY_DELAY_MS"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): PublicAgentGameplayRunnerConfig =
            PublicAgentGameplayRunnerConfig(
                baseUrl = env[BASE_URL]?.takeIf { it.isNotBlank() } ?: error("$BASE_URL is required"),
                clientId = env[CLIENT_ID]?.takeIf { it.isNotBlank() } ?: "fabric-smoke",
                artifactsDir =
                    env[ARTIFACTS_DIR]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Path::of)
                        ?: Path.of("build", "craftless-public-agent-gameplay", "artifacts"),
                actionRequestTimeoutMillis =
                    env[ACTION_REQUEST_TIMEOUT_MS]
                        ?.takeIf { it.isNotBlank() }
                        ?.toLongStrict(ACTION_REQUEST_TIMEOUT_MS)
                        ?: env[SMOKE_ACTION_TIMEOUT_MS]
                            ?.takeIf { it.isNotBlank() }
                            ?.toLongStrict(SMOKE_ACTION_TIMEOUT_MS)
                        ?: DEFAULT_ACTION_REQUEST_TIMEOUT_MS,
                combatEvidenceAttempts =
                    env[COMBAT_EVIDENCE_ATTEMPTS]
                        ?.takeIf { it.isNotBlank() }
                        ?.toIntStrict(COMBAT_EVIDENCE_ATTEMPTS)
                        ?: DEFAULT_COMBAT_EVIDENCE_ATTEMPTS,
                combatRetryDelayMillis =
                    env[COMBAT_RETRY_DELAY_MS]
                        ?.takeIf { it.isNotBlank() }
                        ?.toLongOrNull()
                        ?: DEFAULT_COMBAT_RETRY_DELAY_MS,
            )

        private fun String.toIntStrict(name: String): Int = toIntOrNull() ?: error("$name must be an integer")

        private fun String.toLongStrict(name: String): Long = toLongOrNull() ?: error("$name must be a long integer")
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

internal fun publicAgentHttpClient(actionRequestTimeoutMillis: Long): HttpClient {
    require(actionRequestTimeoutMillis > 0L) { "public agent action request timeout must be positive" }
    return HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = actionRequestTimeoutMillis
            connectTimeoutMillis = actionRequestTimeoutMillis.coerceAtMost(DEFAULT_CONNECT_TIMEOUT_MS)
            socketTimeoutMillis = actionRequestTimeoutMillis
        }
    }
}

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
            if (block["replaceable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true) {
                return@mapNotNull null
            }
            val position = block["position"] as? JsonObject ?: return@mapNotNull null
            PublicBlockTarget(
                handle = block["handle"]?.jsonPrimitive?.contentOrNull,
                position = position,
                distance = block.doubleField("distance"),
                side = block.preferredPlacementSide() ?: DEFAULT_PLACEMENT_SIDE,
            )
        }.minWithOrNull(
            compareBy<PublicBlockTarget> { target ->
                target.position.toCraftlessPoint()?.y ?: Double.MAX_VALUE
            }.thenBy { target ->
                target.distance ?: Double.MAX_VALUE
            },
        )
}

private fun JsonObject.supportBlockTargets(): List<PublicBlockTarget> {
    val data = this["data"] as? JsonObject ?: return emptyList()
    val blocks = data["blocks"]?.jsonArray ?: return emptyList()
    return blocks
        .mapNotNull { element ->
            val block = element as? JsonObject ?: return@mapNotNull null
            if (block["replaceable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true) {
                return@mapNotNull null
            }
            val position = block["position"] as? JsonObject ?: return@mapNotNull null
            val placementSide = block.preferredPlacementSide() ?: return@mapNotNull null
            PublicBlockTarget(
                handle = block["handle"]?.jsonPrimitive?.contentOrNull,
                position = position,
                distance = block.doubleField("distance"),
                side = placementSide,
            )
        }.sortedWith(
            compareBy<PublicBlockTarget> { target ->
                target.distance ?: Double.MAX_VALUE
            }.thenBy { target ->
                target.position.toCraftlessPoint()?.y ?: Double.MAX_VALUE
            },
        )
}

private fun JsonObject.preferredPlacementSide(): String? {
    val faces = this["faces"]?.jsonArray ?: return null
    return faces
        .mapNotNull { element -> element as? JsonObject }
        .filter { face ->
            face["replaceable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true &&
                face["occupied-by-player"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() != true
        }.minByOrNull { face ->
            val side = face["side"]?.jsonPrimitive?.contentOrNull.orEmpty()
            placementSidePreference.indexOf(side).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }?.get("side")
        ?.jsonPrimitive
        ?.contentOrNull
}

private fun JsonObject.supportBlockTarget(): PublicBlockTarget? = supportBlockTargets().firstOrNull()

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

private fun JsonObject.combatLootDropPosition(): JsonObject? {
    val data = this["data"] as? JsonObject ?: return null
    val entities = data["entities"]?.jsonArray ?: return null
    return entities
        .mapNotNull { element ->
            val entity = element as? JsonObject ?: return@mapNotNull null
            val position = entity["position"] as? JsonObject ?: return@mapNotNull null
            val label = entity["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (combatLootNameParts.none { part -> label.contains(part, ignoreCase = true) }) {
                return@mapNotNull null
            }
            val distance = entity.doubleField("distance") ?: Double.MAX_VALUE
            distance to position
        }.minByOrNull { (distance, _) -> distance }
        ?.second
}

private fun JsonObject.attackTarget(preferredHandle: String? = null): PublicEntityTarget? {
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
            val label = entity["label"]?.jsonPrimitive?.contentOrNull
            if (!label.hasCombatEvidenceTargetName()) {
                return@mapNotNull null
            }
            val position = entity["position"] as? JsonObject
            PublicEntityTarget(
                handle = handle,
                label = label,
                position = position,
                distance = entity.doubleField("distance"),
            )
        }.minWithOrNull(
            compareBy<PublicEntityTarget> { target -> target.preferredHandlePriority(preferredHandle) }
                .thenBy { target -> target.combatEvidencePriority() }
                .thenBy { target -> target.distance ?: Double.MAX_VALUE },
        )
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

private fun JsonObject.logItemCount(): Int {
    val data = this["data"] as? JsonObject ?: return 0
    val slots = data["slots"]?.jsonArray ?: return 0
    return slots.sumOf { element ->
        val slot = element as? JsonObject ?: return@sumOf 0
        val empty =
            slot["empty"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toBooleanStrictOrNull()
                ?: false
        if (empty || !slot.containsItemName("log")) {
            0
        } else {
            slot["count"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toIntOrNull()
                ?: 1
        }
    }
}

private fun JsonObject.hasCombatLootItem(): Boolean {
    val data = this["data"] as? JsonObject ?: return false
    val slots = data["slots"]?.jsonArray ?: return false
    return slots.any { element ->
        val slot = element as? JsonObject ?: return@any false
        combatLootNameParts.any(slot::containsItemName)
    }
}

private fun JsonObject.usefulCraftableRecipe(
    excludingHandles: Set<String> = emptySet(),
    inventory: JsonObject? = null,
    openedStationLabels: Set<String> = emptySet(),
): PublicRecipeTarget? {
    val data = this["data"] as? JsonObject ?: return null
    val recipes = data["recipes"]?.jsonArray ?: return null
    val recipeObjects = recipes.mapNotNull { element -> element as? JsonObject }
    val stationLabels = recipeObjects.mapNotNull { recipe -> recipe.stationLabel() }.toSet()
    return recipeObjects
        .mapNotNull { recipe ->
            val craftable =
                recipe["craftable"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
                    ?: false
            if (!craftable) {
                return@mapNotNull null
            }
            val handle =
                recipe["handle"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.takeIf { it.startsWith("recipe.handle:") }
                    ?: return@mapNotNull null
            if (handle in excludingHandles) {
                return@mapNotNull null
            }
            val category = recipe["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val produces =
                (recipe["outputs"] ?: recipe["produces"])
                    ?.jsonArray
                    .orEmpty()
                    .mapNotNull { produced -> produced as? JsonObject }
            if (category !in usefulRecipeCategories && produces.none { it.hasUsefulRecipeOutput(stationLabels) }) {
                return@mapNotNull null
            }
            val outputLabel = produces.firstNotNullOfOrNull { output -> output.recipeLabel() }
            val stationLabel = recipe.stationLabel()
            val basePriority =
                listOf(recipe.recipePriority(stationLabels))
                    .plus(produces.map { output -> output.recipePriority(stationLabels) })
                    .min()
            PublicRecipeTarget(
                handle = handle,
                outputLabel = outputLabel,
                stationLabel = stationLabel,
                priority = recipePriorityWithStationReadiness(basePriority, stationLabel, inventory, openedStationLabels),
            )
        }.minWithOrNull(
            compareBy<PublicRecipeTarget> { recipe -> recipe.priority }
                .thenBy { recipe -> recipe.handle },
        )
}

private fun recipePriorityWithStationReadiness(
    basePriority: Int,
    stationLabel: String?,
    inventory: JsonObject?,
    openedStationLabels: Set<String>,
): Int =
    if (
        stationLabel != null &&
        basePriority < STATION_RECIPE_PRIORITY &&
        stationLabel !in openedStationLabels &&
        inventory?.hotbarSlotContaining(stationLabel) == null
    ) {
        MISSING_STATION_RECIPE_PRIORITY
    } else {
        basePriority
    }

private fun JsonObject.hasUsefulRecipeOutput(stationLabels: Set<String> = emptySet()): Boolean {
    val category = this["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val label = recipeLabel().orEmpty()
    return category in usefulRecipeCategories ||
        label in stationLabels ||
        usefulCraftedItemNameParts.any { part -> label.contains(part, ignoreCase = true) }
}

private fun String?.hasCombatEvidenceTargetName(): Boolean =
    combatEvidenceTargetNameParts.any { part -> orEmpty().contains(part, ignoreCase = true) }

private fun JsonObject.recipePriority(stationLabels: Set<String> = emptySet()): Int {
    val category = this["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val label = recipeLabel().orEmpty()
    return when {
        category == "weapon" -> COMBAT_RECIPE_PRIORITY
        combatReadyItemNameParts.any { part -> label.contains(part, ignoreCase = true) } -> COMBAT_RECIPE_PRIORITY
        category == "tool" -> TOOL_RECIPE_PRIORITY
        label in stationLabels -> STATION_RECIPE_PRIORITY
        category == "utility" -> UTILITY_RECIPE_PRIORITY
        category == "material" -> MATERIAL_RECIPE_PRIORITY
        usefulCraftedItemNameParts.any { part -> label.contains(part, ignoreCase = true) } -> MATERIAL_RECIPE_PRIORITY
        else -> OTHER_RECIPE_PRIORITY
    }
}

private fun JsonObject.recipeLabel(): String? =
    this["label"]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { label -> label.isNotBlank() }

private fun JsonObject.stationLabel(): String? =
    (this["station"] as? JsonObject)
        ?.recipeLabel()

private fun JsonObject.hasUsefulCraftedItem(): Boolean {
    val data = this["data"] as? JsonObject ?: return false
    val slots = data["slots"]?.jsonArray ?: return false
    return slots.any { element ->
        val slot = element as? JsonObject ?: return@any false
        usefulCraftedItemNameParts.any(slot::containsItemName)
    }
}

private fun JsonObject.combatReadyHotbarSlot(): Int? {
    val data = this["data"] as? JsonObject ?: return null
    val slots = data["slots"]?.jsonArray ?: return null
    return slots.firstNotNullOfOrNull { element ->
        val slot = element as? JsonObject ?: return@firstNotNullOfOrNull null
        val slotNumber = slot.hotbarSlotNumber() ?: return@firstNotNullOfOrNull null
        if (combatReadyItemNameParts.any(slot::containsItemName)) {
            slotNumber
        } else {
            null
        }
    }
}

private fun JsonObject.hotbarSlotContaining(label: String): Int? {
    val data = this["data"] as? JsonObject ?: return null
    val slots = data["slots"]?.jsonArray ?: return null
    return slots.firstNotNullOfOrNull { element ->
        val slot = element as? JsonObject ?: return@firstNotNullOfOrNull null
        val slotNumber = slot.hotbarSlotNumber() ?: return@firstNotNullOfOrNull null
        if (slot.containsItemName(label)) {
            slotNumber
        } else {
            null
        }
    }
}

private fun JsonObject.emptyHotbarSlot(): Int? {
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
                ?: false
        slotNumber.takeIf { slotIndex -> slotIndex in 0..8 && empty }
    }
}

private fun JsonObject.hotbarSlotNumber(): Int? {
    val slotNumber =
        this["slot"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
            ?: return null
    val empty =
        this["empty"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
            ?: true
    return slotNumber.takeIf { slot -> slot in 0..8 && !empty }
}

private fun JsonObject.screenMatchesStation(stationLabel: String): Boolean {
    val data = this["data"] as? JsonObject ?: return false
    val open =
        data["open"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
            ?: false
    if (!open) {
        return false
    }
    val title = data["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return title.contains(stationLabel, ignoreCase = true) ||
        stationLabel.contains(title, ignoreCase = true)
}

private fun JsonObject.placedBlockTarget(): PublicBlockTarget? {
    val data = this["data"] as? JsonObject ?: return null
    val adjacentCategory =
        data["adjacent-category"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null
    val adjacentReplaceable =
        data["adjacent-replaceable"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toBooleanStrictOrNull()
            ?: return null
    if (adjacentCategory == "air" || adjacentReplaceable) {
        return null
    }
    val handle =
        data["adjacent-handle"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null
    val position = data["adjacent-position"] as? JsonObject ?: return null
    val side = data["side"]?.jsonPrimitive?.contentOrNull ?: DEFAULT_PLACEMENT_SIDE
    return PublicBlockTarget(handle = handle, position = position, distance = null, side = side)
}

private fun JsonObject.confirmedPlacedBlockTarget(expected: PublicBlockTarget): PublicBlockTarget? {
    val data = this["data"] as? JsonObject ?: return null
    val blocks = data["blocks"]?.jsonArray ?: return null
    return blocks
        .mapNotNull { element -> element as? JsonObject }
        .firstOrNull { block ->
            val category = block["category"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
            val replaceable =
                block["replaceable"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
                    ?: return@firstOrNull false
            category != "air" &&
                !replaceable &&
                block.matchesTarget(expected)
        }?.let { block ->
            val handle = block["handle"]?.jsonPrimitive?.contentOrNull ?: expected.handle
            val position = block["position"] as? JsonObject ?: expected.position
            PublicBlockTarget(
                handle = handle,
                position = position,
                distance = block.doubleField("distance"),
                side = expected.side,
            )
        }
}

private fun JsonObject.matchesTarget(expected: PublicBlockTarget): Boolean {
    val handle = this["handle"]?.jsonPrimitive?.contentOrNull
    if (expected.handle != null && handle == expected.handle) {
        return true
    }
    val position = this["position"] as? JsonObject ?: return false
    return position.toCraftlessPoint() == expected.position.toCraftlessPoint()
}

private fun JsonObject.entityNotAlive(handle: String): Boolean {
    val data = this["data"] as? JsonObject ?: return false
    val entities = data["entities"]?.jsonArray ?: return false
    return entities.any { element ->
        val entity = element as? JsonObject ?: return@any false
        entity["handle"]?.jsonPrimitive?.contentOrNull == handle &&
            entity["alive"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == false
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

private fun JsonObject.navigationSucceeded(): Boolean {
    val status = this["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val data = this["data"] as? JsonObject ?: return false
    val state = data["state"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return status.equals("ACCEPTED", ignoreCase = true) && state == "succeeded"
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

    fun explorationWaypoints(rings: Int = 1): List<CraftlessPoint> =
        (1..rings).flatMap { ring ->
            val step = EXPLORATION_STEP * ring
            listOf(
                copy(x = x + step),
                copy(z = z + step),
                copy(x = x - step),
                copy(z = z - step),
                copy(x = x + step, z = z + step),
                copy(x = x - step, z = z + step),
                copy(x = x + step, z = z - step),
                copy(x = x - step, z = z - step),
            )
        }

    fun lookAt(target: CraftlessPoint): CraftlessLook {
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(dz, dx)) - 90.0
        val pitch = -Math.toDegrees(atan2(dy, horizontal))
        return CraftlessLook(yaw = yaw, pitch = pitch.coerceIn(-90.0, 90.0))
    }

    fun distanceTo(target: CraftlessPoint): Double {
        val dx = target.x - x
        val dy = target.y - y
        val dz = target.z - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

private const val EXPLORATION_STEP = 24.0
private const val MATERIAL_BREAK_REACH_DISTANCE = 6.0
private const val PICKUP_EVIDENCE_ATTEMPTS = 4
private const val MATERIAL_COLLECTION_ATTEMPTS = 2
private const val MIN_RECIPE_COMPOSITION_MATERIAL_ITEMS = 2
private const val PICKUP_MOVE_TICKS = 24
private const val COMBAT_MOVE_TICKS = 24
private const val COMBAT_FOCUS_ATTEMPTS = 3
private const val ATTACK_EXPLORATION_RINGS = 3
private const val DEFAULT_COMBAT_EVIDENCE_ATTEMPTS = 12
private const val DEFAULT_ACTION_REQUEST_TIMEOUT_MS = 300_000L
private const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000L
private const val DEFAULT_COMBAT_RETRY_DELAY_MS = 700L
private const val PLACEMENT_TARGET_ATTEMPTS = 6
private const val MAX_RECIPE_COMPOSITION_STEPS = 6
private const val STATION_OPEN_REACH_DISTANCE = 6.0
private const val ATTACK_MAX_DISTANCE = 4.5
private const val ATTACK_VERTICAL_REACH = 4.5

private data class CraftlessLook(
    val yaw: Double,
    val pitch: Double,
)

private data class PublicBlockTarget(
    val handle: String?,
    val position: JsonObject,
    val distance: Double?,
    val side: String = DEFAULT_PLACEMENT_SIDE,
) {
    fun toJsonObject(): JsonObject =
        buildJsonObject {
            handle?.let { put("handle", JsonPrimitive(it)) }
            put("position", position)
        }
}

private data class FocusedBlockTarget(
    val target: PublicBlockTarget? = null,
    val blocker: String? = null,
)

private data class MaterialCollectionAttempt(
    val inventory: JsonObject? = null,
    val blocker: String? = null,
)

private data class PublicEntityTarget(
    val handle: String,
    val label: String?,
    val position: JsonObject?,
    val distance: Double?,
) {
    fun preferredHandlePriority(preferredHandle: String?): Int =
        if (preferredHandle != null && handle == preferredHandle) {
            0
        } else {
            1
        }

    fun combatEvidencePriority(): Int =
        combatEvidenceTargetNameParts
            .indexOfFirst { part -> label.orEmpty().contains(part, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?: combatEvidenceTargetNameParts.size

    fun isReachableForAttack(playerPosition: CraftlessPoint): Boolean {
        val reportedReachable = distance?.let { it <= ATTACK_MAX_DISTANCE } ?: false
        val positionPoint = position?.toCraftlessPoint()
        val positionedReachable =
            positionPoint?.let { point ->
                abs(point.y - playerPosition.y) <= ATTACK_VERTICAL_REACH &&
                    playerPosition.distanceTo(point) <= ATTACK_MAX_DISTANCE
            } ?: false
        return reportedReachable || positionedReachable
    }
}

private data class PublicRecipeTarget(
    val handle: String,
    val outputLabel: String?,
    val stationLabel: String?,
    val priority: Int,
)

private data class FocusedAttackTarget(
    val target: PublicEntityTarget? = null,
    val blocker: String? = null,
)

private val attackableEntityCategories = setOf("passive", "hostile", "living")

private val combatLootNameParts = listOf("beef", "leather", "pork", "mutton", "chicken", "rotten flesh")
private val combatEvidenceTargetNameParts = listOf("cow", "pig", "sheep", "chicken", "zombie")

private val usefulRecipeCategories = setOf("weapon", "tool", "utility", "material")

private val combatReadyItemNameParts = listOf("sword", "axe")
private val usefulCraftedItemNameParts = listOf("sword", "axe", "pickaxe", "shovel", "plank", "stick")

private const val COMBAT_RECIPE_PRIORITY = 0
private const val TOOL_RECIPE_PRIORITY = 1
private const val STATION_RECIPE_PRIORITY = 2
private const val MISSING_STATION_RECIPE_PRIORITY = 3
private const val UTILITY_RECIPE_PRIORITY = 3
private const val MATERIAL_RECIPE_PRIORITY = 4
private const val OTHER_RECIPE_PRIORITY = 5

private const val DEFAULT_PLACEMENT_SIDE = "up"

private val placementSidePreference = listOf("north", "south", "west", "east", "up", "down")

fun main() {
    val config = PublicAgentGameplayRunnerConfig.fromEnvironment()
    runBlocking {
        publicAgentHttpClient(config.actionRequestTimeoutMillis).use { http ->
            val result =
                PublicAgentGameplayRunner(
                    baseUrl = config.baseUrl,
                    clientId = config.clientId,
                    http = http,
                    combatPause = { delay(config.combatRetryDelayMillis) },
                    combatEvidenceAttempts = config.combatEvidenceAttempts,
                ).runOnce(artifactsDir = config.artifactsDir)
            println("publicAgentState=${result.state}")
            result.blocker?.let { blocker -> println("publicAgentBlocker=$blocker") }
            println("publicAgentArtifacts=${config.artifactsDir}")
        }
    }
}
