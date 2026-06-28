package com.minekube.craftless.driver.fabric.runtime

import com.minekube.craftless.protocol.RuntimeSourceEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FabricRuntimeProviderTest {
    @Test
    fun `runtime identity represents version and fingerprint inputs as Craftless evidence`() {
        val identity =
            FabricRuntimeIdentity(
                gameVersion = "1.21.6",
                loaderVersion = "0.19.3",
                fabricApiVersion = "0.128.2+1.21.6",
                mappingsFingerprint = "mappings:current-lane",
                installedModsFingerprint = "mods:current-lane",
                registryFingerprint = "registries:current-lane",
                serverFeatureFingerprint = "server-features:local",
                permissionsFingerprint = "permissions:local",
            )

        assertEquals("1.21.6", identity.gameVersion)
        assertEquals("0.19.3", identity.loaderVersion)
        assertEquals("0.128.2+1.21.6", identity.fabricApiVersion)
        assertEquals(
            setOf(
                "runtime-version",
                "loader-version",
                "mod-api-version",
                "mappings",
                "installed-mods",
                "registry",
                "server-features",
                "permissions",
            ),
            identity.sourceEvidence().map(RuntimeSourceEvidence::kind).toSet(),
        )
        assertTrue(identity.sourceEvidence().none { evidence -> evidence.fingerprint.contains("net.minecraft") })
        assertTrue(identity.sourceEvidence().none { evidence -> evidence.fingerprint.contains("yarn") })
    }

    @Test
    fun `provider selection records selected provider support evidence`() {
        val identity = currentLaneIdentity()
        val provider =
            TestFabricRuntimeProvider(
                providerId = "fabric-current-lane",
                supportedGameVersion = "1.21.6",
                access = TestFabricRuntimeAccess(identity),
            )

        val selection = selectFabricRuntimeProvider(identity, listOf(provider))

        assertEquals(FabricRuntimeSupportState.SUPPORTED, selection.support.state)
        assertEquals("fabric-current-lane", selection.provider.id)
        assertEquals("supported", selection.support.reason)
        assertSame(provider.access, selection.access)
    }

    @Test
    fun `provider selection reports machine readable matrix unsupported reasons`() {
        val unsupportedIdentity = currentLaneIdentity().copy(gameVersion = "26.2")
        val provider =
            TestFabricRuntimeProvider(
                providerId = "fabric-current-lane",
                supportedGameVersion = "1.21.6",
                access = TestFabricRuntimeAccess(currentLaneIdentity()),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                selectFabricRuntimeProvider(unsupportedIdentity, listOf(provider))
            }

        assertTrue(error.message?.contains("unsupported-version") == true)
    }

    @Test
    fun `provider selection rejects identities from unsupported compatibility lanes`() {
        val unsupportedIdentity = currentLaneIdentity().copy(gameVersion = "26.2")
        val provider =
            TestFabricRuntimeProvider(
                providerId = "fabric-current-lane",
                supportedGameVersion = "26.2",
                access = TestFabricRuntimeAccess(unsupportedIdentity),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                selectFabricRuntimeProvider(unsupportedIdentity, listOf(provider))
            }

        assertTrue(error.message?.contains("unsupported-version") == true)
    }

    @Test
    fun `provider selection requires the supported matrix lane provider id`() {
        val identity = currentLaneIdentity()
        val provider =
            TestFabricRuntimeProvider(
                providerId = "fabric-compatible-but-wrong-id",
                supportedGameVersion = "1.21.6",
                access = TestFabricRuntimeAccess(identity),
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                selectFabricRuntimeProvider(identity, listOf(provider))
            }

        assertTrue(error.message?.contains("fabric-current-lane:provider-missing") == true)
    }

    @Test
    fun `runtime access boundary exposes stable ports`() {
        val access = TestFabricRuntimeAccess(currentLaneIdentity())

        assertEquals("1.21.6", access.identity.gameVersion)
        val ports =
            listOf(
                access.clientState,
                access.registries,
                access.targeting,
                access.interaction,
                access.inventory,
                access.screen,
                access.entities,
                access.world,
                access.events,
            )
        val portInterfaces: Set<String> =
            ports.mapTo(mutableSetOf()) { port ->
                port::class.java
                    .interfaces
                    .single()
                    .simpleName
            }

        assertEquals(
            setOf(
                "FabricClientStateAccess",
                "FabricRegistryAccess",
                "FabricTargetingAccess",
                "FabricInteractionAccess",
                "FabricInventoryAccess",
                "FabricScreenAccess",
                "FabricEntityAccess",
                "FabricWorldAccess",
                "FabricEventAccess",
            ),
            portInterfaces,
        )
    }

    private fun currentLaneIdentity(): FabricRuntimeIdentity =
        FabricRuntimeIdentity(
            gameVersion = "1.21.6",
            loaderVersion = "0.19.3",
            fabricApiVersion = "0.128.2+1.21.6",
            mappingsFingerprint = "mappings:current-lane",
            installedModsFingerprint = "mods:current-lane",
            registryFingerprint = "registries:current-lane",
            serverFeatureFingerprint = "server-features:local",
            permissionsFingerprint = "permissions:local",
        )

    private class TestFabricRuntimeProvider(
        private val providerId: String,
        private val supportedGameVersion: String,
        val access: FabricRuntimeAccess,
    ) : FabricRuntimeProvider {
        override val id: String = providerId

        override fun support(identity: FabricRuntimeIdentity): FabricRuntimeSupport =
            if (identity.gameVersion == supportedGameVersion) {
                FabricRuntimeSupport.supported()
            } else {
                FabricRuntimeSupport.unsupported("unsupported-version")
            }

        override fun createAccess(identity: FabricRuntimeIdentity): FabricRuntimeAccess = access
    }

    private class TestFabricRuntimeAccess(
        override val identity: FabricRuntimeIdentity,
    ) : FabricRuntimeAccess {
        override val clientState: FabricClientStateAccess = object : FabricClientStateAccess {}
        override val registries: FabricRegistryAccess = object : FabricRegistryAccess {}
        override val targeting: FabricTargetingAccess = object : FabricTargetingAccess {}
        override val interaction: FabricInteractionAccess = object : FabricInteractionAccess {}
        override val inventory: FabricInventoryAccess = object : FabricInventoryAccess {}
        override val screen: FabricScreenAccess = object : FabricScreenAccess {}
        override val entities: FabricEntityAccess = object : FabricEntityAccess {}
        override val world: FabricWorldAccess = object : FabricWorldAccess {}
        override val events: FabricEventAccess = object : FabricEventAccess {}
    }
}
