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
import com.minekube.craftless.protocol.CachePreparedArtifact
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.CachePreparedArtifactStatus
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.InstanceFiles
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class WorkspaceClientRuntimeDriverFactory(
    private val workspaceRoot: Path,
    private val launcher: ClientRuntimeLauncher = ProcessClientRuntimeLauncher(),
    private val driverModProvider: ClientRuntimeDriverModProvider = ConfiguredClientRuntimeDriverModProvider(),
) : DriverSessionFactory {
    private val root = workspaceRoot.toAbsolutePath().normalize()
    private val prepared = linkedMapOf<String, PreparedClientRuntime>()

    suspend fun prepare(
        request: CreateClientRequest,
        cachePreparationService: CachePreparationService,
        attachEnvironment: ClientDriverAttachEnvironment? = null,
    ): PreparedClientRuntime {
        val cache =
            cachePreparationService
                .prepare(
                    CachePrepareRequest(
                        minecraftVersion = request.version,
                        loader = request.loader,
                    ),
                ).withConfiguredDriverMod()
        val files = request.instanceFiles()
        val launch = launcher.launch(request, cache, files, root, attachEnvironment)
        return PreparedClientRuntime(
            request = request,
            prepared = cache,
            files = files,
            launch = launch,
        ).also { runtime ->
            prepared[request.id] = runtime
        }
    }

    private fun CachePrepareResult.withConfiguredDriverMod(): CachePrepareResult {
        val driverModRequest =
            ClientRuntimeDriverModRequest(
                loader = loader,
                minecraftVersion = minecraftVersion,
                loaderVersion = loaderVersion,
            )
        val source = driverModProvider.modFor(driverModRequest)?.toAbsolutePath()?.normalize() ?: return this
        require(Files.isRegularFile(source)) { "configured Craftless driver mod does not exist: $source" }
        val handle = "cache/mods/craftless/${source.sha256Hex()}.jar"
        val target = root.resolveHandleOrPath(handle)
        Files.createDirectories(target.parent)
        if (source != target) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
        val artifact =
            CachePreparedArtifact(
                kind = CachePreparedArtifactKind.FABRIC_MOD,
                handle = handle,
                source = source.toUri().toString(),
                status = CachePreparedArtifactStatus.CACHED,
            )
        val augmentedArtifacts = artifacts + artifact
        return copy(
            artifacts = augmentedArtifacts,
            launch =
                launch.copy(
                    mods = (launch.mods + handle).distinct(),
                ),
        )
    }

    override fun create(request: CreateClientRequest): DriverSession {
        val runtime = prepared.remove(request.id) ?: error("client ${request.id} runtime was not prepared")
        return PreparedClientRuntimeDriverSession(clientId = request.id, runtime = runtime)
    }
}

data class ClientDriverAttachEnvironment(
    val clientId: String,
    val daemonUrl: String,
) {
    init {
        require(clientId.isNotBlank()) { "attach client id must not be blank" }
        require(daemonUrl.isNotBlank()) { "attach daemon url must not be blank" }
    }
}

fun interface ClientRuntimeDriverModProvider {
    fun modFor(request: ClientRuntimeDriverModRequest): Path?
}

data class ClientRuntimeDriverModRequest(
    val loader: Loader,
    val minecraftVersion: String,
    val loaderVersion: String?,
) {
    init {
        require(minecraftVersion.isNotBlank()) { "driver mod Minecraft version is required" }
    }
}

object NoClientRuntimeDriverModProvider : ClientRuntimeDriverModProvider {
    override fun modFor(request: ClientRuntimeDriverModRequest): Path? = null
}

class ConfiguredClientRuntimeDriverModProvider(
    private val environment: Map<String, String> = System.getenv(),
) : ClientRuntimeDriverModProvider {
    override fun modFor(request: ClientRuntimeDriverModRequest): Path? =
        manifestModFor(request)
            ?: when (request.loader) {
                Loader.FABRIC -> environment[CRAFTLESS_FABRIC_DRIVER_MOD]?.takeIf { it.isNotBlank() }?.let(Path::of)
                else -> null
            }

    private fun manifestModFor(request: ClientRuntimeDriverModRequest): Path? {
        val manifestPath =
            environment[CRAFTLESS_DRIVER_MOD_MANIFEST]
                ?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?.toAbsolutePath()
                ?.normalize()
                ?: return null
        val manifest = launcherJson.decodeFromString<ConfiguredDriverModManifest>(Files.readString(manifestPath))
        val entry =
            manifest
                .entries
                .filter { entry ->
                    entry.loader == request.loader &&
                        entry.minecraftVersion == request.minecraftVersion &&
                        (entry.loaderVersion == request.loaderVersion || entry.loaderVersion == null)
                }.maxByOrNull { entry -> if (entry.loaderVersion == request.loaderVersion) 1 else 0 }
                ?: return null
        val path = Path.of(entry.path)
        return if (path.isAbsolute) path.normalize() else manifestPath.parent.resolve(path).normalize()
    }

    companion object {
        const val CRAFTLESS_DRIVER_MOD_MANIFEST: String = "CRAFTLESS_DRIVER_MOD_MANIFEST"
        const val CRAFTLESS_FABRIC_DRIVER_MOD: String = "CRAFTLESS_FABRIC_DRIVER_MOD"
    }
}

@Serializable
private data class ConfiguredDriverModManifest(
    val entries: List<ConfiguredDriverModManifestEntry> = emptyList(),
)

@Serializable
private data class ConfiguredDriverModManifestEntry(
    val loader: Loader,
    val minecraftVersion: String,
    val loaderVersion: String? = null,
    val path: String,
)

interface ClientRuntimeLauncher {
    fun launch(
        request: CreateClientRequest,
        prepared: CachePrepareResult,
        files: InstanceFiles,
        workspaceRoot: Path,
        attachEnvironment: ClientDriverAttachEnvironment? = null,
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
        attachEnvironment: ClientDriverAttachEnvironment?,
    ): ClientRuntimeLaunch {
        materializeLaunchMods(prepared.launch, files, workspaceRoot)
        val command = launchCommand(request, prepared.launch, files, workspaceRoot)
        val logs = workspaceRoot.resolve(files.logs).normalize()
        Files.createDirectories(logs)
        val log = logs.resolve("client.log")
        val processBuilder =
            ProcessBuilder(command)
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
        attachEnvironment?.let { environment ->
            processBuilder.environment()[CRAFTLESS_CLIENT_ID] = environment.clientId
            processBuilder.environment()[CRAFTLESS_DAEMON_URL] = environment.daemonUrl
        }
        val process = processBuilder.start()
        return ClientRuntimeLaunch(
            status = ClientRuntimeLaunchStatus.LAUNCHED,
            pid = process.pid(),
            command = command,
            message = "launched client ${request.id}",
            process = process,
        )
    }

    private fun materializeLaunchMods(
        launch: CacheLaunchPlan,
        files: InstanceFiles,
        workspaceRoot: Path,
    ) {
        if (launch.mods.isEmpty()) return
        val modsDirectory = workspaceRoot.resolveHandleOrPath(files.mods)
        Files.createDirectories(modsDirectory)
        launch.mods.forEach { handle ->
            val source = workspaceRoot.resolveHandleOrPath(handle)
            require(Files.isRegularFile(source)) { "prepared mod artifact does not exist: $handle" }
            val target = modsDirectory.resolve(source.fileName.toString()).normalize()
            require(target.startsWith(modsDirectory)) { "materialized mod target must stay under mods directory" }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
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

private fun Path.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
private const val CRAFTLESS_CLIENT_ID = "CRAFTLESS_CLIENT_ID"
private const val CRAFTLESS_DAEMON_URL = "CRAFTLESS_DAEMON_URL"

private val CLIENT_LAUNCH_VARIABLE_PATTERN = Regex("""\{\{([^}]+)}}""")
