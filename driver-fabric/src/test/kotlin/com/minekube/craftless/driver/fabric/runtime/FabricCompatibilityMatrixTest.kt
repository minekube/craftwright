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

        assertEquals("fabric-current-lane", lane.id)
        assertEquals(FabricCompatibilityStatus.SUPPORTED, lane.status)
        assertEquals(21, lane.javaMajorVersion)
        assertEquals("0.19.3", lane.loaderVersion)
        assertEquals("0.128.2+1.21.6", lane.fabricApiVersion)
        assertEquals("craftless-fabric-bindings", lane.mappingsFingerprint)
        assertEquals("fabric-current-lane", lane.providerId)
        assertEquals(null, lane.unsupportedReason)
    }

    @Test
    fun `matrix includes simulated non-current lane without claiming runtime support`() {
        val matrix = defaultFabricCompatibilityMatrix()

        val lane = matrix.resolve(currentLaneIdentity().copy(gameVersion = "26.2"))

        assertEquals("fabric-simulated-26", lane.id)
        assertEquals(FabricCompatibilityStatus.UNSUPPORTED, lane.status)
        assertEquals(25, lane.javaMajorVersion)
        assertEquals("runtime-lane-missing", lane.unsupportedReason)
        assertEquals("fabric-simulated-provider", lane.providerId)
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
            gameVersion = "1.21.6",
            loaderVersion = "0.19.3",
            fabricApiVersion = "0.128.2+1.21.6",
            mappingsFingerprint = "craftless-fabric-bindings",
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
