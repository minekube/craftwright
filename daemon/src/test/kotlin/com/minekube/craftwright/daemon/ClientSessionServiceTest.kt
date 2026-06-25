package com.minekube.craftwright.daemon

import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverActionDescriptor
import com.minekube.craftwright.driver.api.DriverActionInvocation
import com.minekube.craftwright.driver.api.DriverActionResult
import com.minekube.craftwright.driver.api.DriverActionStatus
import com.minekube.craftwright.driver.api.DriverRuntimeMetadata
import com.minekube.craftwright.driver.api.DriverEventType
import com.minekube.craftwright.driver.runtime.BackendDriverSession
import com.minekube.craftwright.driver.runtime.DriverBackend
import com.minekube.craftwright.driver.runtime.DriverBackendAction
import com.minekube.craftwright.driver.runtime.DriverBackendResult
import com.minekube.craftwright.protocol.ClientState
import com.minekube.craftwright.protocol.CreateClientRequest
import com.minekube.craftwright.protocol.Loader
import com.minekube.craftwright.protocol.OpenApiDocument
import com.minekube.craftwright.protocol.Profile
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientSessionServiceTest {
    @Test
    fun `offline session creates running client with generated api route`() {
        val service = ClientSessionService.inMemory()
        val client = service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        assertEquals("alice", client.id)
        assertEquals(ClientState.RUNNING, client.state)
        assertEquals("Alice", client.profile.name)
        assertEquals("/clients/alice/events", service.routesFor("alice").first { it.path.endsWith("/events") }.path)
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/openapi.json" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/player:chat" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/player:move" })
        assertTrue(service.routesFor("alice").none { it.path == "/clients/alice/player/sendChat" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/connection/connect" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/player/position" })
    }

    @Test
    fun `session service lists clients in creation order`() {
        val service = ClientSessionService.inMemory()

        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )
        service.createClient(
            CreateClientRequest(
                id = "bob",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Bob"),
            )
        )

        assertEquals(listOf("alice", "bob"), service.listClients().map { it.id })
    }

    @Test
    fun `client specific openapi exposes action metadata without static action routes`() {
        val service = ClientSessionService.inMemory()
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        val document: OpenApiDocument = service.openApiFor("alice")

        assertEquals("alice", document.extensions["x-craftwright-client-id"])
        assertEquals("1.21.4", document.extensions["x-craftwright-minecraft-version"])
        assertEquals("FABRIC", document.extensions["x-craftwright-loader"])
        assertEquals("none", document.extensions["x-craftwright-loader-version"])
        assertEquals("craftwright-fake", document.extensions["x-craftwright-driver"])
        assertEquals("0.1.0-SNAPSHOT", document.extensions["x-craftwright-driver-version"])
        assertEquals("none", document.extensions["x-craftwright-mappings"])
        assertEquals("none", document.extensions["x-craftwright-installed-mods-fingerprint"])
        assertEquals("none", document.extensions["x-craftwright-registry-fingerprint"])
        assertEquals("none", document.extensions["x-craftwright-server-feature-fingerprint"])
        assertEquals("local-fake", document.extensions["x-craftwright-permissions-fingerprint"])
        assertEquals(
            "minecraft=1.21.4;loader=FABRIC;loaderVersion=none;driver=craftwright-fake;driverVersion=0.1.0-SNAPSHOT;mappings=none;mods=none;registries=none;serverFeatures=none;permissions=local-fake;actions=player.move:1,player.chat:1",
            document.extensions["x-craftwright-runtime-fingerprint"],
        )
        assertTrue(document.paths.containsKey("/clients/alice/openapi.json"))
        assertTrue(document.paths.containsKey("/clients/alice"))
        assertTrue(document.paths.containsKey("/clients/alice/actions"))
        assertTrue(document.paths.containsKey("/clients/alice:run"))
        assertTrue(document.paths.containsKey("/clients/alice/player:chat"))
        assertTrue(document.paths.containsKey("/clients/alice/player:move"))
        val clientSchema = document.paths["/clients/alice"]?.get
            ?.responses
            ?.get("200")
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(clientSchema)
        assertEquals(listOf("id", "instance", "profile", "state"), clientSchema.required)
        assertEquals("runPlayerChat", document.paths["/clients/alice/player:chat"]?.post?.operationId)
        val chatSchema = document.paths["/clients/alice/player:chat"]?.post?.requestBody
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(chatSchema)
        assertEquals("object", chatSchema.type)
        assertEquals(listOf("message"), chatSchema.required)
        assertEquals("string", chatSchema.properties["message"]?.type)
        val moveSchema = document.paths["/clients/alice/player:move"]?.post?.requestBody
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(moveSchema)
        assertEquals("boolean", moveSchema.properties["forward"]?.type)
        assertEquals("integer", moveSchema.properties["ticks"]?.type)
        assertFalse(document.paths.keys.any { it.endsWith("/actions/move") })
        assertFalse(document.paths.keys.any { "/perception/" in it })
        val actionOperation = document.paths["/clients/alice:run"]?.post
        assertNotNull(actionOperation)
        assertEquals("action", actionOperation.extensions["x-craftwright-source"])
        val actionResponseSchema = actionOperation.responses["200"]
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(actionResponseSchema)
        assertEquals(listOf("action", "status"), actionResponseSchema.required)
        assertEquals("string", actionResponseSchema.properties["action"]?.type)
        assertEquals("string", actionResponseSchema.properties["status"]?.type)
        val chatResponseSchema = document.paths["/clients/alice/player:chat"]?.post
            ?.responses
            ?.get("200")
            ?.content
            ?.get("application/json")
            ?.schema
        assertNotNull(chatResponseSchema)
        assertEquals(listOf("action", "status"), chatResponseSchema.required)
        assertEquals("string", chatResponseSchema.properties["message"]?.type)
        assertEquals("1", document.actions.single { it.id == "player.move" }.schemaVersion)
        assertEquals("1", document.actions.single { it.id == "player.chat" }.schemaVersion)
    }

    @Test
    fun `minecraft usernames longer than sixteen characters are rejected`() {
        val service = ClientSessionService.inMemory()

        val result = runCatching {
            service.createClient(
                CreateClientRequest(
                    id = "too-long",
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("CraftwrightApiBotTooLong"),
                )
            )
        }

        assertTrue(result.isFailure)
        assertEquals("offline profile name must be 16 characters or fewer", result.exceptionOrNull()?.message)
    }

    @Test
    fun `created clients expose a driver session contract`() {
        val service = ClientSessionService.inMemory()
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        val driver = service.driverFor("alice")
        assertEquals(ClientState.RUNNING, driver.snapshot().state)
        assertEquals(ClientState.CONNECTED, driver.connect(ConnectionTarget("localhost", 25565)).state)
        val chat = driver.invoke(
            DriverActionInvocation(
                action = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("from driver")),
            )
        )
        assertEquals(DriverActionStatus.ACCEPTED, chat.status)
        assertEquals("Alice", driver.player().name)
        assertTrue(driver.events().any { it.type == DriverEventType.CHAT })
    }

    @Test
    fun `session service can create clients with an injected runtime driver factory`() {
        val backend = RecordingDriverBackend()
        val service = ClientSessionService.inMemory { request ->
            BackendDriverSession(
                clientId = request.id,
                profileName = request.profile.name,
                backend = backend,
            )
        }

        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        service.connectClient("alice", ConnectionTarget("localhost", 25565))
        service.driverFor("alice").invoke(
            DriverActionInvocation(
                action = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("runtime route")),
            )
        )

        assertEquals(
            listOf("connect alice localhost:25565", "chat alice runtime route"),
            backend.calls,
        )
    }

    @Test
    fun `client specific openapi uses runtime metadata from injected driver`() {
        val backend = RecordingDriverBackend(
            metadata = DriverRuntimeMetadata(
                loaderVersion = "0.16.14",
                driver = "craftwright-driver-fabric",
                driverVersion = "0.2.0-test",
                mappings = "yarn-test",
                installedModsFingerprint = "mods-test",
                registryFingerprint = "registries-test",
                serverFeatureFingerprint = "server-features-test",
                permissionsFingerprint = "permissions-test",
            )
        )
        val service = ClientSessionService.inMemory { request ->
            BackendDriverSession(
                clientId = request.id,
                profileName = request.profile.name,
                backend = backend,
            )
        }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            )
        )

        val extensions = service.openApiFor("alice").extensions

        assertEquals("0.16.14", extensions["x-craftwright-loader-version"])
        assertEquals("craftwright-driver-fabric", extensions["x-craftwright-driver"])
        assertEquals("0.2.0-test", extensions["x-craftwright-driver-version"])
        assertEquals("yarn-test", extensions["x-craftwright-mappings"])
        assertEquals("mods-test", extensions["x-craftwright-installed-mods-fingerprint"])
        assertEquals("registries-test", extensions["x-craftwright-registry-fingerprint"])
        assertEquals("server-features-test", extensions["x-craftwright-server-feature-fingerprint"])
        assertEquals("permissions-test", extensions["x-craftwright-permissions-fingerprint"])
        assertEquals(
            "minecraft=1.21.4;loader=FABRIC;loaderVersion=0.16.14;driver=craftwright-driver-fabric;driverVersion=0.2.0-test;mappings=yarn-test;mods=mods-test;registries=registries-test;serverFeatures=server-features-test;permissions=permissions-test;actions=player.move:1,player.chat:1",
            extensions["x-craftwright-runtime-fingerprint"],
        )
    }
}

private class RecordingDriverBackend(
    private val metadata: DriverRuntimeMetadata = DriverRuntimeMetadata.fake(),
) : DriverBackend {
    val calls = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        calls += "connect $clientId ${target.host}:${target.port}"
        return DriverBackendResult(DriverBackendAction.CONNECT)
    }

    override fun stop(clientId: String): DriverBackendResult {
        calls += "stop $clientId"
        return DriverBackendResult(DriverBackendAction.STOP)
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor.playerMove(),
            DriverActionDescriptor.playerChat(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        metadata

    override fun invoke(clientId: String, invocation: DriverActionInvocation): DriverActionResult {
        val message = invocation.arguments["message"]?.jsonPrimitive?.content
        calls += "chat $clientId $message"
        return DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED, message)
    }
}
