package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverOperationAdapter
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverOperationInvocation
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.NavigationGoal
import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
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
    private val survivalTaskExecutor: FabricSurvivalTaskExecutor = RecordingSurvivalExecutor(),
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
        return DriverBackendResult(DriverBackendAction.CONNECT, "fabric ${mode.id} connect requested")
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> = discoveredActions(clientId).map { it.descriptor }

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = runtimeMetadataProvider.runtimeMetadata(clientId)

    override fun runtimeGraph(clientId: String): RuntimeCapabilityGraph =
        capabilityDiscovery.discover(
            FabricCapabilityProbeContext(
                clientId = clientId,
                modeId = mode.id,
                gateway = gateway,
                runtimeMetadata = runtimeMetadata(clientId),
                bindings = actionBindingsById,
            ),
        )

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
            "fabric.world-block-query" to blockQueryOperationAdapter(),
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

    private fun blockQueryOperationAdapter(): DriverOperationAdapter =
        DriverOperationAdapter { invocation ->
            if (invocation.operation.id != "world.block.query") {
                return@DriverOperationAdapter unsupportedGraphOperation(invocation)
            }
            queryBlocks(invocation)
        }

    private fun queryEntities(invocation: DriverOperationInvocation): DriverActionResult {
        val radius = invocation.arguments["radius"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_ENTITY_QUERY_RADIUS
        val limit = invocation.arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_ENTITY_QUERY_LIMIT
        require(radius > 0.0) { "entity query radius must be positive" }
        require(limit in ENTITY_QUERY_LIMIT_RANGE) { "entity query limit must be between 1 and 100" }
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

    private fun queryBlocks(invocation: DriverOperationInvocation): DriverActionResult {
        val radius = invocation.arguments["radius"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_BLOCK_QUERY_RADIUS
        val limit = invocation.arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_BLOCK_QUERY_LIMIT
        val category =
            invocation.arguments["category"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        require(radius > 0.0 && radius <= MAX_BLOCK_QUERY_RADIUS) {
            "block query radius must be between 0 and $MAX_BLOCK_QUERY_RADIUS"
        }
        require(limit in BLOCK_QUERY_LIMIT_RANGE) { "block query limit must be between 1 and 256" }
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
                val radiusBlocks = radius.toInt().coerceAtLeast(1)
                val matches = mutableListOf<CraftlessBlockQueryMatch>()
                for (x in (origin.x - radiusBlocks)..(origin.x + radiusBlocks)) {
                    for (y in (origin.y - radiusBlocks)..(origin.y + radiusBlocks)) {
                        for (z in (origin.z - radiusBlocks)..(origin.z + radiusBlocks)) {
                            val pos = BlockPos(x, y, z)
                            val distance = pos.distanceTo(origin)
                            if (distance > radius) {
                                continue
                            }
                            val state = currentWorld.getBlockState(pos)
                            val blockCategory = state.toCraftlessBlockCategory()
                            if (state.matchesCraftlessBlockCategory(blockCategory, category)) {
                                matches += CraftlessBlockQueryMatch(pos, blockCategory, distance)
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
        val status = survivalTaskExecutor.run(request)
        return DriverActionResult(
            action = invocation.operation.id,
            status = status.state.toDriverActionStatus(),
            message = status.message,
            data =
                buildJsonObject {
                    put("task", request.task)
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
        val status = survivalTaskExecutor.status(taskId)
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
            survivalTaskExecutor: FabricSurvivalTaskExecutor = RecordingSurvivalExecutor(),
        ): FabricDriverBackend =
            FabricDriverBackend(
                mode = Mode.METADATA_ONLY,
                gateway = null,
                actionDiscovery = actionDiscovery,
                pathfinderBackend = pathfinderBackend,
                survivalTaskExecutor = survivalTaskExecutor,
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
                survivalTaskExecutor =
                    RecordingSurvivalExecutor(
                        observations = FabricClientSurvivalObservationProvider(gateway),
                        pathfinderBackend = pathfinderBackend,
                        executionPorts = FabricClientSurvivalExecutionPorts(gateway),
                    ),
            )
        }

        fun install(backend: FabricDriverBackend) {
            installed = backend
        }

        fun current(): FabricDriverBackend = installed ?: metadataOnly().also(::install)
    }
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
) {
    fun toCraftlessJson(): JsonObject =
        buildJsonObject {
            put("handle", "world.block:${position.x}:${position.y}:${position.z}")
            put("category", category)
            put("distance", distance)
            put("position", position.toCraftlessPositionJson())
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

private fun String.fabricOperationAdapterKey(): String = "fabric.${replace(".", "-")}"

private val fabricBackendJson = Json

private const val DEFAULT_ENTITY_QUERY_RADIUS = 16.0
private const val DEFAULT_ENTITY_QUERY_LIMIT = 25
private val ENTITY_QUERY_LIMIT_RANGE = 1..100
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

internal fun interface FabricRuntimeMetadataProvider {
    fun runtimeMetadata(clientId: String): DriverRuntimeMetadata
}

private fun staticFabricRuntimeMetadataProvider(): FabricRuntimeMetadataProvider =
    FabricRuntimeMetadataProvider {
        DriverRuntimeMetadata(
            loaderVersion = "unknown",
            driver = FABRIC_DRIVER_ID,
            driverVersion = FABRIC_DRIVER_VERSION,
            mappings = FABRIC_MAPPINGS_FINGERPRINT,
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
            mappings = FABRIC_MAPPINGS_FINGERPRINT,
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
        gateway.queryOnClient {
            listOf(
                "connection:${if (networkHandler != null && player != null) "connected" else "disconnected"}",
                "server:${serverKind()}",
                "local-server:$isConnectedToLocalServer",
                "feature-set:${networkHandler?.enabledFeatures?.hashCode() ?: "none"}",
            )
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
            registries = runtimeRegistryEntries(),
            serverFeatures =
                listOf("environment:${if (loader.isDevelopmentEnvironment) "dev" else "runtime"}") +
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

private fun runtimeRegistryEntries(): List<String> =
    listOf(
        registryEntries("block", Registries.BLOCK),
        registryEntries("item", Registries.ITEM),
        registryEntries("entity-type", Registries.ENTITY_TYPE),
        registryEntries("screen-handler", Registries.SCREEN_HANDLER),
        registryEntries("status-effect", Registries.STATUS_EFFECT),
        registryEntries("game-event", Registries.GAME_EVENT),
    ).flatten()

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
private const val FABRIC_MAPPINGS_FINGERPRINT = "craftless-fabric-bindings"
private const val FINGERPRINT_LENGTH = 16
