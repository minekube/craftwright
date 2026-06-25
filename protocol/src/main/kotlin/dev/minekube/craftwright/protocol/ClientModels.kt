package dev.minekube.craftwright.protocol

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
)

@Serializable
data class CreateClientRequest(
    val id: String,
    val version: String,
    val loader: Loader,
    val profile: Profile,
)

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
