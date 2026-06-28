package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeSourceEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FabricRuntimeMetadataProviderTest {
    @Test
    fun `snapshot provider emits supplied Fabric runtime metadata`() {
        val snapshot =
            FabricRuntimeMetadataSnapshot(
                loaderVersion = "0.19.3",
                driver = "craftless-driver-fabric",
                driverVersion = "0.1.0-SNAPSHOT",
                mappings = "craftless-fabric-bindings-test",
                installedModsFingerprint =
                    fabricRuntimeFingerprint(
                        "mods",
                        listOf("minecraft@26.2", "fabricloader@0.19.3", "fabric-api@0.153.0+26.2"),
                    ),
                registryFingerprint = fabricRuntimeFingerprint("registries", listOf("block:minecraft:stone")),
                serverFeatureFingerprint = "server-features:not-connected",
                permissionsFingerprint = "permissions:local-client",
            )

        val metadata = SnapshotFabricRuntimeMetadataProvider(snapshot).runtimeMetadata("client")

        assertEquals("0.19.3", metadata.loaderVersion)
        assertEquals("craftless-driver-fabric", metadata.driver)
        assertEquals("0.1.0-SNAPSHOT", metadata.driverVersion)
        assertEquals("craftless-fabric-bindings-test", metadata.mappings)
        assertEquals(snapshot.installedModsFingerprint, metadata.installedModsFingerprint)
        assertEquals(snapshot.registryFingerprint, metadata.registryFingerprint)
        assertEquals("server-features:not-connected", metadata.serverFeatureFingerprint)
        assertEquals("permissions:local-client", metadata.permissionsFingerprint)
    }

    @Test
    fun `runtime fingerprint is deterministic order independent and change sensitive`() {
        val first = fabricRuntimeFingerprint("mods", listOf("b@2", "a@1"))
        val reordered = fabricRuntimeFingerprint("mods", listOf("a@1", "b@2"))
        val changed = fabricRuntimeFingerprint("mods", listOf("a@1", "b@3"))

        assertTrue(first.startsWith("mods:"))
        assertEquals(first, reordered)
        assertNotEquals(first, changed)
    }

    @Test
    fun `runtime resource projection includes metadata and caller lane evidence`() {
        val node =
            fabricRuntimeResourceNode(
                metadata =
                    DriverRuntimeMetadata(
                        loaderVersion = "0.19.3",
                        driver = "craftless-driver-fabric-official",
                        driverVersion = "0.1.0-SNAPSHOT",
                        mappings = "craftless-official-bindings-26-2",
                        installedModsFingerprint = "mods:test",
                        registryFingerprint = "registries:not-discovered",
                        serverFeatureFingerprint = "server-features:not-connected",
                        permissionsFingerprint = "permissions:local-client",
                    ),
                sourceEvidence =
                    listOf(
                        RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                        RuntimeSourceEvidence("runtime-status", "metadata-only"),
                        RuntimeSourceEvidence("runtime-java", "java:25"),
                    ),
            )

        val evidence = node.sourceEvidence.associate { it.kind to it.fingerprint }

        assertEquals("runtime", node.id)
        assertEquals(RuntimeAvailability.available(), node.availability)
        assertEquals("mods:test", evidence["installed-mods"])
        assertEquals("registries:not-discovered", evidence["registry"])
        assertEquals("server-features:not-connected", evidence["server-features"])
        assertEquals("permissions:local-client", evidence["permissions"])
        assertEquals("latest-current-official", evidence["runtime-lane"])
        assertEquals("metadata-only", evidence["runtime-status"])
        assertEquals("java:25", evidence["runtime-java"])
    }
}
