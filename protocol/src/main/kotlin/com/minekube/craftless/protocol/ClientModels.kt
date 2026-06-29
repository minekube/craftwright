package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftVersion(
    val id: String,
)

@Serializable
enum class Loader {
    FABRIC,
    VANILLA,
    NEOFORGE,
    FORGE,
    QUILT,
}

@Serializable
data class Profile(
    val kind: ProfileKind,
    val name: String,
) {
    companion object {
        fun offline(name: String): Profile = Profile(ProfileKind.OFFLINE, name)
    }
}

@Serializable
enum class ProfileKind {
    OFFLINE,
    AUTHENTICATED,
}

@Serializable
enum class ClientWindowMode {
    NONE,
    VISIBLE,
}

@Serializable
enum class ClientAudioMode {
    MUTED,
    DEFAULT,
}

@Serializable
data class ClientPresentation(
    val window: ClientWindowMode = ClientWindowMode.NONE,
    val audio: ClientAudioMode = ClientAudioMode.MUTED,
)

@Serializable
data class Instance(
    val id: String,
    val version: MinecraftVersion,
    val loader: Loader,
    val files: InstanceFiles = InstanceFiles.forInstance(id),
)

@Serializable
data class InstanceFiles(
    val root: String,
    val gameRoot: String,
    val runtimeRoot: String,
    val cache: String,
    val mods: String,
    val config: String,
    val saves: String,
    val resourcePacks: String,
    val shaderPacks: String,
    val screenshots: String,
    val logs: String,
    val artifacts: String,
) {
    init {
        listOf(
            root,
            gameRoot,
            runtimeRoot,
            cache,
            mods,
            config,
            saves,
            resourcePacks,
            shaderPacks,
            screenshots,
            logs,
            artifacts,
        ).forEach { path ->
            require(path.isNotBlank()) { "instance file path is required" }
            require(!path.contains('\\')) { "instance file paths must use forward slashes" }
        }
    }

    companion object {
        fun forInstance(instanceId: String): InstanceFiles {
            require(instanceId.isCraftlessInstanceId()) { "instance id must be a file-safe segment" }
            val root = "instances/$instanceId"
            val gameRoot = "$root/minecraft"
            val runtimeRoot = "$root/runtime"
            return InstanceFiles(
                root = root,
                gameRoot = gameRoot,
                runtimeRoot = runtimeRoot,
                cache = "$root/cache",
                mods = "$gameRoot/mods",
                config = "$gameRoot/config",
                saves = "$gameRoot/saves",
                resourcePacks = "$gameRoot/resourcepacks",
                shaderPacks = "$gameRoot/shaderpacks",
                screenshots = "$gameRoot/screenshots",
                logs = "$runtimeRoot/logs",
                artifacts = "$runtimeRoot/artifacts",
            )
        }
    }
}

@Serializable
data class CreateClientRequest(
    val id: String,
    val version: String,
    val loader: Loader,
    val loaderVersion: String? = null,
    val profile: Profile? = null,
    val presentation: ClientPresentation = ClientPresentation(),
) {
    init {
        require(id.isCraftlessClientId()) { "client id must be a route-safe segment" }
        loaderVersion?.let { requireFileSafeCacheSegment(it, "loader version") }
    }

    fun resolvedProfile(): Profile = profile ?: Profile.offline(defaultOfflineProfileName(id))
}

private fun defaultOfflineProfileName(clientId: String): String {
    val candidate =
        clientId
            .filter { it.isLetterOrDigit() }
            .replaceFirstChar { char -> char.uppercaseChar() }
            .take(MAX_OFFLINE_PROFILE_NAME_LENGTH)
    return candidate.ifBlank { "Player" }
}

const val MAX_OFFLINE_PROFILE_NAME_LENGTH: Int = 16

fun String.isCraftlessClientId(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))

fun String.isCraftlessInstanceId(): Boolean =
    matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) &&
        !contains("..") &&
        !endsWith(".")

@Serializable
data class Client(
    val id: String,
    val instance: Instance,
    val profile: Profile,
    val presentation: ClientPresentation = ClientPresentation(),
    val state: ClientState,
)

@Serializable
enum class ClientState {
    CREATED,
    RUNNING,
    CONNECTED,
    STOPPED,
}
