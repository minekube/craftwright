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
    val mods: String,
    val config: String,
    val saves: String,
    val resourcePacks: String,
    val shaderPacks: String,
) {
    init {
        listOf(root, gameRoot, mods, config, saves, resourcePacks, shaderPacks).forEach { path ->
            require(path.isNotBlank()) { "instance file path is required" }
            require(!path.contains('\\')) { "instance file paths must use forward slashes" }
        }
    }

    companion object {
        fun forInstance(instanceId: String): InstanceFiles {
            require(instanceId.isCraftlessInstanceId()) { "instance id must be a file-safe segment" }
            val root = "instances/$instanceId"
            val gameRoot = "$root/minecraft"
            return InstanceFiles(
                root = root,
                gameRoot = gameRoot,
                mods = "$gameRoot/mods",
                config = "$gameRoot/config",
                saves = "$gameRoot/saves",
                resourcePacks = "$gameRoot/resourcepacks",
                shaderPacks = "$gameRoot/shaderpacks",
            )
        }
    }
}

@Serializable
data class CreateClientRequest(
    val id: String,
    val version: String,
    val loader: Loader,
    val profile: Profile,
) {
    init {
        require(id.isCraftlessClientId()) { "client id must be a route-safe segment" }
    }
}

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
    val state: ClientState,
)

@Serializable
enum class ClientState {
    CREATED,
    RUNNING,
    CONNECTED,
    STOPPED,
}
