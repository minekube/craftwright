package com.minekube.craftless.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class LiveEvent(
    val id: String,
    val type: String,
    val clientId: String? = null,
    val resourceId: String? = null,
    val operationId: String? = null,
    val correlationId: String? = null,
    val payload: JsonObject = buildJsonObject {},
    val timestamp: String,
) {
    init {
        require(id.isCraftlessLiveId()) { "invalid live event id $id" }
        require(type.isCraftlessActionId()) { "invalid live event type $type" }
        require(clientId == null || clientId.isCraftlessClientId()) { "invalid live event client id $clientId" }
        require(resourceId == null || resourceId.isCraftlessResourceId()) { "invalid live event resource id $resourceId" }
        require(operationId == null || operationId.isCraftlessActionId()) { "invalid live event operation id $operationId" }
        require(correlationId == null || correlationId.isCraftlessLiveId()) {
            "invalid live event correlation id $correlationId"
        }
        require(timestamp.isNotBlank()) { "live event timestamp is required" }
    }
}

@Serializable
data class LiveEventFilter(
    val types: List<String> = emptyList(),
    val clientId: String? = null,
    val resourceId: String? = null,
    val operationId: String? = null,
    val correlationId: String? = null,
) {
    init {
        types.forEach { type -> require(type.isCraftlessActionId()) { "invalid live event filter type $type" } }
        require(clientId == null || clientId.isCraftlessClientId()) { "invalid live event filter client id $clientId" }
        require(resourceId == null || resourceId.isCraftlessResourceId()) { "invalid live event filter resource id $resourceId" }
        require(operationId == null || operationId.isCraftlessActionId()) {
            "invalid live event filter operation id $operationId"
        }
        require(correlationId == null || correlationId.isCraftlessLiveId()) {
            "invalid live event filter correlation id $correlationId"
        }
    }

    fun matches(
        type: String,
        clientId: String? = null,
        resourceId: String? = null,
        operationId: String? = null,
        correlationId: String? = null,
    ): Boolean =
        (types.isEmpty() || type in types) &&
            (this.clientId == null || this.clientId == clientId) &&
            (this.resourceId == null || this.resourceId == resourceId) &&
            (this.operationId == null || this.operationId == operationId) &&
            (this.correlationId == null || this.correlationId == correlationId)
}

object JsonRpcMethod {
    const val INVOKE = "invoke"
    const val SUBSCRIBE = "subscribe"
    const val UNSUBSCRIBE = "unsubscribe"
    const val QUERY = "query"

    val allowed: Set<String> = setOf(INVOKE, SUBSCRIBE, UNSUBSCRIBE, QUERY)
}

@Serializable
data class JsonRpcRequest(
    val id: String? = null,
    val method: String,
    val params: JsonObject = buildJsonObject {},
    @EncodeDefault
    val jsonrpc: String = JSON_RPC_VERSION,
) {
    init {
        require(jsonrpc == JSON_RPC_VERSION) { "jsonrpc version must be $JSON_RPC_VERSION" }
        require(id == null || id.isCraftlessLiveId()) { "invalid json rpc id $id" }
        require(method in JsonRpcMethod.allowed) { "unsupported json rpc method $method" }
    }
}

@Serializable
data class JsonRpcResponse(
    val id: String? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    @EncodeDefault
    val jsonrpc: String = JSON_RPC_VERSION,
) {
    init {
        require(jsonrpc == JSON_RPC_VERSION) { "jsonrpc version must be $JSON_RPC_VERSION" }
        require(id == null || id.isCraftlessLiveId()) { "invalid json rpc id $id" }
        require((result == null) != (error == null)) { "json rpc response requires exactly one of result or error" }
    }

    companion object {
        fun result(
            id: String?,
            result: JsonElement,
        ): JsonRpcResponse = JsonRpcResponse(id = id, result = result)

        fun error(
            id: String?,
            code: String,
            message: String,
            data: JsonElement? = null,
        ): JsonRpcResponse = JsonRpcResponse(id = id, error = JsonRpcError(code, message, data))
    }
}

@Serializable
data class JsonRpcError(
    val code: String,
    val message: String,
    val data: JsonElement? = null,
) {
    init {
        require(code.isCraftlessActionArgumentName()) { "invalid json rpc error code $code" }
        require(message.isNotBlank()) { "json rpc error message is required" }
    }
}

private const val JSON_RPC_VERSION = "2.0"

private fun String.isCraftlessLiveId(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
