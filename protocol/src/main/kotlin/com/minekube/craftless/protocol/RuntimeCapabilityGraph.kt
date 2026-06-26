package com.minekube.craftless.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class RuntimeCapabilityGraph(
    val clientId: String,
    val resources: List<RuntimeResourceNode> = emptyList(),
    val operations: List<RuntimeOperationNode> = emptyList(),
    val handles: List<RuntimeHandleNode> = emptyList(),
    val events: List<RuntimeEventNode> = emptyList(),
) {
    init {
        require(clientId.isCraftlessClientId()) { "invalid runtime graph client id $clientId" }
        requireUnique("resource", resources.map { it.id })
        requireUnique("operation", operations.map { it.id })
        requireUnique("handle", handles.map { it.id })
        requireUnique("event", events.map { it.id })

        val resourceIds = resources.map { it.id }.toSet()
        operations.forEach { operation ->
            require(operation.resource in resourceIds) {
                "operation ${operation.id} references unknown resource ${operation.resource}"
            }
        }
        handles.forEach { handle ->
            require(handle.resource in resourceIds) {
                "handle ${handle.id} references unknown resource ${handle.resource}"
            }
        }
        events.forEach { event ->
            require(event.resource in resourceIds) {
                "event ${event.id} references unknown resource ${event.resource}"
            }
        }
    }

    fun fingerprint(): String {
        val canonical =
            buildString {
                appendLine("client=$clientId")
                resources.sortedBy { it.id }.forEach { appendLine("resource=${it.canonical()}") }
                operations.sortedBy { it.id }.forEach { appendLine("operation=${it.canonical()}") }
                handles.sortedBy { it.id }.forEach { appendLine("handle=${it.canonical()}") }
                events.sortedBy { it.id }.forEach { appendLine("event=${it.canonical()}") }
            }
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(canonical.encodeToByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(FINGERPRINT_LENGTH)
        return "graph:$digest"
    }
}

@Serializable
data class RuntimeResourceNode(
    val id: String,
    val availability: RuntimeAvailability,
    val schema: RuntimeSchema = RuntimeSchema.objectSchema(),
    val sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
) {
    init {
        require(id.isCraftlessResourceId()) { "invalid runtime resource id $id" }
    }

    fun canonical(): String = "$id|${availability.canonical()}|${schema.canonical()}|${sourceEvidence.canonical()}"
}

@Serializable
data class RuntimeOperationNode(
    val id: String,
    val resource: String,
    val adapter: String,
    val arguments: Map<String, RuntimeSchema> = emptyMap(),
    val result: RuntimeSchema = RuntimeSchema.objectSchema(),
    val availability: RuntimeAvailability,
    val sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid runtime operation id $id" }
        require(resource.isCraftlessResourceId()) { "invalid runtime operation resource $resource" }
        require(id.startsWith("$resource.")) { "operation $id must belong to resource $resource" }
        require(adapter.isCraftlessAdapterKey()) { "invalid runtime operation adapter $adapter" }
        arguments.keys.forEach { name ->
            require(name.isCraftlessActionArgumentName()) { "invalid runtime operation argument $name" }
        }
    }

    fun canonical(): String =
        "$id|$resource|$adapter|${availability.canonical()}|" +
            arguments.toSortedMap().map { (name, schema) -> "$name=${schema.canonical()}" }.joinToString(",") +
            "|${result.canonical()}|${sourceEvidence.canonical()}"
}

@Serializable
data class RuntimeHandleNode(
    val id: String,
    val resource: String,
    val schema: RuntimeSchema,
    val availability: RuntimeAvailability,
    val sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid runtime handle id $id" }
        require(resource.isCraftlessResourceId()) { "invalid runtime handle resource $resource" }
        require(id.startsWith("$resource.")) { "handle $id must belong to resource $resource" }
    }

    fun canonical(): String = "$id|$resource|${availability.canonical()}|${schema.canonical()}|${sourceEvidence.canonical()}"
}

@Serializable
data class RuntimeEventNode(
    val id: String,
    val resource: String,
    val payload: RuntimeSchema,
    val availability: RuntimeAvailability,
    val sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid runtime event id $id" }
        require(resource.isCraftlessResourceId()) { "invalid runtime event resource $resource" }
        require(id.startsWith("$resource.")) { "event $id must belong to resource $resource" }
    }

    fun canonical(): String = "$id|$resource|${availability.canonical()}|${payload.canonical()}|${sourceEvidence.canonical()}"
}

@Serializable
data class RuntimeSourceEvidence(
    val kind: String,
    val fingerprint: String,
) {
    init {
        require(kind.isCraftlessActionArgumentName()) {
            "runtime source evidence kind must be a machine-readable Craftless code"
        }
        require(fingerprint.isNotBlank()) { "runtime source evidence fingerprint is required" }
    }

    fun canonical(): String = "$kind=$fingerprint"
}

@Serializable
data class RuntimeAvailability(
    val state: RuntimeAvailabilityState,
    val reason: String? = null,
) {
    init {
        require(reason == null || reason.isCraftlessActionArgumentName()) {
            "runtime availability reason must be a machine-readable Craftless code"
        }
        if (state == RuntimeAvailabilityState.UNAVAILABLE) {
            require(!reason.isNullOrBlank()) { "unavailable runtime node requires availability reason" }
        } else {
            require(reason == null) { "available runtime node must not declare availability reason" }
        }
    }

    fun canonical(): String = "${state.name.lowercase()}:${reason.orEmpty()}"

    companion object {
        fun available(): RuntimeAvailability = RuntimeAvailability(RuntimeAvailabilityState.AVAILABLE)

        fun unavailable(reason: String): RuntimeAvailability = RuntimeAvailability(RuntimeAvailabilityState.UNAVAILABLE, reason)
    }
}

@Serializable
enum class RuntimeAvailabilityState {
    @SerialName("available")
    AVAILABLE,

    @SerialName("unavailable")
    UNAVAILABLE,
}

@Serializable
data class RuntimeSchema(
    val type: String,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported runtime schema type $type" }
    }

    fun canonical(): String = type

    companion object {
        fun objectSchema(): RuntimeSchema = RuntimeSchema("object")
    }
}

private fun requireUnique(
    kind: String,
    ids: List<String>,
) {
    val duplicate = ids.groupBy { it }.entries.firstOrNull { (_, matches) -> matches.size > 1 }
    require(duplicate == null) { "duplicate runtime $kind id ${duplicate?.key}" }
}

private fun String.isCraftlessAdapterKey(): Boolean = matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*"))

private fun List<RuntimeSourceEvidence>.canonical(): String =
    sortedWith(compareBy(RuntimeSourceEvidence::kind, RuntimeSourceEvidence::fingerprint))
        .joinToString(",") { it.canonical() }

private const val FINGERPRINT_LENGTH = 16
