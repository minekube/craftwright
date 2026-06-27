package com.minekube.craftless.driver.fabric.runtime

import com.minekube.craftless.protocol.RuntimeSourceEvidence

internal data class FabricCompatibilityMatrix(
    private val lanes: List<FabricCompatibilityLane>,
) {
    init {
        require(lanes.isNotEmpty()) { "Fabric compatibility matrix requires at least one lane" }
        require(lanes.map { it.id }.toSet().size == lanes.size) { "Fabric compatibility matrix lane ids must be unique" }
    }

    fun resolve(identity: FabricRuntimeIdentity): FabricCompatibilityLane =
        lanes.firstOrNull { lane -> lane.matches(identity) }
            ?: FabricCompatibilityLane.unsupported(identity.gameVersion)

    fun selectProvider(
        identity: FabricRuntimeIdentity,
        providers: List<FabricRuntimeProvider>,
    ): FabricRuntimeProviderSelection? {
        val lane = resolve(identity)
        if (lane.status != FabricCompatibilityStatus.SUPPORTED) {
            return null
        }
        val provider = providers.firstOrNull { candidate -> candidate.id == lane.providerId } ?: return null
        val support = provider.support(identity)
        if (support.state != FabricRuntimeSupportState.SUPPORTED) {
            return null
        }
        return FabricRuntimeProviderSelection(
            provider = provider,
            support = support,
            access = provider.createAccess(identity),
        )
    }
}

internal data class FabricCompatibilityLane(
    val id: String,
    val status: FabricCompatibilityStatus,
    val gameVersion: String,
    val loaderVersion: String,
    val fabricApiVersion: String,
    val javaMajorVersion: Int,
    val mappingsFingerprint: String,
    val providerId: String,
    val unsupportedReason: String? = null,
) {
    init {
        require(id.matches(machineCode)) { "Fabric compatibility lane id must be a machine-readable Craftless code" }
        require(gameVersion.isNotBlank()) { "Fabric compatibility lane game version is required" }
        require(loaderVersion.isNotBlank()) { "Fabric compatibility lane loader version is required" }
        require(fabricApiVersion.isNotBlank()) { "Fabric compatibility lane API version is required" }
        require(javaMajorVersion > 0) { "Fabric compatibility lane Java version must be positive" }
        require(mappingsFingerprint.isNotBlank()) { "Fabric compatibility lane mappings fingerprint is required" }
        require(providerId.matches(machineCode)) { "Fabric compatibility lane provider id must be a machine-readable code" }
        require(unsupportedReason == null || unsupportedReason.matches(machineCode)) {
            "Fabric compatibility lane unsupported reason must be a machine-readable code"
        }
        if (status == FabricCompatibilityStatus.UNSUPPORTED) {
            require(!unsupportedReason.isNullOrBlank()) { "unsupported Fabric compatibility lane requires a reason" }
        } else {
            require(unsupportedReason == null) { "supported Fabric compatibility lane must not declare unsupported reason" }
        }
    }

    fun matches(identity: FabricRuntimeIdentity): Boolean = identity.gameVersion == gameVersion

    fun sourceEvidence(): List<RuntimeSourceEvidence> =
        listOfNotNull(
            RuntimeSourceEvidence("runtime-lane", id.sanitizedEvidenceCode()),
            RuntimeSourceEvidence("runtime-status", status.name.lowercase().sanitizedEvidenceCode()),
            RuntimeSourceEvidence("runtime-provider", providerId.sanitizedEvidenceCode()),
            RuntimeSourceEvidence("runtime-java", "java:$javaMajorVersion"),
            unsupportedReason?.let { reason -> RuntimeSourceEvidence("runtime-support", reason) },
        )

    companion object {
        fun unsupported(gameVersion: String): FabricCompatibilityLane =
            FabricCompatibilityLane(
                id = "fabric-unsupported-${gameVersion.machineSuffix()}",
                status = FabricCompatibilityStatus.UNSUPPORTED,
                gameVersion = gameVersion,
                loaderVersion = "unknown",
                fabricApiVersion = "unknown",
                javaMajorVersion = 1,
                mappingsFingerprint = "none",
                providerId = "fabric-unsupported",
                unsupportedReason = "unsupported-version",
            )
    }
}

internal enum class FabricCompatibilityStatus {
    SUPPORTED,
    EXPERIMENTAL,
    UNSUPPORTED,
}

internal fun defaultFabricCompatibilityMatrix(): FabricCompatibilityMatrix =
    FabricCompatibilityMatrix(
        lanes =
            listOf(
                FabricCompatibilityLane(
                    id = FabricCompiledLaneMetadata.ID,
                    status = FabricCompatibilityStatus.SUPPORTED,
                    gameVersion = FabricCompiledLaneMetadata.MINECRAFT_VERSION,
                    loaderVersion = FabricCompiledLaneMetadata.LOADER_VERSION,
                    fabricApiVersion = FabricCompiledLaneMetadata.FABRIC_API_VERSION,
                    javaMajorVersion = FabricCompiledLaneMetadata.JAVA_MAJOR_VERSION,
                    mappingsFingerprint = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
                    providerId = FabricCompiledLaneMetadata.PROVIDER_ID,
                ),
                FabricCompatibilityLane(
                    id = "latest-release-26-2",
                    status = FabricCompatibilityStatus.UNSUPPORTED,
                    gameVersion = "26.2",
                    loaderVersion = "unverified",
                    fabricApiVersion = "unverified",
                    javaMajorVersion = 25,
                    mappingsFingerprint = "unverified",
                    providerId = "no-compatible-client-lane",
                    unsupportedReason = "runtime-lane-missing",
                ),
                FabricCompatibilityLane(
                    id = "older-release-1-20-6",
                    status = FabricCompatibilityStatus.UNSUPPORTED,
                    gameVersion = "1.20.6",
                    loaderVersion = "unverified",
                    fabricApiVersion = "unverified",
                    javaMajorVersion = 21,
                    mappingsFingerprint = "unverified",
                    providerId = "no-compatible-client-lane",
                    unsupportedReason = "runtime-lane-missing",
                ),
            ),
    )

private fun String.machineSuffix(): String =
    lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }

private fun String.sanitizedEvidenceCode(): String =
    machineSuffix()
        .replace(Regex("\\bfabric-?"), "")
        .replace(Regex("\\bminecraft-?"), "")
        .replace(Regex("\\byarn-?"), "")
        .replace(Regex("\\bintermediary-?"), "")
        .trim('-')
        .ifBlank { "runtime" }

private val machineCode = Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")
