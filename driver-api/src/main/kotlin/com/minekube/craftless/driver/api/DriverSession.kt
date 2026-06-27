package com.minekube.craftless.driver.api

import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.isCraftlessActionArgumentName
import com.minekube.craftless.protocol.isCraftlessActionArgumentType
import com.minekube.craftless.protocol.isCraftlessActionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

interface DriverSession {
    val clientId: String

    fun snapshot(): DriverClientSnapshot

    fun connect(target: ConnectionTarget): DriverClientSnapshot

    fun actions(): List<DriverActionDescriptor>

    fun runtimeMetadata(): DriverRuntimeMetadata

    fun runtimeGraph(): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    fun operationAdapters(): DriverOperationAdapters = DriverOperationAdapters.empty()

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
    val result: DriverActionResultDescriptor = DriverActionResultDescriptor(),
    val source: DriverActionSource = DriverActionSource.BINDING,
    val availability: DriverActionAvailability = DriverActionAvailability.AVAILABLE,
    val availabilityReason: String? = null,
) {
    init {
        require(id.isNotBlank()) { "action id is required" }
        require(id.isCraftlessActionId()) { "invalid action id $id" }
        require(schemaVersion.isNotBlank()) { "action schema version is required" }
        require(availabilityReason == null || availabilityReason.isCraftlessActionArgumentName()) {
            "availability reason must be a machine-readable Craftless code"
        }
        if (availability == DriverActionAvailability.UNAVAILABLE) {
            require(!availabilityReason.isNullOrBlank()) { "unavailable action $id requires availability reason" }
        } else {
            require(availabilityReason == null) { "available action $id must not declare availability reason" }
        }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument name $name" }
        }
    }
}

@Serializable
enum class DriverActionSource {
    @SerialName("binding")
    BINDING,

    @SerialName("runtime-probe")
    RUNTIME_PROBE,
}

@Serializable
enum class DriverActionAvailability {
    @SerialName("available")
    AVAILABLE,

    @SerialName("unavailable")
    UNAVAILABLE,
}

@Serializable
data class DriverActionResultDescriptor(
    val properties: Map<String, DriverActionResultProperty> = defaultDriverActionResultProperties(),
    val required: List<String> = listOf("action", "status"),
) {
    init {
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action result property name $name" }
        }
        required.forEach { name ->
            require(properties.containsKey(name)) { "required action result property $name is not declared" }
        }
    }
}

@Serializable
data class DriverActionResultProperty(
    val type: String,
    val properties: Map<String, DriverActionResultProperty> = emptyMap(),
    val items: DriverActionResultProperty? = null,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action result property type $type" }
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action result property schema name $name" }
        }
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
        require(driver.startsWith("craftless-")) { "driver must be a Craftless-owned public name" }
        require(driverVersion.isNotBlank()) { "driver version is required" }
        require(mappings.isNotBlank()) { "mappings fingerprint is required" }
        require(installedModsFingerprint.isNotBlank()) { "installed mods fingerprint is required" }
        require(registryFingerprint.isNotBlank()) { "registry fingerprint is required" }
        require(serverFeatureFingerprint.isNotBlank()) { "server feature fingerprint is required" }
        require(permissionsFingerprint.isNotBlank()) { "permissions fingerprint is required" }
    }

    companion object {
        fun runtimeAdapter(): DriverRuntimeMetadata = DriverRuntimeMetadata(driver = "craftless-driver-runtime")
    }
}

@Serializable
data class DriverActionArgument(
    val type: String,
    val required: Boolean = false,
    val properties: Map<String, DriverActionArgument> = emptyMap(),
    val items: DriverActionArgument? = null,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action argument type $type" }
        properties.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument schema property $name" }
        }
    }
}

@Serializable
data class DriverActionInvocation(
    val action: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(action.isCraftlessActionId()) { "invalid action id $action" }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid action argument name $name" }
        }
    }
}

fun Map<String, JsonElement>.booleanArgument(name: String): Boolean =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
    } == true

fun Map<String, JsonElement>.intArgument(name: String): Int? =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.intOrNull ?: primitive.content.toIntOrNull()
    }

fun Map<String, JsonElement>.stringArgument(name: String): String? = this[name]?.jsonPrimitive?.content

fun requireChatMessage(message: String): String {
    require(message.isNotBlank()) { "chat message is required" }
    require(!message.startsWith("/")) { "minecraft command strings are not valid chat action input" }
    return message
}

@Serializable
data class DriverActionResult(
    val action: String,
    val status: DriverActionStatus,
    val message: String? = null,
    val eventType: DriverEventType? = null,
    val data: JsonObject = buildJsonObject {},
) {
    init {
        require(action.isCraftlessActionId()) { "invalid action id $action" }
    }
}

@Serializable
enum class DriverActionStatus {
    ACCEPTED,
    UNSUPPORTED,
    FAILED,
}

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
    MOVEMENT,
    CLIENT_STOPPED,
    ERROR,
}

private fun defaultDriverActionResultProperties(): Map<String, DriverActionResultProperty> =
    mapOf(
        "action" to DriverActionResultProperty("string"),
        "status" to DriverActionResultProperty("string"),
        "message" to DriverActionResultProperty("string"),
        "data" to DriverActionResultProperty("object"),
    )
