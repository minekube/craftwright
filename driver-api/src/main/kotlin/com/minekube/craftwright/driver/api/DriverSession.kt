package com.minekube.craftwright.driver.api

import com.minekube.craftwright.protocol.ClientState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

interface DriverSession {
    val clientId: String

    fun snapshot(): DriverClientSnapshot

    fun connect(target: ConnectionTarget): DriverClientSnapshot

    fun sendChat(command: ChatCommand): DriverEvent

    fun player(): PlayerSnapshot

    fun capabilities(): List<DriverCapabilityDescriptor>

    fun runtimeMetadata(): DriverRuntimeMetadata

    fun invoke(invocation: DriverCapabilityInvocation): DriverCapabilityResult

    fun stop(): DriverClientSnapshot

    fun events(): List<DriverEvent>
}

@Serializable
data class DriverClientSnapshot(
    val id: String,
    val state: ClientState,
)

@Serializable
data class ConnectionTarget(
    val host: String,
    val port: Int,
)

@Serializable
data class ChatCommand(
    val message: String,
)

@Serializable
data class DriverCapabilityDescriptor(
    val id: String,
    val schemaVersion: String,
    val arguments: Map<String, DriverCapabilityArgument> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "capability id is required" }
        require(schemaVersion.isNotBlank()) { "capability schema version is required" }
    }

    companion object {
        fun playerMove(): DriverCapabilityDescriptor =
            DriverCapabilityDescriptor(
                id = "player.move",
                schemaVersion = "1",
                arguments = mapOf(
                    "forward" to DriverCapabilityArgument("boolean"),
                    "backward" to DriverCapabilityArgument("boolean"),
                    "left" to DriverCapabilityArgument("boolean"),
                    "right" to DriverCapabilityArgument("boolean"),
                    "jump" to DriverCapabilityArgument("boolean"),
                    "sneak" to DriverCapabilityArgument("boolean"),
                    "sprint" to DriverCapabilityArgument("boolean"),
                    "ticks" to DriverCapabilityArgument("integer"),
                ),
            )

        fun playerChat(): DriverCapabilityDescriptor =
            DriverCapabilityDescriptor(
                id = "player.chat",
                schemaVersion = "1",
                arguments = mapOf(
                    "message" to DriverCapabilityArgument("string", required = true),
                ),
            )
    }
}

@Serializable
data class DriverRuntimeMetadata(
    val loaderVersion: String = "none",
    val driver: String,
    val driverVersion: String = "0.1.0-SNAPSHOT",
    val mappings: String = "none",
    val installedModsFingerprint: String = "none",
    val registryFingerprint: String = "none",
    val serverFeatureFingerprint: String = "none",
    val permissionsFingerprint: String = "local-fake",
) {
    init {
        require(loaderVersion.isNotBlank()) { "loader version is required" }
        require(driver.isNotBlank()) { "driver is required" }
        require(driverVersion.isNotBlank()) { "driver version is required" }
        require(mappings.isNotBlank()) { "mappings fingerprint is required" }
        require(installedModsFingerprint.isNotBlank()) { "installed mods fingerprint is required" }
        require(registryFingerprint.isNotBlank()) { "registry fingerprint is required" }
        require(serverFeatureFingerprint.isNotBlank()) { "server feature fingerprint is required" }
        require(permissionsFingerprint.isNotBlank()) { "permissions fingerprint is required" }
    }

    companion object {
        fun fake(): DriverRuntimeMetadata =
            DriverRuntimeMetadata(driver = "craftwright-fake")

        fun runtimeAdapter(): DriverRuntimeMetadata =
            DriverRuntimeMetadata(driver = "craftwright-driver-runtime")
    }
}

@Serializable
data class DriverCapabilityArgument(
    val type: String,
    val required: Boolean = false,
)

@Serializable
data class DriverCapabilityInvocation(
    val capability: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

fun Map<String, JsonElement>.booleanArgument(name: String): Boolean =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
    } == true

fun Map<String, JsonElement>.intArgument(name: String): Int? =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.intOrNull ?: primitive.content.toIntOrNull()
    }

fun Map<String, JsonElement>.stringArgument(name: String): String? =
    this[name]?.jsonPrimitive?.content

@Serializable
data class DriverCapabilityResult(
    val capability: String,
    val status: DriverCapabilityStatus,
    val message: String? = null,
)

@Serializable
enum class DriverCapabilityStatus {
    ACCEPTED,
    UNSUPPORTED,
    FAILED,
}

@Serializable
data class PlayerPosition(
    val x: Double,
    val y: Double,
    val z: Double,
)

@Serializable
data class PlayerSnapshot(
    val id: String,
    val name: String,
    val state: ClientState,
    val position: PlayerPosition,
)

@Serializable
data class DriverEvent(
    val type: DriverEventType,
    val client: String,
    val message: String? = null,
    val time: String = Instant.now().toString(),
)

@Serializable
enum class DriverEventType {
    CLIENT_CREATED,
    CLIENT_CONNECTED,
    CHAT,
    CLIENT_STOPPED,
}

class FakeDriverSession(
    override val clientId: String,
    private val profileName: String,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events = mutableListOf(
        DriverEvent(
            type = DriverEventType.CLIENT_CREATED,
            client = clientId,
            message = "created client $clientId",
        )
    )

    override fun snapshot(): DriverClientSnapshot =
        DriverClientSnapshot(id = clientId, state = state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        state = ClientState.CONNECTED
        events += DriverEvent(
            type = DriverEventType.CLIENT_CONNECTED,
            client = clientId,
            message = "connected $clientId to ${target.host}:${target.port}",
        )
        return snapshot()
    }

    override fun sendChat(command: ChatCommand): DriverEvent {
        require(command.message.isNotBlank()) { "chat message is required" }
        val event = DriverEvent(
            type = DriverEventType.CHAT,
            client = clientId,
            message = command.message,
        )
        events += event
        return event
    }

    override fun player(): PlayerSnapshot =
        PlayerSnapshot(
            id = clientId,
            name = profileName,
            state = state,
            position = PlayerPosition(0.0, 0.0, 0.0),
        )

    override fun capabilities(): List<DriverCapabilityDescriptor> =
        listOf(
            DriverCapabilityDescriptor.playerMove(),
            DriverCapabilityDescriptor.playerChat(),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata.fake()

    override fun invoke(invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        require(invocation.capability.isNotBlank()) { "capability is required" }
        return when (invocation.capability) {
            "player.chat" -> {
                val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
                val event = sendChat(ChatCommand(message))
                DriverCapabilityResult(
                    capability = invocation.capability,
                    status = DriverCapabilityStatus.ACCEPTED,
                    message = event.message,
                )
            }

            "player.move" -> DriverCapabilityResult(
                capability = invocation.capability,
                status = DriverCapabilityStatus.ACCEPTED,
                message = "accepted ${invocation.capability} for $clientId",
            )

            else -> DriverCapabilityResult(
                capability = invocation.capability,
                status = DriverCapabilityStatus.UNSUPPORTED,
                message = "unsupported fake capability ${invocation.capability}",
            )
        }
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        events += DriverEvent(
            type = DriverEventType.CLIENT_STOPPED,
            client = clientId,
            message = "stopped client $clientId",
        )
        return snapshot()
    }

    override fun events(): List<DriverEvent> =
        events.toList()
}
