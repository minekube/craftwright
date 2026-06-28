package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionResultDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverOperationAdapter
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverOperationInvocation
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeIdentity
import com.minekube.craftless.driver.fabric.runtime.defaultFabricCompatibilityMatrix
import com.minekube.craftless.driver.fabric.v1_21_6.mixin.ClientRecipeBookAccessor
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.NavigationGoal
import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeAvailabilityState
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.item.ItemStack
import net.minecraft.recipe.NetworkRecipeId
import net.minecraft.recipe.RecipeDisplayEntry
import net.minecraft.recipe.RecipeFinder
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.tag.BlockTags
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.screen.AbstractCraftingScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import java.security.MessageDigest
import kotlin.math.sqrt

class FabricDriverBackend private constructor(
    private val mode: Mode,
    private val gateway: FabricClientGateway?,
    actionBindings: List<FabricActionBinding> = defaultFabricActionBindings(),
    private val actionDiscovery: FabricActionDiscovery = defaultFabricActionDiscovery(),
    private val capabilityDiscovery: FabricCapabilityDiscovery = defaultFabricCapabilityDiscovery(),
    private val runtimeMetadataProvider: FabricRuntimeMetadataProvider = staticFabricRuntimeMetadataProvider(),
    private val pathfinderBackend: FabricPathfinderBackend = UnavailableFabricPathfinderBackend,
    private val taskExecutor: FabricTaskExecutor = UnavailableFabricTaskExecutor,
) : DriverBackend {
    private val events = mutableListOf<String>()
    private val actionBindingsById = actionBindings.associateBy { it.descriptor.id }

    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        record("connect $clientId ${target.host}:${target.port}")
        gateway?.execute {
            gateway.connect(target)
        }
        return DriverBackendResult(
            action = DriverBackendAction.CONNECT,
            message = "fabric ${mode.id} connect requested",
            observed = gateway?.isConnected() ?: true,
        )
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        runtimeGraph(clientId).operations.sortedBy { operation -> operation.id }.map { operation -> operation.toDriverActionDescriptor() }

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = runtimeMetadataProvider.runtimeMetadata(clientId)

    override fun runtimeGraph(clientId: String): RuntimeCapabilityGraph {
        val metadata = runtimeMetadata(clientId)
        val identity = metadata.toCurrentLaneRuntimeIdentity()
        return capabilityDiscovery.discover(
            FabricCapabilityProbeContext(
                clientId = clientId,
                modeId = mode.id,
                gateway = gateway,
                runtimeMetadata = metadata,
                compatibilityLane = defaultFabricCompatibilityMatrix().resolve(identity),
            ),
        )
    }

    override fun operationAdapters(clientId: String): DriverOperationAdapters {
        val adapters =
            navigationTaskOperationAdapters() +
                discoveredActions(clientId)
                    .mapNotNull { discoveredAction ->
                        val binding = discoveredAction.binding ?: return@mapNotNull null
                        discoveredAction.descriptor.id.fabricOperationAdapterKey() to
                            DriverOperationAdapter { invocation ->
                                binding.invoke(
                                    clientId = invocation.clientId,
                                    invocation =
                                        DriverActionInvocation(
                                            action = invocation.operation.id,
                                            arguments = invocation.arguments,
                                        ),
                                    context =
                                        FabricActionContext(
                                            modeId = mode.id,
                                            gateway = gateway,
                                            record = ::record,
                                        ),
                                )
                            }
                    }.toMap()
        return DriverOperationAdapters(adapters)
    }

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val discoveredAction = discoveredActions(clientId).firstOrNull { it.descriptor.id == invocation.action }
        if (discoveredAction == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported Fabric action ${invocation.action}",
            )
        }
        val binding = discoveredAction.binding
        if (binding == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = discoveredAction.descriptor.availabilityReason ?: "unavailable Fabric action ${invocation.action}",
            )
        }
        return binding.invoke(
            clientId = clientId,
            invocation = invocation,
            context =
                FabricActionContext(
                    modeId = mode.id,
                    gateway = gateway,
                    record = ::record,
                ),
        )
    }

    override fun stop(clientId: String): DriverBackendResult {
        record("stop $clientId")
        gateway?.execute {
            gateway.stop()
        }
        return DriverBackendResult(DriverBackendAction.STOP, "fabric ${mode.id} stop requested")
    }

    fun events(): List<String> = events.toList()

    private fun discoveredActions(clientId: String): List<FabricDiscoveredAction> =
        actionDiscovery.discover(
            FabricActionDiscoveryContext(
                clientId = clientId,
                modeId = mode.id,
                gateway = gateway,
                bindings = actionBindingsById,
            ),
        )

    private fun record(event: String) {
        events += event
    }

    private fun navigationTaskOperationAdapters(): Map<String, DriverOperationAdapter> =
        mapOf(
            "navigation.default" to navigationOperationAdapter(),
            "task.executor" to taskOperationAdapter(),
            "fabric.entity-query" to entityQueryOperationAdapter(),
            "fabric.entity-attack" to entityAttackOperationAdapter(),
            "fabric.world-block-query" to blockQueryOperationAdapter(),
            "fabric.recipe-query" to recipeQueryOperationAdapter(),
            "fabric.recipe-craft" to recipeCraftOperationAdapter(),
        )

    private fun navigationOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (!pathfinderBackend.available()) {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            when (invocation.operation.id) {
                "navigation.plan" -> planNavigation(invocation)
                "navigation.follow" -> followNavigation(invocation)
                "navigation.stop" -> stopNavigation(invocation)
                else -> unsupportedGraphOperation(invocation)
            }
        }

    private fun unsupportedGraphOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation -> unsupportedGraphOperation(invocation) }

    private fun taskOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.availability.state == RuntimeAvailabilityState.UNAVAILABLE) {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            when (invocation.operation.id) {
                "task.run" -> runTask(invocation)
                "task.status" -> queryTaskStatus(invocation)
                else -> unsupportedGraphOperation(invocation)
            }
        }

    private fun entityQueryOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "entity.query") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            queryEntities(invocation)
        }

    private fun entityAttackOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "entity.attack") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            attackEntity(invocation)
        }

    private fun blockQueryOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "world.block.query") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            queryBlocks(invocation)
        }

    private fun recipeQueryOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "recipe.query") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            queryRecipes(invocation)
        }

    private fun recipeCraftOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "recipe.craft") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            craftRecipe(invocation)
        }

    private fun queryRecipes(invocation: DriverOperationInvocation): DriverActionResult {
        val limit = invocation.arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_RECIPE_QUERY_LIMIT
        val category =
            invocation.arguments["category"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        val output =
            invocation.arguments["output"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val craftable =
            invocation.arguments["craftable"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toBooleanStrictOrNull()
        if (limit !in RECIPE_QUERY_LIMIT_RANGE) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-limit",
                data = recipeQueryFailure("invalid-limit"),
            )
        }
        invocation.unavailableRecipeOperationResult()?.let { result -> return result }
        val clientGateway = gateway
        if (clientGateway == null || !clientGateway.isConnected()) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.UNSUPPORTED,
                message = "client-not-connected",
            )
        }
        val data =
            clientGateway.queryOnClient {
                val currentPlayer = requireNotNull(player) { "client is not connected to a server" }
                val recipeBook = currentPlayer.recipeBook
                val finder = RecipeFinder()
                currentPlayer.inventory.populateRecipeFinder(finder)
                recipeBook.refresh()
                val features = networkHandler?.enabledFeatures
                val recipes =
                    recipeBook
                        .craftlessRecipeEntries(finder, features)
                        .map { candidate -> craftlessRecipeRecord(candidate.entry, candidate.craftable) }
                        .filter { recipe -> recipe.matchesCraftlessRecipeQuery(category, output, craftable) }
                        .take(limit)
                        .toList()
                buildJsonObject {
                    put("count", recipes.size)
                    put(
                        "recipes",
                        buildJsonArray {
                            recipes.forEach { recipe -> add(recipe) }
                        },
                    )
                }
            }
        return DriverActionResult(
            action = invocation.operation.id,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${mode.id} action ${invocation.operation.id} queried",
            data = data,
        )
    }

    private fun craftRecipe(invocation: DriverOperationInvocation): DriverActionResult {
        val count = invocation.arguments["count"]?.jsonPrimitive?.intOrNull ?: 1
        val handle =
            invocation.arguments["target"]?.recipeTargetHandle()
        if (count !in RECIPE_CRAFT_COUNT_RANGE) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-count",
                data =
                    if (handle == null) {
                        recipeCraftTargetFailure(
                            reason = "invalid-count",
                            requestedCount = count,
                            phase = "target-validation-failed",
                        )
                    } else {
                        recipeCraftFailure(
                            handle = handle,
                            reason = "invalid-count",
                            requestedCount = count,
                            phase = "target-validation-failed",
                        )
                    },
            )
        }
        val targetHandle =
            handle
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-target",
                    data =
                        recipeCraftTargetFailure(
                            reason = "missing-target",
                            requestedCount = count,
                            phase = "target-validation-failed",
                        ),
                )
        if (!targetHandle.startsWith("recipe.handle:")) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-recipe-handle",
                data =
                    recipeCraftFailure(
                        handle = targetHandle,
                        reason = "invalid-recipe-handle",
                        requestedCount = count,
                        phase = "target-validation-failed",
                    ),
            )
        }
        val recipeIndex =
            targetHandle.recipeHandleIndex()
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "invalid-recipe-handle",
                    data =
                        recipeCraftFailure(
                            handle = targetHandle,
                            reason = "invalid-recipe-handle",
                            requestedCount = count,
                            phase = "target-validation-failed",
                        ),
                )
        invocation.unavailableRecipeOperationResult()?.let { result -> return result }
        val clientGateway = gateway
        if (clientGateway == null || !clientGateway.isConnected()) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.UNSUPPORTED,
                message = "client-not-connected",
            )
        }
        val fillRequest =
            clientGateway.queryOnClient {
                val currentPlayer = requireNotNull(player) { "client is not connected to a server" }
                val currentInteractionManager =
                    requireNotNull(interactionManager) { "client interaction manager is unavailable" }
                val currentScreenHandler =
                    currentPlayer.currentScreenHandler
                        ?: return@queryOnClient recipeCraftFailure(
                            handle = targetHandle,
                            reason = "screen-handler-unavailable",
                            requestedCount = count,
                            phase = "recipe-fill-failed",
                        )
                val recipeId = NetworkRecipeId(recipeIndex)
                val recipeBook = currentPlayer.recipeBook
                val finder = RecipeFinder()
                currentPlayer.inventory.populateRecipeFinder(finder)
                recipeBook.refresh()
                val features = networkHandler?.enabledFeatures
                val matchingRecipe =
                    recipeBook
                        .craftlessRecipeEntries(finder, features)
                        .firstOrNull { candidate -> candidate.entry.id() == recipeId }
                        ?: return@queryOnClient recipeCraftFailure(
                            handle = targetHandle,
                            reason = "stale-recipe-handle",
                            requestedCount = count,
                            phase = "recipe-fill-failed",
                        )
                if (!matchingRecipe.craftable) {
                    return@queryOnClient recipeCraftFailure(
                        handle = targetHandle,
                        reason = "recipe-not-craftable",
                        requestedCount = count,
                        phase = "recipe-fill-failed",
                    )
                }
                val before = currentScreenHandler.stacks.toCraftlessInventoryFingerprint()
                val expectedOutput =
                    matchingRecipe.entry
                        .craftlessOutputItems()
                        .firstOrNull()
                        ?.toCraftlessRecipeItem()
                currentInteractionManager.clickRecipe(currentScreenHandler.syncId, recipeId, count > 1)
                currentPlayer.onRecipeDisplayed(recipeId)
                currentScreenHandler.sendContentUpdates()
                buildJsonObject {
                    put("handle", targetHandle)
                    put("accepted", true)
                    put("changed", false)
                    put("requested-count", count)
                    put("crafted-count", 0)
                    put("inventory-before", before)
                    put("inventory-after", before)
                    put("sync-id", currentScreenHandler.syncId)
                    expectedOutput?.let { output -> put("expected-output", output) }
                    put("phase", "recipe-fill-requested")
                }
            }
        val outputData =
            if (fillRequest.jsonObject["accepted"]?.jsonPrimitive?.booleanOrNull == true) {
                clientGateway.takeCraftingOutputAfterRecipeFill(
                    handle = targetHandle,
                    count = count,
                    before =
                        fillRequest.jsonObject["inventory-before"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            .orEmpty(),
                    expectedSyncId = fillRequest.jsonObject["sync-id"]?.jsonPrimitive?.intOrNull,
                    expectedOutput = fillRequest.jsonObject["expected-output"] as? JsonObject,
                )
            } else {
                fillRequest
            }
        val data =
            if (
                outputData.jsonObject["accepted"]?.jsonPrimitive?.booleanOrNull == true &&
                outputData.jsonObject["phase"]?.jsonPrimitive?.contentOrNull == "crafting-output-taken"
            ) {
                clientGateway.confirmCraftingInventoryAfterOutputTake(outputData.jsonObject)
            } else {
                outputData
            }
        val accepted = data.jsonObject["accepted"]?.jsonPrimitive?.booleanOrNull == true
        return DriverActionResult(
            action = invocation.operation.id,
            status = if (accepted) DriverActionStatus.ACCEPTED else DriverActionStatus.FAILED,
            message =
                data.jsonObject["reason"]?.jsonPrimitive?.contentOrNull
                    ?: "fabric ${mode.id} action ${invocation.operation.id} accepted",
            data = data,
        )
    }

    private fun FabricClientGateway.takeCraftingOutputAfterRecipeFill(
        handle: String,
        count: Int,
        before: String,
        expectedSyncId: Int?,
        expectedOutput: JsonObject?,
    ): JsonObject {
        var latest =
            recipeCraftPending(
                handle = handle,
                reason = "crafting-output-pending",
                before = before,
                syncId = expectedSyncId,
                requestedCount = count,
                attempt = 0,
            )
        repeat(CRAFTING_OUTPUT_WAIT_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                Thread.sleep(CRAFTING_OUTPUT_WAIT_INTERVAL_MS)
            }
            latest =
                queryOnClient {
                    val currentPlayer =
                        player
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "client-not-connected",
                                requestedCount = count,
                                phase = "crafting-output-failed",
                            )
                    val currentInteractionManager =
                        interactionManager
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "interaction-manager-unavailable",
                                requestedCount = count,
                                phase = "crafting-output-failed",
                            )
                    val currentScreenHandler =
                        currentPlayer.currentScreenHandler
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "screen-handler-unavailable",
                                requestedCount = count,
                                phase = "crafting-output-failed",
                            )
                    if (expectedSyncId != null && currentScreenHandler.syncId != expectedSyncId) {
                        return@queryOnClient recipeCraftFailure(
                            handle = handle,
                            reason = "screen-handler-changed",
                            requestedCount = count,
                            phase = "crafting-output-failed",
                        )
                    }
                    val craftingHandler =
                        currentScreenHandler as? AbstractCraftingScreenHandler
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "crafting-handler-unavailable",
                                requestedCount = count,
                                phase = "crafting-output-failed",
                            )
                    val outputSlot = craftingHandler.getOutputSlot()
                    if (!outputSlot.hasStack()) {
                        return@queryOnClient recipeCraftPending(
                            handle = handle,
                            reason = "crafting-output-pending",
                            before = before,
                            syncId = currentScreenHandler.syncId,
                            requestedCount = count,
                            attempt = attempt + 1,
                        )
                    }
                    val actualOutput = outputSlot.stack.toCraftlessRecipeItem().toCraftlessRecipeItem()
                    if (expectedOutput != null && !actualOutput.matchesCraftlessRecipeOutput(expectedOutput)) {
                        return@queryOnClient recipeCraftFailure(
                            handle = handle,
                            reason = "crafting-output-mismatch",
                            requestedCount = count,
                            phase = "crafting-output-failed",
                            extra =
                                buildJsonObject {
                                    put("expected-output", expectedOutput)
                                    put("actual-output", actualOutput)
                                },
                        )
                    }
                    val outputStackCount = outputSlot.stack.count
                    currentInteractionManager.clickSlot(
                        currentScreenHandler.syncId,
                        outputSlot.id,
                        0,
                        SlotActionType.QUICK_MOVE,
                        currentPlayer,
                    )
                    currentScreenHandler.sendContentUpdates()
                    val after = currentScreenHandler.stacks.toCraftlessInventoryFingerprint()
                    val changed = before != after
                    buildJsonObject {
                        put("handle", handle)
                        put("accepted", true)
                        put("changed", changed)
                        put("requested-count", count)
                        put("crafted-count", if (changed) outputStackCount else 0)
                        put("inventory-before", before)
                        put("inventory-after", after)
                        put("sync-id", currentScreenHandler.syncId)
                        put("output-slot", outputSlot.id)
                        put("phase", "crafting-output-taken")
                    }
                }
            val reason = latest["reason"]?.jsonPrimitive?.contentOrNull
            if (reason != "crafting-output-pending") {
                return latest
            }
        }
        return latest
    }

    private fun FabricClientGateway.confirmCraftingInventoryAfterOutputTake(taken: JsonObject): JsonObject {
        val handle = taken["handle"]?.jsonPrimitive?.contentOrNull ?: return taken
        val before = taken["inventory-before"]?.jsonPrimitive?.contentOrNull ?: return taken
        val expectedSyncId = taken["sync-id"]?.jsonPrimitive?.intOrNull
        val requestedCount = taken["requested-count"]?.jsonPrimitive?.intOrNull ?: 1
        var latest = taken
        repeat(CRAFTING_CONFIRMATION_WAIT_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                Thread.sleep(CRAFTING_CONFIRMATION_WAIT_INTERVAL_MS)
            }
            latest =
                queryOnClient {
                    val currentPlayer =
                        player
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "client-not-connected",
                                requestedCount = requestedCount,
                                phase = "crafting-inventory-confirmation-failed",
                            )
                    val currentScreenHandler =
                        currentPlayer.currentScreenHandler
                            ?: return@queryOnClient recipeCraftFailure(
                                handle = handle,
                                reason = "screen-handler-unavailable",
                                requestedCount = requestedCount,
                                phase = "crafting-inventory-confirmation-failed",
                            )
                    if (expectedSyncId != null && currentScreenHandler.syncId != expectedSyncId) {
                        return@queryOnClient recipeCraftFailure(
                            handle = handle,
                            reason = "screen-handler-changed",
                            requestedCount = requestedCount,
                            phase = "crafting-inventory-confirmation-failed",
                        )
                    }
                    val after = currentScreenHandler.stacks.toCraftlessInventoryFingerprint()
                    val changed = before != after
                    taken.withCraftingConfirmation(
                        after = after,
                        changed = changed,
                        phase = if (changed) "crafting-inventory-confirmed" else "crafting-output-taken",
                        attempt = attempt + 1,
                        reason = if (changed) null else "crafting-inventory-confirmation-pending",
                    )
                }
            if (latest["reason"]?.jsonPrimitive?.contentOrNull != "crafting-inventory-confirmation-pending") {
                return latest
            }
        }
        return latest
    }

    private fun queryEntities(invocation: DriverOperationInvocation): DriverActionResult {
        val radius = invocation.arguments["radius"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_ENTITY_QUERY_RADIUS
        val limit = invocation.arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_ENTITY_QUERY_LIMIT
        if (radius <= 0.0) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-radius",
                data = entityQueryFailure("invalid-radius"),
            )
        }
        if (limit !in ENTITY_QUERY_LIMIT_RANGE) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-limit",
                data = entityQueryFailure("invalid-limit"),
            )
        }
        val clientGateway = gateway
        if (clientGateway == null || !clientGateway.isConnected()) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.UNSUPPORTED,
                message = "client-not-connected",
            )
        }
        val data =
            clientGateway.queryOnClient {
                val currentPlayer = requireNotNull(player) { "client is not connected to a server" }
                val currentWorld = requireNotNull(world) { "client world is unavailable" }
                val nearby =
                    currentWorld
                        .getOtherEntities(currentPlayer, currentPlayer.boundingBox.expand(radius)) { entity ->
                            !entity.isSpectator
                        }.asSequence()
                        .sortedBy { entity -> entity.squaredDistanceTo(currentPlayer) }
                        .take(limit)
                        .toList()
                buildJsonObject {
                    put("origin", currentPlayer.pos.toCraftlessJson())
                    put("radius", radius)
                    put("count", nearby.size)
                    put(
                        "entities",
                        buildJsonArray {
                            nearby.forEach { entity ->
                                add(entity.toCraftlessEntityData(currentPlayer))
                            }
                        },
                    )
                }
            }
        return DriverActionResult(
            action = invocation.operation.id,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${mode.id} action ${invocation.operation.id} queried",
            data = data,
        )
    }

    private fun attackEntity(invocation: DriverOperationInvocation): DriverActionResult {
        val maxDistance = invocation.arguments["max-distance"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_ENTITY_ATTACK_DISTANCE
        if (maxDistance <= 0.0) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-max-distance",
                data = entityAttackFailure(reason = "invalid-max-distance"),
            )
        }
        val targetHandle =
            invocation.arguments["target"]
                ?.entityTargetHandle()
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-target",
                    data = entityAttackFailure(reason = "missing-target"),
                )
        val entityId =
            targetHandle.entityHandleId()
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "invalid-entity-handle",
                    data = entityAttackFailure(handle = targetHandle, reason = "invalid-entity-handle"),
                )
        val clientGateway = gateway
        if (clientGateway == null || !clientGateway.isConnected()) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.UNSUPPORTED,
                message = "client-not-connected",
            )
        }
        val data =
            clientGateway.queryOnClient {
                val currentPlayer = player ?: return@queryOnClient entityAttackFailure(targetHandle, "client-not-connected")
                val currentWorld = world ?: return@queryOnClient entityAttackFailure(targetHandle, "world-unavailable")
                val currentInteractionManager =
                    interactionManager
                        ?: return@queryOnClient entityAttackFailure(targetHandle, "interaction-manager-unavailable")
                val target =
                    currentWorld
                        .getOtherEntities(currentPlayer, currentPlayer.boundingBox.expand(maxDistance)) { entity ->
                            entity.id == entityId && !entity.isSpectator
                        }.firstOrNull()
                        ?: return@queryOnClient entityAttackFailure(targetHandle, "entity-target-unavailable")
                val distance = target.distanceTo(currentPlayer).toDouble()
                if (distance > maxDistance) {
                    return@queryOnClient entityAttackFailure(targetHandle, "entity-target-out-of-range", distance)
                }
                currentInteractionManager.attackEntity(currentPlayer, target)
                currentPlayer.swingHand(Hand.MAIN_HAND)
                target.toCraftlessEntityAttackData(currentPlayer, distance)
            }
        val hit = data["hit"]?.jsonPrimitive?.booleanOrNull == true
        val reason = data["reason"]?.jsonPrimitive?.contentOrNull
        return DriverActionResult(
            action = invocation.operation.id,
            status = if (hit) DriverActionStatus.ACCEPTED else DriverActionStatus.FAILED,
            message = reason ?: "fabric ${mode.id} action ${invocation.operation.id} accepted",
            data = data,
        )
    }

    private fun queryBlocks(invocation: DriverOperationInvocation): DriverActionResult {
        val radius = invocation.arguments["radius"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_BLOCK_QUERY_RADIUS
        val limit = invocation.arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_BLOCK_QUERY_LIMIT
        val target = invocation.arguments.blockQueryTarget()
        val category =
            invocation.arguments["category"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        if (radius <= 0.0 || radius > MAX_BLOCK_QUERY_RADIUS) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-radius",
                data = blockQueryFailure("invalid-radius"),
            )
        }
        if (limit !in BLOCK_QUERY_LIMIT_RANGE) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.FAILED,
                message = "invalid-limit",
                data = blockQueryFailure("invalid-limit"),
            )
        }
        val clientGateway = gateway
        if (clientGateway == null || !clientGateway.isConnected()) {
            return DriverActionResult(
                action = invocation.operation.id,
                status = DriverActionStatus.UNSUPPORTED,
                message = "client-not-connected",
            )
        }
        val data =
            clientGateway.queryOnClient {
                val currentPlayer = requireNotNull(player) { "client is not connected to a server" }
                val currentWorld = requireNotNull(world) { "client world is unavailable" }
                val origin = currentPlayer.blockPos
                val matches = mutableListOf<CraftlessBlockQueryMatch>()
                if (target != null) {
                    currentWorld
                        .craftlessBlockQueryMatch(target, origin, currentPlayer.boundingBox)
                        .takeIf { match -> category == null || match.categoryMatches(category) }
                        ?.let(matches::add)
                } else {
                    val radiusBlocks = radius.toInt().coerceAtLeast(1)
                    for (x in (origin.x - radiusBlocks)..(origin.x + radiusBlocks)) {
                        for (y in (origin.y - radiusBlocks)..(origin.y + radiusBlocks)) {
                            for (z in (origin.z - radiusBlocks)..(origin.z + radiusBlocks)) {
                                val pos = BlockPos(x, y, z)
                                val distance = pos.distanceTo(origin)
                                if (distance > radius) {
                                    continue
                                }
                                currentWorld
                                    .craftlessBlockQueryMatch(pos, origin, currentPlayer.boundingBox)
                                    .takeIf { match -> match.categoryMatches(category) }
                                    ?.let(matches::add)
                            }
                        }
                    }
                }
                val limited = matches.sortedBy { it.distance }.take(limit)
                buildJsonObject {
                    put("origin", origin.toCraftlessPositionJson())
                    put("radius", radius)
                    put("count", limited.size)
                    put(
                        "blocks",
                        buildJsonArray {
                            limited.forEach { match ->
                                add(match.toCraftlessJson())
                            }
                        },
                    )
                }
            }
        return DriverActionResult(
            action = invocation.operation.id,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${mode.id} action ${invocation.operation.id} queried",
            data = data,
        )
    }

    private fun planNavigation(invocation: DriverOperationInvocation): DriverActionResult {
        val goalElement =
            invocation.arguments["goal"]
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-goal",
                )
        val goal = fabricBackendJson.decodeFromJsonElement(NavigationGoal.serializer(), goalElement)
        val plan = pathfinderBackend.plan(goal)
        return DriverActionResult(
            action = invocation.operation.id,
            status = plan.status.state.toDriverActionStatus(),
            message = plan.status.message,
            data =
                buildJsonObject {
                    put("plan-id", plan.id)
                    put("task-id", plan.status.id)
                    put("state", plan.status.state)
                },
        )
    }

    private fun followNavigation(invocation: DriverOperationInvocation): DriverActionResult {
        val planId =
            invocation.arguments["plan"]?.navigationPlanId()
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-plan",
                )
        val status = pathfinderBackend.follow(planId)
        return DriverActionResult(
            action = invocation.operation.id,
            status = status.state.toDriverActionStatus(),
            message = status.message,
            data =
                buildJsonObject {
                    put("task-id", status.id)
                    put("state", status.state)
                },
        )
    }

    private fun stopNavigation(invocation: DriverOperationInvocation): DriverActionResult {
        val status = pathfinderBackend.stop()
        return DriverActionResult(
            action = invocation.operation.id,
            status = status.state.toDriverActionStatus(),
            message = status.message,
            data =
                buildJsonObject {
                    put("task-id", status.id)
                    put("state", status.state)
                },
        )
    }

    private fun runTask(invocation: DriverOperationInvocation): DriverActionResult {
        val requestElement =
            invocation.arguments["request"]
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-request",
                )
        val request = fabricBackendJson.decodeFromJsonElement(NavigationTaskRequest.serializer(), requestElement)
        val status = taskExecutor.run(request)
        return DriverActionResult(
            action = invocation.operation.id,
            status = status.state.toDriverActionStatus(),
            message = status.message,
            data =
                buildJsonObject {
                    put("task-id", status.id)
                    put("state", status.state)
                },
        )
    }

    private fun queryTaskStatus(invocation: DriverOperationInvocation): DriverActionResult {
        val taskId =
            invocation.arguments["task"]?.jsonPrimitive?.contentOrNull
                ?: return DriverActionResult(
                    action = invocation.operation.id,
                    status = DriverActionStatus.FAILED,
                    message = "missing-task",
                )
        val status = taskExecutor.status(taskId)
        return DriverActionResult(
            action = invocation.operation.id,
            status = status.state.toDriverActionStatus(),
            message = status.message,
            data =
                buildJsonObject {
                    put("task-id", status.id)
                    put("state", status.state)
                },
        )
    }

    private fun unsupportedGraphOperation(invocation: DriverOperationInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.operation.id,
            status = DriverActionStatus.UNSUPPORTED,
            message = invocation.operation.availability.reason ?: "adapter-unavailable",
        )

    private enum class Mode(
        val id: String,
    ) {
        METADATA_ONLY("metadata-only"),
        REAL_CLIENT("real-client"),
    }

    companion object {
        @Volatile
        private var installed: FabricDriverBackend? = null

        fun metadataOnly(): FabricDriverBackend = metadataOnly(defaultFabricActionDiscovery())

        internal fun metadataOnly(
            actionDiscovery: FabricActionDiscovery = defaultFabricActionDiscovery(),
            pathfinderBackend: FabricPathfinderBackend = UnavailableFabricPathfinderBackend,
            taskExecutor: FabricTaskExecutor = UnavailableFabricTaskExecutor,
        ): FabricDriverBackend =
            FabricDriverBackend(
                mode = Mode.METADATA_ONLY,
                gateway = null,
                actionDiscovery = actionDiscovery,
                pathfinderBackend = pathfinderBackend,
                taskExecutor = taskExecutor,
            )

        fun real(gateway: FabricClientGateway = MinecraftFabricClientGateway()): FabricDriverBackend =
            real(gateway, defaultFabricActionDiscovery())

        internal fun real(
            gateway: FabricClientGateway,
            actionDiscovery: FabricActionDiscovery,
        ): FabricDriverBackend =
            real(
                gateway = gateway,
                actionDiscovery = actionDiscovery,
                runtimeMetadataProvider = FabricLoaderRuntimeMetadataProvider(gateway),
            )

        internal fun real(
            gateway: FabricClientGateway,
            actionDiscovery: FabricActionDiscovery = defaultFabricActionDiscovery(),
            runtimeMetadataProvider: FabricRuntimeMetadataProvider,
        ): FabricDriverBackend {
            val pathfinderBackend = ReflectiveFabricPathfinderBackend(gateway = gateway)
            return FabricDriverBackend(
                mode = Mode.REAL_CLIENT,
                gateway = gateway,
                actionDiscovery = actionDiscovery,
                runtimeMetadataProvider = runtimeMetadataProvider,
                pathfinderBackend = pathfinderBackend,
                taskExecutor = UnavailableFabricTaskExecutor,
            )
        }

        fun install(backend: FabricDriverBackend) {
            installed = backend
        }

        fun current(): FabricDriverBackend = installed ?: metadataOnly().also(::install)
    }
}

private fun Map<String, JsonElement>.blockQueryTarget(): BlockPos? {
    val target = this["target"] as? JsonObject ?: return null
    target["handle"]?.jsonPrimitive?.contentOrNull?.let { handle ->
        return parseCraftlessBlockQueryHandle(handle)
    }
    val position = target["position"]
    require(position is JsonObject) { "block query target requires handle or position" }
    return position.toBlockQueryPos()
}

private fun parseCraftlessBlockQueryHandle(handle: String): BlockPos {
    val parts = handle.split(":")
    require(parts.size == 4 && parts[0] == "world.block") { "block query target handle must use world.block:x:y:z" }
    val x = requireNotNull(parts[1].toIntOrNull()) { "block query target handle must use world.block:x:y:z" }
    val y = requireNotNull(parts[2].toIntOrNull()) { "block query target handle must use world.block:x:y:z" }
    val z = requireNotNull(parts[3].toIntOrNull()) { "block query target handle must use world.block:x:y:z" }
    return BlockPos(x, y, z)
}

private fun JsonObject.toBlockQueryPos(): BlockPos {
    val x = blockQueryCoordinate("x")
    val y = blockQueryCoordinate("y")
    val z = blockQueryCoordinate("z")
    return BlockPos(x, y, z)
}

private fun JsonObject.blockQueryCoordinate(name: String): Int {
    val primitive = this[name]?.jsonPrimitive
    return primitive?.intOrNull
        ?: primitive?.doubleOrNull?.toInt()
        ?: error("block query target position requires x, y, and z")
}

private fun World.craftlessBlockQueryMatch(
    pos: BlockPos,
    origin: BlockPos,
    playerBox: Box,
): CraftlessBlockQueryMatch {
    val state = getBlockState(pos)
    val blockCategory = state.toCraftlessBlockCategory()
    return CraftlessBlockQueryMatch(
        position = pos,
        category = blockCategory,
        distance = pos.distanceTo(origin),
        replaceable = state.isAir || state.isReplaceable,
        faces =
            Direction.entries.map { side ->
                val adjacentPosition = pos.offset(side)
                val adjacentState = getBlockState(adjacentPosition)
                CraftlessBlockQueryFace(
                    side = side,
                    adjacentPosition = adjacentPosition,
                    adjacentCategory = adjacentState.toCraftlessBlockCategory(),
                    replaceable = adjacentState.isAir || adjacentState.isReplaceable,
                    occupiedByPlayer = playerBox.intersects(adjacentPosition.toCraftlessBlockBox()),
                )
            },
    )
}

private fun CraftlessBlockQueryMatch.categoryMatches(category: String?): Boolean =
    when (category) {
        null -> this.category != "air"
        "any" -> true
        "non-air" -> this.category != "air"
        "block" -> this.category == "block" || this.category == "log"
        else -> this.category == category
    }

private fun Entity.toCraftlessEntityData(origin: Entity): JsonObject =
    buildJsonObject {
        put("handle", "entity.handle-$id")
        put("label", name.string)
        put("category", toCraftlessEntityCategory())
        put("distance", distanceTo(origin).toDouble())
        put("position", pos.toCraftlessJson())
        if (this@toCraftlessEntityData is LivingEntity) {
            put("alive", isAlive)
        }
    }

private fun Entity.toCraftlessEntityAttackData(
    origin: Entity,
    distance: Double,
): JsonObject =
    buildJsonObject {
        put("handle", "entity.handle-$id")
        put("label", name.string)
        put("category", toCraftlessEntityCategory())
        put("distance", distance)
        put("position", pos.toCraftlessJson())
        put("hit", true)
        if (this@toCraftlessEntityAttackData is LivingEntity) {
            put("alive", isAlive)
        }
        put("origin", origin.pos.toCraftlessJson())
    }

private fun Entity.toCraftlessEntityCategory(): String =
    when (this) {
        is PassiveEntity -> "passive"
        is HostileEntity -> "hostile"
        is LivingEntity -> "living"
        else -> "object"
    }

private data class CraftlessBlockQueryMatch(
    val position: BlockPos,
    val category: String,
    val distance: Double,
    val replaceable: Boolean,
    val faces: List<CraftlessBlockQueryFace>,
) {
    fun toCraftlessJson(): JsonObject =
        buildJsonObject {
            put("handle", position.toCraftlessBlockHandle())
            put("category", category)
            put("replaceable", replaceable)
            put("distance", distance)
            put("position", position.toCraftlessPositionJson())
            put(
                "faces",
                buildJsonArray {
                    faces.forEach { face -> add(face.toCraftlessJson()) }
                },
            )
        }
}

internal data class CraftlessBlockQueryFace(
    val side: Direction,
    val adjacentPosition: BlockPos,
    val adjacentCategory: String,
    val replaceable: Boolean,
    val occupiedByPlayer: Boolean,
) {
    fun toCraftlessJson(): JsonObject =
        buildJsonObject {
            put("side", side.name.lowercase())
            put("adjacent-handle", adjacentPosition.toCraftlessBlockHandle())
            put("adjacent-position", adjacentPosition.toCraftlessPositionJson())
            put("adjacent-category", adjacentCategory)
            put("replaceable", replaceable)
            put("occupied-by-player", occupiedByPlayer)
        }
}

private fun BlockState.toCraftlessBlockCategory(): String =
    when {
        isAir -> "air"
        isIn(BlockTags.LOGS) -> "log"
        !fluidState.isEmpty -> "fluid"
        else -> "block"
    }

private fun BlockState.matchesCraftlessBlockCategory(
    projectedCategory: String,
    requestedCategory: String?,
): Boolean =
    when (requestedCategory) {
        null -> projectedCategory != "air"
        "any" -> true
        "non-air" -> projectedCategory != "air"
        "block" -> projectedCategory == "block" || projectedCategory == "log"
        else -> projectedCategory == requestedCategory
    }

private fun BlockPos.distanceTo(origin: BlockPos): Double {
    val dx = x - origin.x
    val dy = y - origin.y
    val dz = z - origin.z
    return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
}

private fun BlockPos.toCraftlessBlockHandle(): String = "world.block:$x:$y:$z"

private fun BlockPos.toCraftlessBlockBox(): Box =
    Box(
        x.toDouble(),
        y.toDouble(),
        z.toDouble(),
        x + 1.0,
        y + 1.0,
        z + 1.0,
    )

private fun BlockPos.toCraftlessPositionJson(): JsonObject =
    buildJsonObject {
        put("x", x)
        put("y", y)
        put("z", z)
    }

private fun kotlinx.serialization.json.JsonElement.navigationPlanId(): String? =
    when (this) {
        is JsonPrimitive -> content
        is JsonObject ->
            this["id"]?.jsonPrimitive?.content
                ?: this["plan-id"]?.jsonPrimitive?.content
        else -> null
    }

private fun kotlinx.serialization.json.JsonElement.entityTargetHandle(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["handle"]?.jsonPrimitive?.contentOrNull
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

private fun kotlinx.serialization.json.JsonElement.recipeTargetHandle(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["handle"]?.jsonPrimitive?.contentOrNull
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

private fun String.entityHandleId(): Int? =
    removePrefix("entity.handle-")
        .takeIf { it != this }
        ?.toIntOrNull()

private fun String.recipeHandleIndex(): Int? =
    removePrefix("recipe.handle:")
        .takeIf { it != this }
        ?.toIntOrNull()

private fun String.fabricOperationAdapterKey(): String = "fabric.${replace(".", "-")}"

private fun RuntimeOperationNode.toDriverActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = id,
        schemaVersion = "1",
        arguments = arguments.mapValues { (_, schema) -> schema.toDriverActionArgument() },
        result = result.toDriverActionResultDescriptor(),
        source = DriverActionSource.RUNTIME_PROBE,
        availability = availability.toDriverActionAvailability(),
        availabilityReason = availability.reason,
    )

private fun RuntimeSchema.toDriverActionArgument(): DriverActionArgument =
    DriverActionArgument(
        type = type,
        required = required,
        properties = properties.mapValues { (_, schema) -> schema.toDriverActionArgument() },
        items = items?.toDriverActionArgument(),
    )

private fun RuntimeSchema.toDriverActionResultDescriptor(): DriverActionResultDescriptor =
    DriverActionResultDescriptor(
        properties =
            mapOf(
                "action" to DriverActionResultProperty("string"),
                "status" to DriverActionResultProperty("string"),
                "message" to DriverActionResultProperty("string"),
                "data" to toDriverActionResultProperty(),
            ),
        required = listOf("action", "status"),
    )

private fun RuntimeSchema.toDriverActionResultProperty(): DriverActionResultProperty =
    DriverActionResultProperty(
        type = type,
        properties = properties.mapValues { (_, schema) -> schema.toDriverActionResultProperty() },
        items = items?.toDriverActionResultProperty(),
    )

private fun RuntimeAvailability.toDriverActionAvailability(): DriverActionAvailability =
    when (state) {
        RuntimeAvailabilityState.AVAILABLE -> DriverActionAvailability.AVAILABLE
        RuntimeAvailabilityState.UNAVAILABLE -> DriverActionAvailability.UNAVAILABLE
    }

private fun net.minecraft.client.recipebook.ClientRecipeBook.craftlessRecipeEntries(
    finder: RecipeFinder,
    features: FeatureSet?,
): Sequence<CraftlessRecipeDisplayCandidate> {
    val groupedEntries =
        orderedResults
            .asSequence()
            .onEach { collection ->
                collection.populateRecipes(finder) { display ->
                    features == null || display.isEnabled(features)
                }
            }.flatMap { collection ->
                collection.allRecipes.asSequence().map { entry ->
                    CraftlessRecipeDisplayCandidate(entry, collection.isCraftable(entry.id()))
                }
            }
    val synchronizedEntries =
        (this as? ClientRecipeBookAccessor)
            ?.`craftless$getRecipes`()
            ?.values
            ?.asSequence()
            ?.filter { entry -> features == null || entry.display().isEnabled(features) }
            ?.map { entry -> CraftlessRecipeDisplayCandidate(entry, entry.isCraftable(finder)) }
            ?: emptySequence()
    return (groupedEntries + synchronizedEntries).distinctBy { candidate -> candidate.entry.id() }
}

private data class CraftlessRecipeDisplayCandidate(
    val entry: RecipeDisplayEntry,
    val craftable: Boolean,
)

private fun DriverOperationInvocation.unavailableRecipeOperationResult(): DriverActionResult? {
    if (operation.availability.state != RuntimeAvailabilityState.UNAVAILABLE) {
        return null
    }
    return DriverActionResult(
        action = operation.id,
        status = DriverActionStatus.UNSUPPORTED,
        message = operation.availability.reason ?: "operation-unavailable",
    )
}

private fun recipeQueryFailure(reason: String): JsonObject =
    buildJsonObject {
        put("count", 0)
        put("recipes", buildJsonArray {})
        put("reason", reason)
    }

private fun entityQueryFailure(reason: String): JsonObject =
    buildJsonObject {
        put("count", 0)
        put("entities", buildJsonArray {})
        put("reason", reason)
    }

private fun blockQueryFailure(reason: String): JsonObject =
    buildJsonObject {
        put("count", 0)
        put("blocks", buildJsonArray {})
        put("reason", reason)
    }

private fun entityAttackFailure(
    handle: String? = null,
    reason: String,
    distance: Double? = null,
): JsonObject =
    buildJsonObject {
        handle?.let { put("handle", it) }
        distance?.let { put("distance", it) }
        put("hit", false)
        put("reason", reason)
    }

private fun recipeCraftTargetFailure(
    reason: String,
    requestedCount: Int,
    phase: String,
): JsonObject =
    buildJsonObject {
        put("accepted", false)
        put("changed", false)
        put("requested-count", requestedCount)
        put("crafted-count", 0)
        put("phase", phase)
        put("reason", reason)
    }

private fun recipeCraftFailure(
    handle: String,
    reason: String,
    requestedCount: Int,
    phase: String,
    extra: JsonObject? = null,
): JsonObject =
    buildJsonObject {
        put("handle", handle)
        put("accepted", false)
        put("changed", false)
        put("requested-count", requestedCount)
        put("crafted-count", 0)
        put("phase", phase)
        put("reason", reason)
        extra?.forEach { (key, value) -> put(key, value) }
    }

private fun JsonObject.matchesCraftlessRecipeOutput(expected: JsonObject): Boolean {
    val actualLabel = this["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val expectedLabel = expected["label"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val actualCategory = this["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val expectedCategory = expected["category"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return actualLabel == expectedLabel && actualCategory == expectedCategory
}

private fun recipeCraftPending(
    handle: String,
    reason: String,
    before: String,
    syncId: Int?,
    requestedCount: Int,
    attempt: Int,
): JsonObject =
    buildJsonObject {
        put("handle", handle)
        put("accepted", true)
        put("changed", false)
        put("requested-count", requestedCount)
        put("crafted-count", 0)
        put("inventory-before", before)
        put("inventory-after", before)
        syncId?.let { value -> put("sync-id", value) }
        put("attempt", attempt)
        put("phase", "crafting-output-pending")
        put("reason", reason)
    }

private fun JsonObject.withCraftingConfirmation(
    after: String,
    changed: Boolean,
    phase: String,
    attempt: Int,
    reason: String?,
): JsonObject =
    buildJsonObject {
        this@withCraftingConfirmation.forEach { (key, value) -> put(key, value) }
        put("changed", changed)
        put("inventory-after", after)
        put("phase", phase)
        put("confirmation-attempt", attempt)
        if (reason != null) {
            put("reason", reason)
        }
    }

private fun Iterable<ItemStack>.toCraftlessInventoryFingerprint(): String {
    val entries =
        mapIndexedNotNull { index, stack ->
            if (stack.isEmpty) {
                null
            } else {
                "$index:${stack.item.translationKey}:${stack.count}"
            }
        }
    return fingerprint("inventory.fingerprint", entries)
}

private val fabricBackendJson = Json

private const val DEFAULT_ENTITY_QUERY_RADIUS = 16.0
private const val DEFAULT_ENTITY_QUERY_LIMIT = 25
private val ENTITY_QUERY_LIMIT_RANGE = 1..100
private const val DEFAULT_ENTITY_ATTACK_DISTANCE = 4.5
private const val DEFAULT_RECIPE_QUERY_LIMIT = 64
private val RECIPE_QUERY_LIMIT_RANGE = 1..256
private val RECIPE_CRAFT_COUNT_RANGE = 1..64
private const val CRAFTING_OUTPUT_WAIT_ATTEMPTS = 20
private const val CRAFTING_OUTPUT_WAIT_INTERVAL_MS = 50L
private const val CRAFTING_CONFIRMATION_WAIT_ATTEMPTS = 20
private const val CRAFTING_CONFIRMATION_WAIT_INTERVAL_MS = 50L
private const val DEFAULT_BLOCK_QUERY_RADIUS = 16.0
private const val MAX_BLOCK_QUERY_RADIUS = 32.0
private const val DEFAULT_BLOCK_QUERY_LIMIT = 64
private val BLOCK_QUERY_LIMIT_RANGE = 1..256

private fun String.toDriverActionStatus(): DriverActionStatus =
    when (this) {
        NavigationTaskState.PENDING,
        NavigationTaskState.RUNNING,
        NavigationTaskState.SUCCEEDED,
        -> DriverActionStatus.ACCEPTED
        NavigationTaskState.CANCELLED -> DriverActionStatus.ACCEPTED
        NavigationTaskState.FAILED -> DriverActionStatus.FAILED
        else -> DriverActionStatus.FAILED
    }

private fun DriverRuntimeMetadata.toCurrentLaneRuntimeIdentity(): FabricRuntimeIdentity =
    FabricRuntimeIdentity(
        gameVersion = FabricCompiledLaneMetadata.MINECRAFT_VERSION,
        loaderVersion = loaderVersion,
        fabricApiVersion = FabricCompiledLaneMetadata.FABRIC_API_VERSION,
        mappingsFingerprint = mappings,
        installedModsFingerprint = installedModsFingerprint,
        registryFingerprint = registryFingerprint,
        serverFeatureFingerprint = serverFeatureFingerprint,
        permissionsFingerprint = permissionsFingerprint,
    )

internal fun interface FabricRuntimeMetadataProvider {
    fun runtimeMetadata(clientId: String): DriverRuntimeMetadata
}

private fun staticFabricRuntimeMetadataProvider(): FabricRuntimeMetadataProvider =
    FabricRuntimeMetadataProvider {
        DriverRuntimeMetadata(
            loaderVersion = "unknown",
            driver = FABRIC_DRIVER_ID,
            driverVersion = FABRIC_DRIVER_VERSION,
            mappings = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
            installedModsFingerprint = "mods:metadata-only",
            registryFingerprint = "registries:metadata-only",
            serverFeatureFingerprint = "server-features:metadata-only",
            permissionsFingerprint = "permissions:local-client",
        )
    }

internal data class FabricRuntimeMetadataSnapshot(
    val loaderVersion: String,
    val driverVersion: String,
    val installedMods: List<String>,
    val registries: List<String>,
    val serverFeatures: List<String>,
    val permissionsFingerprint: String = "permissions:local-client",
)

internal class SnapshotFabricRuntimeMetadataProvider(
    private val snapshot: FabricRuntimeMetadataSnapshot,
) : FabricRuntimeMetadataProvider {
    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = snapshot.loaderVersion,
            driver = FABRIC_DRIVER_ID,
            driverVersion = snapshot.driverVersion,
            mappings = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
            installedModsFingerprint = fingerprint("mods", snapshot.installedMods),
            registryFingerprint = fingerprint("registries", snapshot.registries),
            serverFeatureFingerprint = fingerprint("server-features", snapshot.serverFeatures),
            permissionsFingerprint = snapshot.permissionsFingerprint,
        )
}

internal class GatewayFabricServerFeatureProvider(
    private val gateway: FabricClientGateway,
) {
    fun serverFeatures(): List<String> =
        try {
            gateway.queryOnClient {
                listOf(
                    "connection:${if (networkHandler != null && player != null) "connected" else "disconnected"}",
                    "server:${serverKind()}",
                    "local-server:$isConnectedToLocalServer",
                    "feature-set:${networkHandler?.enabledFeatures?.hashCode() ?: "none"}",
                )
            }
        } catch (_: ClassCastException) {
            listOf("server-features:unavailable")
        }
}

private class FabricLoaderRuntimeMetadataProvider(
    private val gateway: FabricClientGateway,
) : FabricRuntimeMetadataProvider {
    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        SnapshotFabricRuntimeMetadataProvider(runtimeMetadataSnapshot()).runtimeMetadata(clientId)

    private fun runtimeMetadataSnapshot(): FabricRuntimeMetadataSnapshot {
        val loader = FabricLoader.getInstance()
        return FabricRuntimeMetadataSnapshot(
            loaderVersion = loader.versionFor(FABRIC_LOADER_ID) ?: "unknown",
            driverVersion = loader.versionFor(FABRIC_DRIVER_ID) ?: FABRIC_DRIVER_VERSION,
            installedMods = loader.installedMods(),
            registries = safeRuntimeRegistryEntries(),
            serverFeatures =
                listOf("environment:${if (loader.safeIsDevelopmentEnvironment()) "dev" else "runtime"}") +
                    GatewayFabricServerFeatureProvider(gateway).serverFeatures(),
        )
    }
}

private fun net.minecraft.client.MinecraftClient.serverKind(): String =
    when {
        isInSingleplayer -> "singleplayer"
        currentServerEntry?.isLocal == true -> "local"
        currentServerEntry?.isRealm == true -> "realm"
        currentServerEntry != null -> "remote"
        else -> "none"
    }

private fun FabricLoader.versionFor(modId: String): String? =
    getModContainer(modId)
        .map { it.metadata.version.friendlyString }
        .orElse(null)

private fun FabricLoader.installedMods(): List<String> =
    allMods
        .map { "${it.metadata.id}@${it.metadata.version.friendlyString}" }
        .sorted()

private fun FabricLoader.safeIsDevelopmentEnvironment(): Boolean =
    try {
        isDevelopmentEnvironment
    } catch (_: NullPointerException) {
        false
    }

private fun runtimeRegistryEntries(): List<String> =
    listOf(
        registryEntries("block", Registries.BLOCK),
        registryEntries("item", Registries.ITEM),
        registryEntries("entity-type", Registries.ENTITY_TYPE),
        registryEntries("screen-handler", Registries.SCREEN_HANDLER),
        registryEntries("status-effect", Registries.STATUS_EFFECT),
        registryEntries("game-event", Registries.GAME_EVENT),
    ).flatten()

private fun safeRuntimeRegistryEntries(): List<String> =
    try {
        runtimeRegistryEntries()
    } catch (_: IllegalArgumentException) {
        listOf("registry:unavailable-unbootstrapped")
    } catch (_: IllegalStateException) {
        listOf("registry:unavailable-unbootstrapped")
    } catch (_: ExceptionInInitializerError) {
        listOf("registry:unavailable-unbootstrapped")
    } catch (_: NoClassDefFoundError) {
        listOf("registry:unavailable-unbootstrapped")
    }

private fun registryEntries(
    label: String,
    registry: Registry<*>,
): List<String> = registry.ids.map { id -> "$label:$id" }

private fun fingerprint(
    label: String,
    values: List<String>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        digest.update(value.encodeToByteArray())
        digest.update(0)
    }
    return "$label:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(FINGERPRINT_LENGTH)
}

private const val FABRIC_DRIVER_ID = "craftless-driver-fabric"
private const val FABRIC_DRIVER_VERSION = "0.1.0-SNAPSHOT"
private const val FABRIC_LOADER_ID = "fabricloader"
private const val FINGERPRINT_LENGTH = 16
