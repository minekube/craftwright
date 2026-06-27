package com.minekube.craftless.driver.fabric.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FabricCompatibilityMatrixTest {
    @Test
    fun `matrix exposes the current compiled Fabric lane`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val lane = matrix.resolve(currentLaneIdentity())

        assertEquals(FabricCompiledLaneMetadata.ID, lane.id)
        assertEquals(FabricCompatibilityStatus.SUPPORTED, lane.status)
        assertEquals(FabricCompiledLaneMetadata.JAVA_MAJOR_VERSION, lane.javaMajorVersion)
        assertEquals(FabricCompiledLaneMetadata.LOADER_VERSION, lane.loaderVersion)
        assertEquals(FabricCompiledLaneMetadata.FABRIC_API_VERSION, lane.fabricApiVersion)
        assertEquals(FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT, lane.mappingsFingerprint)
        assertEquals(FabricCompiledLaneMetadata.PROVIDER_ID, lane.providerId)
        assertEquals(null, lane.unsupportedReason)
    }

    @Test
    fun `matrix includes latest release lane without claiming runtime support`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val lane = matrix.resolve(currentLaneIdentity().copy(gameVersion = "26.2"))

        assertEquals("latest-release-26-2", lane.id)
        assertEquals(FabricCompatibilityStatus.UNSUPPORTED, lane.status)
        assertEquals(25, lane.javaMajorVersion)
        assertEquals("runtime-lane-missing", lane.unsupportedReason)
        assertEquals("no-compatible-client-lane", lane.providerId)
    }

    @Test
    fun `matrix includes representative older release lane without claiming runtime support`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val lane = matrix.resolve(currentLaneIdentity().copy(gameVersion = "1.20.6"))

        assertEquals("older-release-1-20-6", lane.id)
        assertEquals(FabricCompatibilityStatus.UNSUPPORTED, lane.status)
        assertEquals(21, lane.javaMajorVersion)
        assertEquals("runtime-lane-missing", lane.unsupportedReason)
        assertEquals("no-compatible-client-lane", lane.providerId)
    }

    @Test
    fun `lane source evidence records status separately from unavailable reason`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val currentEvidence = matrix.resolve(currentLaneIdentity()).sourceEvidence()
        val unsupportedEvidence = matrix.resolve(currentLaneIdentity().copy(gameVersion = "26.2")).sourceEvidence()

        assertTrue(currentEvidence.any { it.kind == "runtime-status" && it.fingerprint == "supported" })
        assertTrue(currentEvidence.none { it.kind == "runtime-support" })
        assertTrue(unsupportedEvidence.any { it.kind == "runtime-status" && it.fingerprint == "unsupported" })
        assertTrue(unsupportedEvidence.any { it.kind == "runtime-support" && it.fingerprint == "runtime-lane-missing" })
    }

    @Test
    fun `matrix reports unknown versions with machine readable reason`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val lane = matrix.resolve(currentLaneIdentity().copy(gameVersion = "0.0.0-test"))

        assertEquals(FabricCompatibilityStatus.UNSUPPORTED, lane.status)
        assertEquals("unsupported-version", lane.unsupportedReason)
        assertTrue(lane.id.startsWith("fabric-unsupported-"))
    }

    @Test
    fun `matrix can select runtime providers by resolved lane`() {
        val matrix = defaultFabricCompatibilityMatrix()
        val provider = TestProvider("fabric-current-lane")

        val selection = matrix.selectProvider(currentLaneIdentity(), listOf(provider))

        assertNotNull(selection)
        assertEquals("fabric-current-lane", selection.provider.id)
        assertEquals(FabricRuntimeSupportState.SUPPORTED, selection.support.state)
    }

    private fun currentLaneIdentity(): FabricRuntimeIdentity =
        FabricRuntimeIdentity(
            gameVersion = FabricCompiledLaneMetadata.MINECRAFT_VERSION,
            loaderVersion = FabricCompiledLaneMetadata.LOADER_VERSION,
            fabricApiVersion = FabricCompiledLaneMetadata.FABRIC_API_VERSION,
            mappingsFingerprint = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
            installedModsFingerprint = "mods:current-lane",
            registryFingerprint = "registries:current-lane",
            serverFeatureFingerprint = "server-features:local",
            permissionsFingerprint = "permissions:local",
        )

    private class TestProvider(
        override val id: String,
    ) : FabricRuntimeProvider {
        override fun support(identity: FabricRuntimeIdentity): FabricRuntimeSupport = FabricRuntimeSupport.supported()

        override fun createAccess(identity: FabricRuntimeIdentity): FabricRuntimeAccess =
            object : FabricRuntimeAccess {
                override val identity: FabricRuntimeIdentity = identity
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
}
