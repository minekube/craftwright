package com.minekube.craftless.driver.fabric

import com.minekube.craftless.daemon.HttpDriverSession
import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FabricDriverSelfAttachTest {
    @Test
    fun `attach environment is absent unless both daemon url and client id exist`() {
        assertEquals(null, FabricDriverAttachEnvironment.from(emptyMap()))
        assertEquals(null, FabricDriverAttachEnvironment.from(mapOf("CRAFTLESS_CLIENT_ID" to "alice")))
        assertEquals(null, FabricDriverAttachEnvironment.from(mapOf("CRAFTLESS_DAEMON_URL" to "http://127.0.0.1:8080")))
    }

    @Test
    fun `attach environment trims required values`() {
        val environment =
            FabricDriverAttachEnvironment.from(
                mapOf(
                    "CRAFTLESS_CLIENT_ID" to " alice ",
                    "CRAFTLESS_DAEMON_URL" to " http://127.0.0.1:8080/ ",
                ),
            )

        assertEquals("alice", environment?.clientId)
        assertEquals("http://127.0.0.1:8080", environment?.daemonUrl)
    }

    @Test
    fun `loopback endpoint exposes driver session contract`() {
        val session = RecordingDriverSession("alice")
        FabricDriverLoopbackEndpoint(session).use { endpoint ->
            endpoint.start()

            val remote = HttpDriverSession(clientId = "alice", endpoint = endpoint.url)

            assertEquals(DriverClientSnapshot("alice", ClientState.RUNNING), remote.snapshot())
            assertEquals(DriverClientSnapshot("alice", ClientState.CONNECTED), remote.connect(ConnectionTarget("localhost", 25565)))
            assertEquals(listOf("player.chat"), remote.actions().map { action -> action.id })
            assertEquals("craftless-test-driver", remote.runtimeMetadata().driver)
            assertEquals("alice", remote.runtimeGraph().clientId)

            val result =
                remote.invoke(
                    DriverActionInvocation(
                        action = "player.chat",
                        arguments = mapOf("message" to JsonPrimitive("hello")),
                    ),
                )

            assertEquals(DriverActionStatus.ACCEPTED, result.status)
            assertEquals(listOf("player.chat:hello"), session.invocations)
            assertEquals(listOf(DriverEventType.CLIENT_CREATED), remote.events().map { event -> event.type })
        }
    }

    @Test
    fun `self attach posts loopback endpoint to daemon`() =
        runBlocking {
            val session = RecordingDriverSession("alice")
            SupervisorAttachProbe().use { supervisor ->
                supervisor.start()
                FabricDriverSelfAttach()
                    .start(
                        session = session,
                        environment =
                            FabricDriverAttachEnvironment(
                                clientId = "alice",
                                daemonUrl = supervisor.url,
                            ),
                    ).use {
                        assertEquals(listOf("/clients/alice:attach"), supervisor.paths)
                        val body = supervisor.attachBodies.single()
                        val endpoint =
                            Json
                                .parseToJsonElement(body)
                                .jsonObject
                                .getValue("endpoint")
                                .jsonPrimitive
                                .content
                        assertTrue(endpoint.startsWith("http://127.0.0.1:"))
                        assertEquals(DriverClientSnapshot("alice", ClientState.RUNNING), HttpDriverSession("alice", endpoint).snapshot())
                    }
            }
        }
}

private class RecordingDriverSession(
    override val clientId: String,
) : DriverSession {
    val invocations = mutableListOf<String>()
    private var state = ClientState.RUNNING
    private val events =
        mutableListOf(
            DriverEvent(
                type = DriverEventType.CLIENT_CREATED,
                client = clientId,
                message = "created client $clientId",
            ),
        )

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        state = ClientState.CONNECTED
        return snapshot()
    }

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor(
                id = "player.chat",
                schemaVersion = "1",
                arguments = mapOf("message" to DriverActionArgument("string", required = true)),
                source = DriverActionSource.RUNTIME_PROBE,
            ),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = DriverRuntimeMetadata(driver = "craftless-test-driver")

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "player.chat",
                        arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        val message =
            invocation
                .arguments["message"]
                ?.toString()
                ?.trim('"')
                .orEmpty()
        invocations += "${invocation.action}:$message"
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = message,
        )
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        return snapshot()
    }

    override fun events(): List<DriverEvent> = events.toList()
}

private class SupervisorAttachProbe : AutoCloseable {
    private val port = allocateLoopbackPort()
    private val engine =
        embeddedServer(CIO, host = "127.0.0.1", port = port) {
            routing {
                post("/clients/{id}:attach") {
                    paths += call.request.path()
                    attachBodies += call.receiveText()
                    call.respondText("{}", ContentType.Application.Json)
                }
            }
        }
    val paths = mutableListOf<String>()
    val attachBodies = mutableListOf<String>()
    val url: String = "http://127.0.0.1:$port"

    fun start() {
        engine.start(wait = false)
    }

    override fun close() {
        engine.stop(gracePeriodMillis = 250, timeoutMillis = 1_000)
    }

    private fun allocateLoopbackPort(): Int =
        ServerSocket(0).use { socket ->
            socket.reuseAddress = true
            socket.localPort
        }
}
