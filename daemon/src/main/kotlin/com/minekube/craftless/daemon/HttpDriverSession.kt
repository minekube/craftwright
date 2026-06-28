package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HttpDriverSession(
    override val clientId: String,
    endpoint: String,
    private val http: HttpClient = HttpClient(CIO),
) : DriverSession {
    private val endpoint = endpoint.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(this.endpoint.isNotBlank()) { "driver attach endpoint is required" }
    }

    override fun snapshot(): DriverClientSnapshot = get("snapshot")

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = post("connect", target)

    override fun runtimeMetadata(): DriverRuntimeMetadata = get("runtime-metadata")

    override fun runtimeGraph(): RuntimeCapabilityGraph = get("runtime-graph")

    override fun operationAdapters(): DriverOperationAdapters = DriverOperationAdapters.empty()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult = post("invoke", invocation)

    override fun stop(): DriverClientSnapshot = post("stop", UnitPayload)

    override fun events(): List<DriverEvent> = get("events")

    private inline fun <reified T> get(path: String): T =
        runBlocking {
            json.decodeFromString<T>(http.get(url(path)).bodyAsText())
        }

    private inline fun <reified T, reified R> post(
        path: String,
        value: T,
    ): R =
        runBlocking {
            json.decodeFromString<R>(
                http
                    .post(url(path)) {
                        contentType(ContentType.Application.Json)
                        setBody(json.encodeToString(value))
                    }.bodyAsText(),
            )
        }

    private fun url(path: String): String = "$endpoint/$path"
}

@kotlinx.serialization.Serializable
private object UnitPayload
