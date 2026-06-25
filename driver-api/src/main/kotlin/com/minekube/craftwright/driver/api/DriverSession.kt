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

    fun player(): PlayerSnapshot

    fun actions(): List<DriverActionDescriptor>

    fun runtimeMetadata(): DriverRuntimeMetadata

    fun invoke(invocation: DriverActionInvocation): DriverActionResult

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
data class DriverActionDescriptor(
    val id: String,
    val schemaVersion: String,
    val arguments: Map<String, DriverActionArgument> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "action id is required" }
        require(schemaVersion.isNotBlank()) { "action schema version is required" }
    }

    companion object {
        fun playerMove(): DriverActionDescriptor =
            DriverActionDescriptor(
                id = "player.move",
                schemaVersion = "1",
                arguments = mapOf(
                    "forward" to DriverActionArgument("boolean"),
                    "backward" to DriverActionArgument("boolean"),
                    "left" to DriverActionArgument("boolean"),
                    "right" to DriverActionArgument("boolean"),
                    "jump" to DriverActionArgument("boolean"),
                    "sneak" to DriverActionArgument("boolean"),
                    "sprint" to DriverActionArgument("boolean"),
                    "ticks" to DriverActionArgument("integer"),
                ),
            )

        fun playerChat(): DriverActionDescriptor =
            DriverActionDescriptor(
                id = "player.chat",
                schemaVersion = "1",
                arguments = mapOf(
                    "message" to DriverActionArgument("string", required = true),
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
data class DriverActionArgument(
    val type: String,
    val required: Boolean = false,
)

@Serializable
data class DriverActionInvocation(
    val action: String,
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
data class DriverActionResult(
    val action: String,
    val status: DriverActionStatus,
    val message: String? = null,
)

@Serializable
enum class DriverActionStatus {
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

    private fun recordChat(message: String): DriverEvent {
        require(message.isNotBlank()) { "chat message is required" }
        val event = DriverEvent(
            type = DriverEventType.CHAT,
            client = clientId,
            message = message,
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

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor.playerMove(),
            DriverActionDescriptor.playerChat(),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata.fake()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        return when (invocation.action) {
            "player.chat" -> {
                val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
                val event = recordChat(message)
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.ACCEPTED,
                    message = event.message,
                )
            }

            "player.move" -> DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.ACCEPTED,
                message = "accepted ${invocation.action} for $clientId",
            )

            else -> DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported fake action ${invocation.action}",
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
