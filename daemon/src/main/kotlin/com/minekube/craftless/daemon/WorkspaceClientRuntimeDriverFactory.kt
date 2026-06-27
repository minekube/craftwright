package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.CacheLaunchPlan
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.InstanceFiles
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class WorkspaceClientRuntimeDriverFactory(
    private val workspaceRoot: Path,
    private val launcher: ClientRuntimeLauncher = ProcessClientRuntimeLauncher(),
) : DriverSessionFactory {
    private val root = workspaceRoot.toAbsolutePath().normalize()
    private val prepared = linkedMapOf<String, PreparedClientRuntime>()

    suspend fun prepare(
        request: CreateClientRequest,
        cachePreparationService: CachePreparationService,
    ): PreparedClientRuntime {
        val cache =
            cachePreparationService.prepare(
                CachePrepareRequest(
                    minecraftVersion = request.version,
                    loader = request.loader,
                ),
            )
        val files = request.instanceFiles()
        val launch = launcher.launch(request, cache, files, root)
        return PreparedClientRuntime(
            request = request,
            prepared = cache,
            files = files,
            launch = launch,
        ).also { runtime ->
            prepared[request.id] = runtime
        }
    }

    override fun create(request: CreateClientRequest): DriverSession {
        val runtime = prepared.remove(request.id) ?: error("client ${request.id} runtime was not prepared")
        return PreparedClientRuntimeDriverSession(clientId = request.id, runtime = runtime)
    }
}

interface ClientRuntimeLauncher {
    fun launch(
        request: CreateClientRequest,
        prepared: CachePrepareResult,
        files: InstanceFiles,
        workspaceRoot: Path,
    ): ClientRuntimeLaunch
}

data class ClientRuntimeLaunch(
    val status: ClientRuntimeLaunchStatus,
    val pid: Long? = null,
    val command: List<String> = emptyList(),
    val message: String? = null,
    val process: Process? = null,
)

enum class ClientRuntimeLaunchStatus {
    LAUNCHED,
}

data class PreparedClientRuntime(
    val request: CreateClientRequest,
    val prepared: CachePrepareResult,
    val files: InstanceFiles,
    val launch: ClientRuntimeLaunch,
)

class ProcessClientRuntimeLauncher : ClientRuntimeLauncher {
    override fun launch(
        request: CreateClientRequest,
        prepared: CachePrepareResult,
        files: InstanceFiles,
        workspaceRoot: Path,
    ): ClientRuntimeLaunch {
        val command = launchCommand(request, prepared.launch, files, workspaceRoot)
        val logs = workspaceRoot.resolve(files.logs).normalize()
        Files.createDirectories(logs)
        val log = logs.resolve("client.log")
        val process =
            ProcessBuilder(command)
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                .start()
        return ClientRuntimeLaunch(
            status = ClientRuntimeLaunchStatus.LAUNCHED,
            pid = process.pid(),
            command = command,
            message = "launched client ${request.id}",
            process = process,
        )
    }

    private fun launchCommand(
        request: CreateClientRequest,
        launch: CacheLaunchPlan,
        files: InstanceFiles,
        workspaceRoot: Path,
    ): List<String> {
        val javaExecutable = requireNotNull(launch.javaExecutable) { "prepared client launch plan is missing javaExecutable" }
        val argumentsHandle = requireNotNull(launch.arguments) { "prepared client launch plan is missing arguments" }
        val java = workspaceRoot.resolveHandleOrPath(javaExecutable).toString()
        val argumentsFile = workspaceRoot.resolveHandleOrPath(argumentsHandle)
        val arguments = launcherJson.decodeFromString<ClientLaunchArgumentsFile>(Files.readString(argumentsFile))
        val variables = request.clientLaunchVariables(files)
        return listOf(java) +
            arguments.jvm.resolveClientLaunchVariables(variables) +
            arguments.mainClass +
            arguments.game.resolveClientLaunchVariables(variables)
    }
}

private class PreparedClientRuntimeDriverSession(
    override val clientId: String,
    private val runtime: PreparedClientRuntime,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events =
        mutableListOf(
            DriverEvent(
                type = DriverEventType.CLIENT_CREATED,
                client = clientId,
                message = "launched client $clientId from ${runtime.prepared.manifest}",
            ),
        )

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = runtime.loaderVersion(),
            driver = "craftless-prepared-client-runtime",
            mappings = "runtime-launch-plan",
            installedModsFingerprint = "mods:${runtime.prepared.loader.name.lowercase()}",
            registryFingerprint = "registries:unattached",
            serverFeatureFingerprint = "server-features:unattached",
            permissionsFingerprint = "permissions:workspace",
        )

    override fun runtimeGraph(): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    override fun operationAdapters(): DriverOperationAdapters = DriverOperationAdapters.empty()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "client runtime is launched but no in-client driver is attached",
        )

    override fun stop(): DriverClientSnapshot {
        runtime.stopProcess()
        state = ClientState.STOPPED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_STOPPED,
                client = clientId,
                message = "stopped client $clientId",
            )
        return snapshot()
    }

    override fun events(): List<DriverEvent> = events.toList()
}

@Serializable
private data class ClientLaunchArgumentsFile(
    val mainClass: String,
    val jvm: List<String> = emptyList(),
    val game: List<String> = emptyList(),
)

private fun CreateClientRequest.instanceFiles(): InstanceFiles = InstanceFiles.forInstance("$id-$version-${loader.name.lowercase()}")

private fun PreparedClientRuntime.loaderVersion(): String = prepared.loaderVersion ?: prepared.loader.name.lowercase()

private fun PreparedClientRuntime.stopProcess() {
    val process = launch.process ?: return
    if (!process.isAlive) {
        return
    }
    process.destroy()
    if (!process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
    }
}

private fun Path.resolveHandleOrPath(value: String): Path {
    val path = Path.of(value)
    return if (path.isAbsolute) path.normalize() else resolve(path).normalize()
}

private fun CreateClientRequest.clientLaunchVariables(files: InstanceFiles): Map<String, String> =
    mapOf(
        "assets_index_name" to version,
        "auth_access_token" to "0",
        "auth_player_name" to profile.name,
        "auth_uuid" to offlineUuid(profile.name),
        "auth_xuid" to "",
        "clientid" to "",
        "gameRoot" to files.gameRoot,
        "launcher_name" to "craftless",
        "launcher_version" to "0",
        "quickPlayPath" to "${files.gameRoot}/quickplay",
        "quickPlayMultiplayer" to "",
        "quickPlayRealms" to "",
        "quickPlaySingleplayer" to "",
        "resolution_height" to "",
        "resolution_width" to "",
        "user_type" to "legacy",
        "version_type" to "release",
    )

private fun offlineUuid(name: String): String = UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8)).toString()

private fun List<String>.resolveClientLaunchVariables(variables: Map<String, String>): List<String> {
    val resolved = map { argument -> argument.resolveClientLaunchVariables(variables) }
    return buildList {
        var index = 0
        while (index < resolved.size) {
            val current = resolved[index]
            val next = resolved.getOrNull(index + 1)
            if (current.startsWith("--") && next.isNullOrBlank()) {
                index += 2
                continue
            }
            if (current.isNotBlank()) {
                add(current)
            }
            index += 1
        }
    }
}

private fun String.resolveClientLaunchVariables(variables: Map<String, String>): String =
    CLIENT_LAUNCH_VARIABLE_PATTERN.replace(this) { match ->
        variables[match.groupValues[1]].orEmpty()
    }

private val launcherJson = Json { ignoreUnknownKeys = true }

private const val PROCESS_STOP_TIMEOUT_SECONDS = 2L

private val CLIENT_LAUNCH_VARIABLE_PATTERN = Regex("""\{\{([^}]+)}}""")
