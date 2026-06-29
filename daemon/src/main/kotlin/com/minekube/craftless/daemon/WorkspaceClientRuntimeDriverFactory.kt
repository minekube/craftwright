package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
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
import com.minekube.craftless.protocol.ClientAudioMode
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
        val loaderVersion = request.loaderVersion ?: preferredLoaderVersion(request, cachePreparationService)
        val driverModRequest =
            cachePreparationService.resolveClientRuntimeDriverModRequest(
                CachePrepareRequest(
                    minecraftVersion = request.version,
                    loader = request.loader,
                    loaderVersion = loaderVersion,
                ),
            )
        val driverMods = driverModProvider.modsFor(driverModRequest)
        val cache =
            cachePreparationService
                .prepare(
                    CachePrepareRequest(
                        minecraftVersion = request.version,
                        loader = request.loader,
                        loaderVersion = loaderVersion,
                    ),
                ).withConfiguredDriverMods(driverMods)
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

    private suspend fun preferredLoaderVersion(
        request: CreateClientRequest,
        cachePreparationService: CachePreparationService,
    ): String? {
        val directRequest =
            ClientRuntimeDriverModRequest(
                loader = request.loader,
                minecraftVersion = request.version,
                loaderVersion = null,
            )
        driverModProvider.preferredLoaderVersion(directRequest)?.let { return it }
        val resolvedVersion = cachePreparationService.resolveMinecraftVersionAlias(request.version)
        if (resolvedVersion == request.version) return null
        return driverModProvider.preferredLoaderVersion(directRequest.copy(minecraftVersion = resolvedVersion))
    }

    private fun CachePrepareResult.withConfiguredDriverMods(driverMods: ClientRuntimeDriverMods): CachePrepareResult {
        val sources = driverMods.all().map { it.toAbsolutePath().normalize() }
        if (sources.isEmpty()) return this
        val preparedMods =
            sources.distinct().map { source ->
                require(Files.isRegularFile(source)) { "configured Craftless runtime mod does not exist: $source" }
                val handle = "cache/mods/craftless/${source.sha256Hex()}.jar"
                val target = root.resolveHandleOrPath(handle)
                Files.createDirectories(target.parent)
                if (source != target) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.FABRIC_MOD,
                    handle = handle,
                    source = source.toUri().toString(),
                    status = CachePreparedArtifactStatus.CACHED,
                )
            }
        val augmentedArtifacts = artifacts + preparedMods
        return copy(
            artifacts = augmentedArtifacts,
            launch =
                launch.copy(
                    mods = (launch.mods + preparedMods.map { it.handle }).distinct(),
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

    fun modsFor(request: ClientRuntimeDriverModRequest): ClientRuntimeDriverMods = ClientRuntimeDriverMods(primary = modFor(request))

    fun preferredLoaderVersion(request: ClientRuntimeDriverModRequest): String? = null
}

data class ClientRuntimeDriverMods(
    val primary: Path?,
    val runtimeMods: List<Path> = emptyList(),
) {
    init {
        runtimeMods.forEach { mod ->
            require(mod.toString().isNotBlank()) { "runtime mod path must not be blank" }
        }
    }

    fun all(): List<Path> = listOfNotNull(primary) + runtimeMods
}

data class ClientRuntimeDriverModRequest(
    val loader: Loader,
    val minecraftVersion: String,
    val loaderVersion: String?,
    val fabricApiVersion: String? = null,
    val javaMajorVersion: Int? = null,
    val mappingsFingerprint: String? = null,
) {
    init {
        require(minecraftVersion.isNotBlank()) { "driver mod Minecraft version is required" }
        fabricApiVersion?.let { require(it.isNotBlank()) { "driver mod Fabric API version must not be blank" } }
        javaMajorVersion?.let { require(it > 0) { "driver mod Java major version must be positive" } }
        mappingsFingerprint?.let { require(it.isNotBlank()) { "driver mod mappings fingerprint must not be blank" } }
    }
}

object NoClientRuntimeDriverModProvider : ClientRuntimeDriverModProvider {
    override fun modFor(request: ClientRuntimeDriverModRequest): Path? = null
}

class ConfiguredClientRuntimeDriverModProvider(
    private val environment: Map<String, String> = System.getenv(),
) : ClientRuntimeDriverModProvider {
    override fun preferredLoaderVersion(request: ClientRuntimeDriverModRequest): String? {
        if (request.loader != Loader.FABRIC || request.loaderVersion != null) return null
        val manifestPath = configuredManifestPath() ?: return null
        return manifestEntriesFor(request, manifestPath)
            .firstOrNull { entry -> entry.loaderVersion != null }
            ?.loaderVersion
    }

    override fun modFor(request: ClientRuntimeDriverModRequest): Path? = modsFor(request).primary

    override fun modsFor(request: ClientRuntimeDriverModRequest): ClientRuntimeDriverMods {
        val manifestPath = configuredManifestPath()
        if (manifestPath != null) {
            return manifestModsFor(request, manifestPath)
                ?: if (request.loader == Loader.FABRIC) {
                    throw IllegalArgumentException(
                        "driver mod manifest has no Fabric entry for " +
                            request.runtimeIdentityLabel(),
                    )
                } else {
                    ClientRuntimeDriverMods(primary = null)
                }
        }
        return when (request.loader) {
            Loader.FABRIC ->
                ClientRuntimeDriverMods(
                    primary = environment[CRAFTLESS_FABRIC_DRIVER_MOD]?.takeIf { it.isNotBlank() }?.let(Path::of),
                )
            else -> ClientRuntimeDriverMods(primary = null)
        }
    }

    private fun configuredManifestPath(): Path? =
        environment[CRAFTLESS_DRIVER_MOD_MANIFEST]
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()

    private fun manifestModsFor(
        request: ClientRuntimeDriverModRequest,
        manifestPath: Path,
    ): ClientRuntimeDriverMods? {
        val entry =
            manifestEntriesFor(request, manifestPath)
                .filter { entry -> entry.matches(request) }
                .maxWithOrNull(
                    compareBy<ConfiguredDriverModManifestEntry> { entry -> if (entry.loaderVersion == request.loaderVersion) 1 else 0 }
                        .thenBy { entry -> entry.runtimeIdentitySpecificity() },
                )
                ?: return null
        return ClientRuntimeDriverMods(
            primary = entry.path.resolveManifestEntryPath(manifestPath),
            runtimeMods = entry.runtimeMods.map { runtimeMod -> runtimeMod.resolveManifestEntryPath(manifestPath) },
        )
    }

    private fun manifestEntriesFor(
        request: ClientRuntimeDriverModRequest,
        manifestPath: Path,
    ): List<ConfiguredDriverModManifestEntry> {
        val manifest = launcherJson.decodeFromString<ConfiguredDriverModManifest>(Files.readString(manifestPath))
        return manifest
            .entries
            .filter { entry ->
                entry.loader == request.loader &&
                    entry.minecraftVersion == request.minecraftVersion
            }
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
    val fabricApiVersion: String? = null,
    val javaMajorVersion: Int? = null,
    val mappingsFingerprint: String? = null,
    val path: String,
    val runtimeMods: List<String> = emptyList(),
) {
    fun matches(request: ClientRuntimeDriverModRequest): Boolean =
        (loaderVersion == request.loaderVersion || loaderVersion == null) &&
            optionalMatches(fabricApiVersion, request.fabricApiVersion) &&
            optionalMatches(javaMajorVersion, request.javaMajorVersion) &&
            optionalMatches(mappingsFingerprint, request.mappingsFingerprint)

    fun runtimeIdentitySpecificity(): Int = listOfNotNull(loaderVersion, fabricApiVersion, javaMajorVersion, mappingsFingerprint).size
}

private fun String.resolveManifestEntryPath(manifestPath: Path): Path {
    val path = Path.of(this)
    return if (path.isAbsolute) path.normalize() else manifestPath.parent.resolve(path).normalize()
}

private fun <T> optionalMatches(
    manifestValue: T?,
    requestValue: T?,
): Boolean = manifestValue == null || requestValue == null || manifestValue == requestValue

private fun ClientRuntimeDriverModRequest.runtimeIdentityLabel(): String =
    buildString {
        append(minecraftVersion)
        append(' ')
        append(loaderVersion ?: "default-loader")
        fabricApiVersion?.let { append(" fabricApiVersion=").append(it) }
        javaMajorVersion?.let { append(" javaMajorVersion=").append(it) }
        mappingsFingerprint?.let { append(" mappingsFingerprint=").append(it) }
    }

private val fabricApiArtifactVersionPattern = Regex("/fabric-api/([^/]+)/fabric-api-[^/]+\\.jar$")

private fun CachePrepareResult.fabricApiVersion(): String? =
    artifacts.firstNotNullOfOrNull { artifact ->
        if (artifact.kind != CachePreparedArtifactKind.FABRIC_MOD) {
            null
        } else {
            artifact.source?.let { source ->
                fabricApiArtifactVersionPattern.find(source)?.groupValues?.get(1)
            }
        }
    }

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
        materializePresentationOptions(request, files, workspaceRoot)
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

    private fun materializePresentationOptions(
        request: CreateClientRequest,
        files: InstanceFiles,
        workspaceRoot: Path,
    ) {
        if (request.presentation.audio != ClientAudioMode.MUTED) return
        val gameRoot = workspaceRoot.resolveHandleOrPath(files.gameRoot)
        Files.createDirectories(gameRoot)
        val options = gameRoot.resolve("options.txt")
        val existing = if (Files.isRegularFile(options)) Files.readAllLines(options, StandardCharsets.UTF_8) else emptyList()
        val preserved = existing.filter { line -> line.substringBefore(":") !in mutedSoundOptionKeys }
        Files.write(options, preserved + mutedSoundOptions.map { (key, value) -> "$key:$value" }, StandardCharsets.UTF_8)
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
    resolvedProfile().let { launchProfile ->
        mapOf(
            "assets_index_name" to version,
            "auth_access_token" to "0",
            "auth_player_name" to launchProfile.name,
            "auth_uuid" to offlineUuid(launchProfile.name),
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
    }

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

private val mutedSoundOptions =
    linkedMapOf(
        "soundDevice" to "\"\"",
        "soundCategory_master" to "0.0",
        "soundCategory_music" to "0.0",
        "soundCategory_record" to "0.0",
        "soundCategory_weather" to "0.0",
        "soundCategory_block" to "0.0",
        "soundCategory_hostile" to "0.0",
        "soundCategory_neutral" to "0.0",
        "soundCategory_player" to "0.0",
        "soundCategory_ambient" to "0.0",
        "soundCategory_voice" to "0.0",
        "soundCategory_ui" to "0.0",
    )

private val mutedSoundOptionKeys = mutedSoundOptions.keys

private const val PROCESS_STOP_TIMEOUT_SECONDS = 2L
private const val CRAFTLESS_CLIENT_ID = "CRAFTLESS_CLIENT_ID"
private const val CRAFTLESS_DAEMON_URL = "CRAFTLESS_DAEMON_URL"

private val CLIENT_LAUNCH_VARIABLE_PATTERN = Regex("""\{\{([^}]+)}}""")
