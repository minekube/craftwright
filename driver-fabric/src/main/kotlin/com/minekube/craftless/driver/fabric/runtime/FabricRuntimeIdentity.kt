package com.minekube.craftless.driver.fabric.runtime

import com.minekube.craftless.protocol.RuntimeSourceEvidence

internal data class FabricRuntimeIdentity(
    val gameVersion: String,
    val loaderVersion: String,
    val fabricApiVersion: String,
    val mappingsFingerprint: String,
    val installedModsFingerprint: String,
    val registryFingerprint: String,
    val serverFeatureFingerprint: String,
    val permissionsFingerprint: String,
) {
    init {
        require(gameVersion.isNotBlank()) { "game version is required" }
        require(loaderVersion.isNotBlank()) { "loader version is required" }
        require(fabricApiVersion.isNotBlank()) { "Fabric API version is required" }
        require(mappingsFingerprint.isNotBlank()) { "mappings fingerprint is required" }
        require(installedModsFingerprint.isNotBlank()) { "installed mods fingerprint is required" }
        require(registryFingerprint.isNotBlank()) { "registry fingerprint is required" }
        require(serverFeatureFingerprint.isNotBlank()) { "server feature fingerprint is required" }
        require(permissionsFingerprint.isNotBlank()) { "permissions fingerprint is required" }
    }

    fun sourceEvidence(): List<RuntimeSourceEvidence> =
        listOf(
            RuntimeSourceEvidence("runtime-version", "game:$gameVersion"),
            RuntimeSourceEvidence("loader-version", "loader:$loaderVersion"),
            RuntimeSourceEvidence("mod-api-version", "mod-api:$fabricApiVersion"),
            RuntimeSourceEvidence("mappings", mappingsFingerprint),
            RuntimeSourceEvidence("installed-mods", installedModsFingerprint),
            RuntimeSourceEvidence("registry", registryFingerprint),
            RuntimeSourceEvidence("server-features", serverFeatureFingerprint),
            RuntimeSourceEvidence("permissions", permissionsFingerprint),
        )
}
