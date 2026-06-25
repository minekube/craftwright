package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionResultDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.runtime.BackendDriverSession
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiResponse
import com.minekube.craftless.protocol.Profile
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientSessionServiceTest {
    @Test
    fun `offline session creates running client with generated api route`() {
        val service = ClientSessionService.inMemory()
        val client =
            service.createClient(
                CreateClientRequest(
                    id = "alice",
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("Alice"),
                ),
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
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice:connect" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice:stop" })
        assertTrue(service.routesFor("alice").none { it.path == "/clients/alice/connection/connect" })
        assertTrue(service.routesFor("alice").none { it.path == "/clients/alice/stop" })
        assertTrue(service.routesFor("alice").none { it.path == "/clients/alice/player" })
        assertTrue(service.routesFor("alice").none { it.path == "/clients/alice/player/position" })
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
            ),
        )
        service.createClient(
            CreateClientRequest(
                id = "bob",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Bob"),
            ),
        )

        assertEquals(listOf("alice", "bob"), service.listClients().map { it.id })
    }

    @Test
    fun `session service rejects client ids that cannot be used as route segments`() {
        val service = ClientSessionService.inMemory()

        listOf(
            "",
            "alice/bob",
            "alice:run",
            "alice bob",
            ".alice",
            "alice.",
        ).forEach { clientId ->
            assertFailsWith<IllegalArgumentException> {
                service.createClient(
                    CreateClientRequest(
                        id = clientId,
                        version = "1.21.4",
                        loader = Loader.FABRIC,
                        profile = Profile.offline("Alice"),
                    ),
                )
            }
        }
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
            ),
        )

        val document: OpenApiDocument = service.openApiFor("alice")

        assertEquals("alice", document.extensions["x-craftless-client-id"])
        assertEquals("1.21.4", document.extensions["x-craftless-minecraft-version"])
        assertEquals("FABRIC", document.extensions["x-craftless-loader"])
        assertEquals("none", document.extensions["x-craftless-loader-version"])
        assertEquals("craftless-fake", document.extensions["x-craftless-driver"])
        assertEquals("0.1.0-SNAPSHOT", document.extensions["x-craftless-driver-version"])
        assertEquals("none", document.extensions["x-craftless-mappings-fingerprint"])
        assertFalse(document.extensions.containsKey("x-craftless-mappings"))
        assertEquals("none", document.extensions["x-craftless-installed-mods-fingerprint"])
        assertEquals("none", document.extensions["x-craftless-registry-fingerprint"])
        assertEquals("none", document.extensions["x-craftless-server-feature-fingerprint"])
        assertEquals("local-fake", document.extensions["x-craftless-permissions-fingerprint"])
        assertEquals(
            "minecraft=1.21.4;loader=FABRIC;loaderVersion=none;driver=craftless-fake;driverVersion=0.1.0-SNAPSHOT;mappings=none;mods=none;registries=none;serverFeatures=none;permissions=local-fake;actions=player.chat:1(message:string!)->(action:string!,message:string,status:string!),player.move:1(backward:boolean,forward:boolean,jump:boolean,left:boolean,right:boolean,sneak:boolean,sprint:boolean,ticks:integer)->(action:string!,message:string,status:string!)",
            document.extensions["x-craftless-runtime-fingerprint"],
        )
        assertTrue(document.paths.containsKey("/clients/alice/openapi.json"))
        assertTrue(document.paths.containsKey("/clients/alice"))
        assertTrue(document.paths.containsKey("/clients/alice:connect"))
        assertTrue(document.paths.containsKey("/clients/alice:stop"))
        assertTrue(document.paths.containsKey("/clients/alice/actions"))
        assertTrue(document.paths.containsKey("/clients/alice:run"))
        assertTrue(document.paths.containsKey("/clients/alice/player:chat"))
        assertTrue(document.paths.containsKey("/clients/alice/player:move"))
        assertEquals("getClient", document.paths["/clients/alice"]?.get?.operationId)
        assertEquals("getClientOpenapiJson", document.paths["/clients/alice/openapi.json"]?.get?.operationId)
        assertEquals("clientConnect", document.paths["/clients/alice:connect"]?.post?.operationId)
        assertEquals("stopClient", document.paths["/clients/alice:stop"]?.post?.operationId)
        assertEquals("listClientActions", document.paths["/clients/alice/actions"]?.get?.operationId)
        assertEquals("runClientAction", document.paths["/clients/alice:run"]?.post?.operationId)
        assertEquals("getClientEvents", document.paths["/clients/alice/events"]?.get?.operationId)
        assertFalse(document.paths.containsKey("/clients/alice/connection/connect"))
        assertFalse(document.paths.containsKey("/clients/alice/stop"))
        assertFalse(document.paths.containsKey("/clients/alice/player"))
        assertFalse(document.paths.containsKey("/clients/alice/player/position"))
        val clientSchema =
            document.paths["/clients/alice"]
                ?.get
                ?.responses
                ?.get("200")
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(clientSchema)
        assertEquals(listOf("id", "instance", "profile", "state"), clientSchema.required)
        assertEquals("runPlayerChat", document.paths["/clients/alice/player:chat"]?.post?.operationId)
        val chatSchema =
            document.paths["/clients/alice/player:chat"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(chatSchema)
        assertEquals("object", chatSchema.type)
        assertEquals(false, chatSchema.additionalProperties)
        assertEquals(listOf("message"), chatSchema.required)
        assertEquals("string", chatSchema.properties["message"]?.type)
        val moveSchema =
            document.paths["/clients/alice/player:move"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(moveSchema)
        assertEquals(false, moveSchema.additionalProperties)
        assertEquals("boolean", moveSchema.properties["forward"]?.type)
        assertEquals("integer", moveSchema.properties["ticks"]?.type)
        assertFalse(document.paths.keys.any { it.endsWith("/actions/move") })
        assertFalse(document.paths.keys.any { "/perception/" in it })
        val actionOperation = document.paths["/clients/alice:run"]?.post
        assertNotNull(actionOperation)
        assertEquals("action", actionOperation.extensions["x-craftless-source"])
        val actionResponseSchema =
            actionOperation.responses["200"]
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(actionResponseSchema)
        assertEquals(listOf("action", "status"), actionResponseSchema.required)
        assertEquals("string", actionResponseSchema.properties["action"]?.type)
        assertEquals("string", actionResponseSchema.properties["status"]?.type)
        val chatResponseSchema =
            document.paths["/clients/alice/player:chat"]
                ?.post
                ?.responses
                ?.get("200")
                ?.content
                ?.get("application/json")
                ?.schema
        assertNotNull(chatResponseSchema)
        assertEquals(listOf("action", "status"), chatResponseSchema.required)
        assertEquals("string", chatResponseSchema.properties["message"]?.type)
        val chatActionMetadata = document.actions.single { it.id == "player.chat" }
        assertEquals(listOf("action", "status"), chatActionMetadata.result.required)
        assertEquals("string", chatActionMetadata.result.properties["message"]?.type)
        assertErrorSchema(
            requireNotNull(
                document.paths["/clients/alice/player:chat"]
                    ?.post
                    ?.responses
                    ?.get("400"),
            ),
        )
        assertErrorSchema(
            requireNotNull(
                document.paths["/clients/alice/player:chat"]
                    ?.post
                    ?.responses
                    ?.get("404"),
            ),
        )
        assertErrorSchema(
            requireNotNull(
                document.paths["/clients/alice/player:chat"]
                    ?.post
                    ?.responses
                    ?.get("409"),
            ),
        )
        assertEquals("1", document.actions.single { it.id == "player.move" }.schemaVersion)
        assertEquals("1", document.actions.single { it.id == "player.chat" }.schemaVersion)
    }

    @Test
    fun `minecraft usernames longer than sixteen characters are rejected`() {
        val service = ClientSessionService.inMemory()

        val result =
            runCatching {
                service.createClient(
                    CreateClientRequest(
                        id = "too-long",
                        version = "1.21.4",
                        loader = Loader.FABRIC,
                        profile = Profile.offline("CraftlessApiBotTooLong"),
                    ),
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
            ),
        )

        val driver = service.driverFor("alice")
        assertEquals(ClientState.RUNNING, driver.snapshot().state)
        assertEquals(ClientState.CONNECTED, driver.connect(ConnectionTarget("localhost", 25565)).state)
        val chat =
            driver.invoke(
                DriverActionInvocation(
                    action = "player.chat",
                    arguments = mapOf("message" to JsonPrimitive("from driver")),
                ),
            )
        assertEquals(DriverActionStatus.ACCEPTED, chat.status)
        assertTrue(driver.events().any { it.type == DriverEventType.CHAT })
    }

    @Test
    fun `session service can create clients with an injected runtime driver factory`() {
        val backend = RecordingDriverBackend()
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend = backend,
                )
            }

        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        service.connectClient("alice", ConnectionTarget("localhost", 25565))
        service.driverFor("alice").invoke(
            DriverActionInvocation(
                action = "player.chat",
                arguments = mapOf("message" to JsonPrimitive("runtime route")),
            ),
        )

        assertEquals(
            listOf("connect alice localhost:25565", "chat alice runtime route"),
            backend.calls,
        )
    }

    @Test
    fun `client specific openapi uses runtime metadata from injected driver`() {
        val backend =
            RecordingDriverBackend(
                metadata =
                    DriverRuntimeMetadata(
                        loaderVersion = "0.16.14",
                        driver = "craftless-driver-fabric",
                        driverVersion = "0.2.0-test",
                        mappings = "mappings-fingerprint-test",
                        installedModsFingerprint = "mods-test",
                        registryFingerprint = "registries-test",
                        serverFeatureFingerprint = "server-features-test",
                        permissionsFingerprint = "permissions-test",
                    ),
            )
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend = backend,
                )
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val extensions = service.openApiFor("alice").extensions

        assertEquals("0.16.14", extensions["x-craftless-loader-version"])
        assertEquals("craftless-driver-fabric", extensions["x-craftless-driver"])
        assertEquals("0.2.0-test", extensions["x-craftless-driver-version"])
        assertEquals("mappings-fingerprint-test", extensions["x-craftless-mappings-fingerprint"])
        assertFalse(extensions.containsKey("x-craftless-mappings"))
        assertEquals("mods-test", extensions["x-craftless-installed-mods-fingerprint"])
        assertEquals("registries-test", extensions["x-craftless-registry-fingerprint"])
        assertEquals("server-features-test", extensions["x-craftless-server-feature-fingerprint"])
        assertEquals("permissions-test", extensions["x-craftless-permissions-fingerprint"])
        assertEquals(
            "minecraft=1.21.4;loader=FABRIC;loaderVersion=0.16.14;driver=craftless-driver-fabric;driverVersion=0.2.0-test;mappings=mappings-fingerprint-test;mods=mods-test;registries=registries-test;serverFeatures=server-features-test;permissions=permissions-test;actions=player.chat:1(message:string!)->(action:string!,message:string,status:string!),player.move:1(backward:boolean,forward:boolean,jump:boolean,left:boolean,right:boolean,sneak:boolean,sprint:boolean,ticks:integer)->(action:string!,message:string,status:string!)",
            extensions["x-craftless-runtime-fingerprint"],
        )
    }

    @Test
    fun `client specific openapi action metadata is deterministic by action id`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        RecordingDriverBackend(
                            actions =
                                listOf(
                                    testPlayerMoveActionDescriptor(),
                                    testPlayerChatActionDescriptor(),
                                ),
                        ),
                )
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val document = service.openApiFor("alice")

        assertEquals(listOf("player.chat", "player.move"), document.actions.map { it.id })
        assertEquals(
            "minecraft=1.21.4;loader=FABRIC;loaderVersion=none;driver=craftless-fake;driverVersion=0.1.0-SNAPSHOT;mappings=none;mods=none;registries=none;serverFeatures=none;permissions=local-fake;actions=player.chat:1(message:string!)->(action:string!,message:string,status:string!),player.move:1(backward:boolean,forward:boolean,jump:boolean,left:boolean,right:boolean,sneak:boolean,sprint:boolean,ticks:integer)->(action:string!,message:string,status:string!)",
            document.extensions["x-craftless-runtime-fingerprint"],
        )
        assertEquals(
            "player.chat:1(message:string!)->(action:string!,message:string,status:string!),player.move:1(backward:boolean,forward:boolean,jump:boolean,left:boolean,right:boolean,sneak:boolean,sprint:boolean,ticks:integer)->(action:string!,message:string,status:string!)",
            document.extensions["x-craftless-action-fingerprint"],
        )
    }

    @Test
    fun `client specific openapi rejects duplicate action ids`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        RecordingDriverBackend(
                            actions =
                                listOf(
                                    testPlayerChatActionDescriptor(),
                                    testPlayerChatActionDescriptor().copy(schemaVersion = "2"),
                                ),
                        ),
                )
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val error =
            assertFailsWith<IllegalArgumentException> {
                service.openApiFor("alice")
            }

        assertEquals("duplicate action id player.chat", error.message)
    }

    @Test
    fun `client specific openapi reports all action schema versions`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        RecordingDriverBackend(
                            actions =
                                listOf(
                                    testPlayerChatActionDescriptor(),
                                    testPlayerMoveActionDescriptor().copy(schemaVersion = "2"),
                                ),
                        ),
                )
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val extensions = service.openApiFor("alice").extensions

        assertEquals("1,2", extensions["x-craftless-action-schema-versions"])
        assertFalse(extensions.containsKey("x-craftless-action-schema-version"))
    }
}

private class RecordingDriverBackend(
    private val metadata: DriverRuntimeMetadata = DriverRuntimeMetadata.fake(),
    private val actions: List<DriverActionDescriptor> =
        listOf(
            testPlayerMoveActionDescriptor(),
            testPlayerChatActionDescriptor(),
        ),
) : DriverBackend {
    val calls = mutableListOf<String>()

    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult {
        calls += "connect $clientId ${target.host}:${target.port}"
        return DriverBackendResult(DriverBackendAction.CONNECT)
    }

    override fun stop(clientId: String): DriverBackendResult {
        calls += "stop $clientId"
        return DriverBackendResult(DriverBackendAction.STOP)
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> = actions

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = metadata

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult {
        val message = invocation.arguments["message"]?.jsonPrimitive?.content
        calls += "chat $clientId $message"
        return DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED, message)
    }
}

private fun testPlayerMoveActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.move",
        schemaVersion = "1",
        arguments =
            mapOf(
                "forward" to DriverActionArgument("boolean"),
                "backward" to DriverActionArgument("boolean"),
                "left" to DriverActionArgument("boolean"),
                "right" to DriverActionArgument("boolean"),
                "jump" to DriverActionArgument("boolean"),
                "sneak" to DriverActionArgument("boolean"),
                "sprint" to DriverActionArgument("boolean"),
                "ticks" to DriverActionArgument("integer"),
            ),
        result =
            DriverActionResultDescriptor(
                properties =
                    mapOf(
                        "action" to DriverActionResultProperty("string"),
                        "status" to DriverActionResultProperty("string"),
                        "message" to DriverActionResultProperty("string"),
                    ),
                required = listOf("action", "status"),
            ),
    )

private fun testPlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments =
            mapOf(
                "message" to DriverActionArgument("string", required = true),
            ),
    )

private fun assertErrorSchema(response: OpenApiResponse) {
    val schema = requireNotNull(response.content["application/json"]?.schema)
    assertEquals("object", schema.type)
    assertEquals(listOf("code", "message"), schema.required)
    assertEquals("string", schema.properties["code"]?.type)
    assertEquals("string", schema.properties["message"]?.type)
}
