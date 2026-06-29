package com.minekube.craftless.daemon

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionResultDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionSource
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.driver.runtime.BackendDriverSession
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.ClientPresentation
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.CreateClientRequest
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.OpenApiActionAvailability
import com.minekube.craftless.protocol.OpenApiActionSource
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiResourceAvailability
import com.minekube.craftless.protocol.OpenApiResponse
import com.minekube.craftless.protocol.Profile
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.testkit.FakeDriverSession
import com.minekube.craftless.testkit.fakeDriverRuntimeMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClientSessionServiceTest {
    @Test
    fun `session service requires an explicit driver factory`() {
        val service = ClientSessionService.inMemory()

        val error =
            assertFailsWith<IllegalStateException> {
                service.createClient(
                    CreateClientRequest(
                        id = "alice",
                        version = "1.21.4",
                        loader = Loader.FABRIC,
                        profile = Profile.offline("Alice"),
                    ),
                )
            }

        assertEquals("no Craftless driver runtime configured for client alice", error.message)
        assertTrue(service.listClients().isEmpty())
    }

    @Test
    fun `offline session creates running client with generated api route`() {
        val service = fakeClientSessionService()
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
        assertEquals(ClientPresentation(), client.presentation)
        assertEquals("/clients/alice/events", service.routesFor("alice").first { it.path.endsWith("/events") }.path)
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/openapi.json" })
        assertTrue(service.routesFor("alice").any { it.path == "/clients/alice/resources" })
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
    fun `session service derives profile and lifecycle presentation defaults`() {
        val service = fakeClientSessionService()
        val client =
            service.createClient(
                CreateClientRequest(
                    id = "bot",
                    version = "latest-release",
                    loader = Loader.FABRIC,
                ),
            )

        assertEquals("Bot", client.profile.name)
        assertEquals(ClientPresentation(), client.presentation)
    }

    @Test
    fun `attached driver replaces prepared session as openapi authority`() {
        val service =
            ClientSessionService.inMemory(
                DriverSessionFactory { request ->
                    PreparedOnlyTestDriverSession(request.id)
                },
            )
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.6",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )
        assertEquals("craftless-prepared-client-runtime", service.openApiFor("alice").extensions["x-craftless-driver"])

        service.attachDriver("alice", AttachedTestDriverSession("alice"))

        val document = service.openApiFor("alice")
        assertEquals("craftless-driver-fabric", document.extensions["x-craftless-driver"])
        assertTrue(document.actions.any { action -> action.id == "player.chat" })
    }

    @Test
    fun `session service lists clients in creation order`() {
        val service = fakeClientSessionService()

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
    fun `session service prepares configured instance file directories`() {
        val workspace = Files.createTempDirectory("craftless-client-files")
        val fileStore = InstanceFileStore(workspace)
        val service = fakeClientSessionService(fileStore)

        val client =
            service.createClient(
                CreateClientRequest(
                    id = "alice",
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("Alice"),
                ),
            )

        client.instance.files.directoryHandles().forEach { handle ->
            assertTrue(Files.isDirectory(workspace.resolve(handle)))
        }
    }

    @Test
    fun `instance file store preparation is idempotent and preserves existing runtime files`() {
        val workspace = Files.createTempDirectory("craftless-client-files-repeat")
        val fileStore = InstanceFileStore(workspace)
        val service = fakeClientSessionService(fileStore)
        val client =
            service.createClient(
                CreateClientRequest(
                    id = "alice",
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("Alice"),
                ),
            )
        val artifact = workspace.resolve(client.instance.files.artifacts).resolve("evidence.jsonl")
        Files.writeString(artifact, "kept\n")

        fileStore.prepare(client.instance.files)

        assertEquals("kept\n", Files.readString(artifact))
    }

    @Test
    fun `session service rejects client ids that cannot be used as route segments`() {
        val service = fakeClientSessionService()

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
        val service = fakeClientSessionService()
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
        assertEquals(document.extensions["runtimeGraphFingerprint"], document.extensions["x-craftless-runtime-fingerprint"])
        assertTrue(document.paths.containsKey("/clients/alice/openapi.json"))
        assertTrue(document.paths.containsKey("/clients/alice"))
        assertTrue(document.paths.containsKey("/clients/alice:connect"))
        assertTrue(document.paths.containsKey("/clients/alice:stop"))
        assertTrue(document.paths.containsKey("/clients/alice/actions"))
        assertTrue(document.paths.containsKey("/clients/alice/resources"))
        assertTrue(document.paths.containsKey("/clients/alice:run"))
        assertTrue(document.paths.containsKey("/clients/alice/player:chat"))
        assertTrue(document.paths.containsKey("/clients/alice/player:move"))
        assertEquals("getClient", document.paths["/clients/alice"]?.get?.operationId)
        assertEquals("getClientOpenapiJson", document.paths["/clients/alice/openapi.json"]?.get?.operationId)
        assertEquals("clientConnect", document.paths["/clients/alice:connect"]?.post?.operationId)
        assertEquals("stopClient", document.paths["/clients/alice:stop"]?.post?.operationId)
        assertEquals("listClientActions", document.paths["/clients/alice/actions"]?.get?.operationId)
        assertEquals("listClientResources", document.paths["/clients/alice/resources"]?.get?.operationId)
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
        assertEquals(listOf("id", "instance", "profile", "presentation", "state"), clientSchema.required)
        val presentationSchema = clientSchema.properties["presentation"]
        assertNotNull(presentationSchema)
        assertEquals("object", presentationSchema.type)
        assertEquals(listOf("window", "audio"), presentationSchema.required)
        val windowSchema = presentationSchema.properties["window"]
        assertNotNull(windowSchema)
        assertEquals("string", windowSchema.type)
        assertEquals(listOf("NONE", "VISIBLE"), windowSchema.enumValues)
        assertEquals("NONE", windowSchema.default)
        val audioSchema = presentationSchema.properties["audio"]
        assertNotNull(audioSchema)
        assertEquals("string", audioSchema.type)
        assertEquals(listOf("MUTED", "DEFAULT"), audioSchema.enumValues)
        assertEquals("MUTED", audioSchema.default)
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
        assertEquals(listOf("player", "media.screenshot"), document.resources.map { it.id })
        assertEquals(listOf("player.chat", "player.move"), document.resources.single { it.id == "player" }.actions)
        assertEquals(listOf("media.screenshot.capture"), document.resources.single { it.id == "media.screenshot" }.actions)
        assertEquals(OpenApiResourceAvailability.AVAILABLE, document.resources.single { it.id == "player" }.availability)
        assertEquals(OpenApiResourceAvailability.AVAILABLE, document.resources.single { it.id == "media.screenshot" }.availability)
    }

    @Test
    fun `client resources are projected from the same live action snapshot`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        RecordingDriverBackend(
                            actions =
                                listOf(
                                    DriverActionDescriptor(
                                        id = "player.query",
                                        schemaVersion = "1",
                                    ),
                                    DriverActionDescriptor(
                                        id = "player.raycast",
                                        schemaVersion = "1",
                                        source = DriverActionSource.RUNTIME_PROBE,
                                        availability = DriverActionAvailability.UNAVAILABLE,
                                        availabilityReason = "client-not-connected",
                                    ),
                                    DriverActionDescriptor(
                                        id = "world.block.break",
                                        schemaVersion = "1",
                                    ),
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
        val resources = service.resourcesFor("alice")

        assertEquals(document.resources, resources)
        assertEquals(listOf("player", "world.block"), resources.map { it.id })
        val player = resources.single { it.id == "player" }
        assertEquals(listOf("player.query", "player.raycast"), player.actions)
        assertEquals(OpenApiResourceAvailability.PARTIAL, player.availability)
        assertEquals(listOf("client-not-connected"), player.availabilityReasons)
        assertEquals(listOf("player.query", "player.raycast"), player.actionDescriptors.map { it.id })
        assertEquals(
            "client-not-connected",
            player.actionDescriptors.single { it.id == "player.raycast" }.availabilityReason,
        )
    }

    @Test
    fun `client specific openapi preserves driver action source and availability`() {
        val service =
            ClientSessionService.inMemory(
                DriverSessionFactory { request ->
                    BackendDriverSession(
                        clientId = request.id,
                        backend =
                            RecordingDriverBackend(
                                actions =
                                    listOf(
                                        DriverActionDescriptor(
                                            id = "player.raycast",
                                            schemaVersion = "1",
                                            source = DriverActionSource.RUNTIME_PROBE,
                                            availability = DriverActionAvailability.UNAVAILABLE,
                                            availabilityReason = "client-not-connected",
                                        ),
                                    ),
                            ),
                    )
                },
            )
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val document = service.openApiFor("alice")
        val action = document.actions.single()

        assertEquals("player.raycast", action.id)
        assertEquals(OpenApiActionSource.RUNTIME_PROBE, action.source)
        assertEquals(OpenApiActionAvailability.UNAVAILABLE, action.availability)
        assertEquals("client-not-connected", action.availabilityReason)
        assertTrue(document.extensions["x-craftless-action-fingerprint"]?.startsWith("graph:") == true)
        assertEquals(document.extensions["runtimeGraphFingerprint"], document.extensions["x-craftless-action-fingerprint"])
    }

    @Test
    fun `client specific openapi preserves nested driver action argument schemas`() {
        val service =
            ClientSessionService.inMemory(
                DriverSessionFactory { request ->
                    BackendDriverSession(
                        clientId = request.id,
                        backend =
                            RecordingDriverBackend(
                                actions =
                                    listOf(
                                        DriverActionDescriptor(
                                            id = "world.block.break",
                                            schemaVersion = "1",
                                            arguments =
                                                mapOf(
                                                    "target" to
                                                        DriverActionArgument(
                                                            type = "object",
                                                            required = true,
                                                            properties =
                                                                mapOf(
                                                                    "handle" to DriverActionArgument("string"),
                                                                    "position" to
                                                                        DriverActionArgument(
                                                                            type = "object",
                                                                            properties =
                                                                                mapOf(
                                                                                    "x" to DriverActionArgument("integer"),
                                                                                    "y" to DriverActionArgument("integer"),
                                                                                    "z" to DriverActionArgument("integer"),
                                                                                ),
                                                                        ),
                                                                ),
                                                        ),
                                                ),
                                        ),
                                    ),
                            ),
                    )
                },
            )
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.4",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val schema =
            service
                .openApiFor("alice")
                .paths["/clients/alice/world/block:break"]
                ?.post
                ?.requestBody
                ?.content
                ?.get("application/json")
                ?.schema

        assertNotNull(schema)
        assertEquals(listOf("target"), schema.required)
        val targetSchema = schema.properties["target"]
        val positionSchema = targetSchema?.properties?.get("position")
        assertEquals("object", targetSchema?.type)
        assertEquals("string", targetSchema?.properties?.get("handle")?.type)
        assertEquals("object", positionSchema?.type)
        assertEquals("integer", positionSchema?.properties?.get("x")?.type)
        assertEquals("integer", positionSchema?.properties?.get("y")?.type)
        assertEquals("integer", positionSchema?.properties?.get("z")?.type)
    }

    @Test
    fun `minecraft usernames longer than sixteen characters are rejected`() {
        val service = fakeClientSessionService()

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
        val service = fakeClientSessionService()
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
        assertTrue(driver.events().none { it.message == "from driver" })
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
        assertTrue(extensions["x-craftless-runtime-fingerprint"]?.startsWith("graph:") == true)
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
        assertTrue(document.extensions["x-craftless-runtime-fingerprint"]?.startsWith("graph:") == true)
        assertEquals(document.extensions["runtimeGraphFingerprint"], document.extensions["x-craftless-action-fingerprint"])
    }

    @Test
    fun `client specific openapi derives aliases from the same action snapshot as metadata`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        ChangingActionsBackend(
                            snapshots =
                                listOf(
                                    listOf(testPlayerChatActionDescriptor()),
                                    listOf(testPlayerMoveActionDescriptor()),
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

        assertEquals(listOf("player.chat"), document.actions.map { it.id })
        assertTrue(document.paths.containsKey("/clients/alice/player:chat"))
        assertFalse(document.paths.containsKey("/clients/alice/player:move"))
        assertEquals(
            "player.chat",
            document.paths["/clients/alice/player:chat"]
                ?.post
                ?.extensions
                ?.get("x-craftless-action"),
        )
    }

    @Test
    fun `client route list is projected from generated runtime graph openapi`() {
        val service =
            ClientSessionService.inMemory { request ->
                GraphOnlyRouteTestDriverSession(request.id)
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.6",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val document = service.openApiFor("alice")
        val routes = service.routesFor("alice")
        val clientDocumentPaths =
            document.paths.keys
                .filter { path -> path == "/clients/alice" || path.startsWith("/clients/alice/") || path.startsWith("/clients/alice:") }
                .sorted()

        assertEquals(clientDocumentPaths, routes.map { it.path }.distinct().sorted())
        assertTrue(routes.any { it.path == "/clients/alice/player:chat" && it.actionId == "player.chat" })
        assertFalse(routes.any { it.path == "/clients/alice/player:move" })
        assertEquals(listOf("player.chat"), document.actions.map { it.id })
    }

    @Test
    fun `descriptor only actions are not public openapi authority when runtime graph is empty`() {
        val service =
            ClientSessionService.inMemory { request ->
                DescriptorOnlyTestDriverSession(request.id)
            }
        service.createClient(
            CreateClientRequest(
                id = "alice",
                version = "1.21.6",
                loader = Loader.FABRIC,
                profile = Profile.offline("Alice"),
            ),
        )

        val document = service.openApiFor("alice")
        val routes = service.routesFor("alice")

        assertEquals(emptyList(), document.actions.map { it.id })
        assertEquals(emptyList(), document.resources.map { it.id })
        assertFalse(document.paths.containsKey("/clients/alice/player:chat"))
        assertFalse(routes.any { it.path == "/clients/alice/player:chat" })
    }

    @Test
    fun `client specific openapi derives nested resource action aliases`() {
        val service =
            ClientSessionService.inMemory { request ->
                BackendDriverSession(
                    clientId = request.id,
                    backend =
                        RecordingDriverBackend(
                            actions =
                                listOf(
                                    DriverActionDescriptor(
                                        id = "world.block.break",
                                        schemaVersion = "1",
                                    ),
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

        assertEquals(listOf("world.block.break"), document.actions.map { it.id })
        assertTrue(document.paths.containsKey("/clients/alice/world/block:break"))
        assertEquals(
            "world.block.break",
            document.paths["/clients/alice/world/block:break"]
                ?.post
                ?.extensions
                ?.get("x-craftless-action"),
        )
        assertEquals("runWorldBlockBreak", document.paths["/clients/alice/world/block:break"]?.post?.operationId)
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

        assertEquals("duplicate runtime operation id player.chat", error.message)
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

        assertTrue(extensions["x-craftless-action-schema-versions"]?.startsWith("graph:") == true)
        assertFalse(extensions.containsKey("x-craftless-action-schema-version"))
    }
}

private class ChangingActionsBackend(
    private val snapshots: List<List<DriverActionDescriptor>>,
) : DriverBackend {
    private var calls = 0

    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult = DriverBackendResult(DriverBackendAction.CONNECT)

    override fun stop(clientId: String): DriverBackendResult = DriverBackendResult(DriverBackendAction.STOP)

    override fun actions(clientId: String): List<DriverActionDescriptor> {
        val index = calls.coerceAtMost(snapshots.lastIndex)
        calls += 1
        return snapshots[index]
    }

    override fun runtimeGraph(clientId: String): RuntimeCapabilityGraph = actions(clientId).toRuntimeGraph(clientId)

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult = DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED)
}

private class PreparedOnlyTestDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun actions(): List<DriverActionDescriptor> = emptyList()

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            driver = "craftless-prepared-client-runtime",
            mappings = "runtime-launch-plan",
            installedModsFingerprint = "mods:fabric",
            registryFingerprint = "registries:unattached",
            serverFeatureFingerprint = "server-features:unattached",
            permissionsFingerprint = "permissions:workspace",
        )

    override fun runtimeGraph(): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "not-attached",
        )

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events() = emptyList<com.minekube.craftless.driver.api.DriverEvent>()
}

private class AttachedTestDriverSession(
    override val clientId: String,
) : DriverSession {
    private val fake = FakeDriverSession(clientId)

    override fun snapshot(): DriverClientSnapshot = fake.snapshot()

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = fake.connect(target)

    override fun actions(): List<DriverActionDescriptor> = fake.actions()

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = "0.19.3",
            driver = "craftless-driver-fabric",
            mappings = "craftless-fabric-bindings",
            installedModsFingerprint = "mods:attached",
            registryFingerprint = "registries:attached",
            serverFeatureFingerprint = "server-features:attached",
            permissionsFingerprint = "permissions:local-client",
        )

    override fun runtimeGraph(): RuntimeCapabilityGraph = fake.runtimeGraph()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult = fake.invoke(invocation)

    override fun stop(): DriverClientSnapshot = fake.stop()

    override fun events() = fake.events()
}

private class GraphOnlyRouteTestDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun actions(): List<DriverActionDescriptor> = error("routes must be projected from generated runtime graph OpenAPI")

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "graph.chat",
                        arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED)

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events() = emptyList<com.minekube.craftless.driver.api.DriverEvent>()
}

private class DescriptorOnlyTestDriverSession(
    override val clientId: String,
) : DriverSession {
    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun actions(): List<DriverActionDescriptor> = listOf(testPlayerChatActionDescriptor())

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.ACCEPTED)

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events() = emptyList<com.minekube.craftless.driver.api.DriverEvent>()
}

private fun fakeClientSessionService(fileStore: InstanceFileStore? = null): ClientSessionService =
    ClientSessionService.inMemory(
        driverFactory =
            DriverSessionFactory { request ->
                FakeDriverSession(request.id)
            },
        fileStore = fileStore,
    )

private class RecordingDriverBackend(
    private val metadata: DriverRuntimeMetadata = fakeDriverRuntimeMetadata(),
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

    override fun runtimeGraph(clientId: String): RuntimeCapabilityGraph = actions.toRuntimeGraph(clientId)

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

private fun List<DriverActionDescriptor>.toRuntimeGraph(clientId: String): RuntimeCapabilityGraph {
    val operations = map { action -> action.toRuntimeOperationNode() }
    return RuntimeCapabilityGraph(
        clientId = clientId,
        resources =
            operations
                .map { operation -> operation.resource }
                .distinct()
                .sorted()
                .map { resource -> RuntimeResourceNode(resource, RuntimeAvailability.available()) },
        operations = operations,
    )
}

private fun DriverActionDescriptor.toRuntimeOperationNode(): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = id.substringBeforeLast("."),
        adapter = "test.${id.replace('.', '-')}",
        arguments = arguments.mapValues { (_, argument) -> argument.toRuntimeSchema() },
        result = result.toRuntimeSchema(),
        availability =
            when (availability) {
                DriverActionAvailability.AVAILABLE -> RuntimeAvailability.available()
                DriverActionAvailability.UNAVAILABLE -> RuntimeAvailability.unavailable(requireNotNull(availabilityReason))
            },
    )

private fun DriverActionArgument.toRuntimeSchema(): RuntimeSchema =
    RuntimeSchema(
        type = type,
        required = required,
        properties = properties.mapValues { (_, argument) -> argument.toRuntimeSchema() },
        items = items?.toRuntimeSchema(),
    )

private fun DriverActionResultDescriptor.toRuntimeSchema(): RuntimeSchema =
    properties["data"]?.toRuntimeSchema(required = "data" in required)
        ?: RuntimeSchema(
            type = "object",
            properties =
                properties
                    .filterKeys { name -> name !in setOf("action", "status", "message") }
                    .mapValues { (name, property) ->
                        property.toRuntimeSchema(required = name in required)
                    },
        )

private fun DriverActionResultProperty.toRuntimeSchema(required: Boolean = false): RuntimeSchema =
    RuntimeSchema(
        type = type,
        required = required,
        properties = properties.mapValues { (_, property) -> property.toRuntimeSchema() },
        items = items?.toRuntimeSchema(),
    )

private fun assertErrorSchema(response: OpenApiResponse) {
    val schema = requireNotNull(response.content["application/json"]?.schema)
    assertEquals("object", schema.type)
    assertEquals(listOf("code", "message"), schema.required)
    assertEquals("string", schema.properties["code"]?.type)
    assertEquals("string", schema.properties["message"]?.type)
}
