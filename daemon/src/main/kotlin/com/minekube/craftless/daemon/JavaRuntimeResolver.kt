package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.JavaRuntimeDescriptor
import com.minekube.craftless.protocol.JavaRuntimeListResult
import com.minekube.craftless.protocol.JavaRuntimeRequirement
import com.minekube.craftless.protocol.JavaRuntimeSelection
import com.minekube.craftless.protocol.JavaRuntimeSelectionStatus
import com.minekube.craftless.protocol.RejectedJavaRuntimeCandidate

class JavaRuntimeResolver(
    private val providers: List<JavaRuntimeProvider> =
        listOf(
            ConfiguredJavaRuntimeProvider(),
            ManagedCacheJavaRuntimeProvider(),
            MiseJavaRuntimeProvider(),
            SystemJavaRuntimeProvider(),
        ),
    private val validator: JavaRuntimeValidator = JavaRuntimeValidator(),
) {
    fun list(context: JavaRuntimeDiscoveryContext = JavaRuntimeDiscoveryContext()): JavaRuntimeListResult {
        val validated =
            providers
                .flatMap { provider -> provider.candidates(context) }
                .deduplicateByExecutable()
                .map { candidate -> candidate to validator.validate(candidate.executable) }
        return JavaRuntimeListResult(
            runtimes =
                validated.mapNotNull { (candidate, result) ->
                    if (result.status == JavaRuntimeValidationStatus.VALID && result.majorVersion != null && result.version != null) {
                        descriptor(candidate, result, requirementReason = "runtime-list")
                    } else {
                        null
                    }
                },
            rejected =
                validated.mapNotNull { (candidate, result) ->
                    if (result.status == JavaRuntimeValidationStatus.VALID) {
                        null
                    } else {
                        RejectedJavaRuntimeCandidate(
                            executable = candidate.executable.toString(),
                            provider = candidate.provider,
                            reason = result.reason ?: "java-validation-failed",
                            detectedMajorVersion = result.majorVersion,
                        )
                    }
                },
        )
    }

    fun resolve(
        requirement: JavaRuntimeRequirement,
        context: JavaRuntimeDiscoveryContext = JavaRuntimeDiscoveryContext(),
    ): JavaRuntimeSelection {
        val candidates = providers.flatMap { provider -> provider.candidates(context) }.deduplicateByExecutable()
        val validated =
            candidates.map { candidate ->
                candidate to validator.validate(candidate.executable)
            }
        val rejected =
            validated
                .mapNotNull { (candidate, result) ->
                    when {
                        result.status != JavaRuntimeValidationStatus.VALID ->
                            RejectedJavaRuntimeCandidate(
                                executable = candidate.executable.toString(),
                                provider = candidate.provider,
                                reason = result.reason ?: "java-validation-failed",
                                detectedMajorVersion = result.majorVersion,
                            )
                        result.majorVersion == null || result.majorVersion < requirement.majorVersion ->
                            RejectedJavaRuntimeCandidate(
                                executable = candidate.executable.toString(),
                                provider = candidate.provider,
                                reason = "java-major-too-low",
                                detectedMajorVersion = result.majorVersion,
                            )
                        else -> null
                    }
                }
        val selected =
            validated.firstOrNull { (_, result) ->
                result.status == JavaRuntimeValidationStatus.VALID &&
                    result.majorVersion != null &&
                    result.majorVersion >= requirement.majorVersion
            }
        if (selected == null) {
            return JavaRuntimeSelection(
                requirement = requirement,
                status = JavaRuntimeSelectionStatus.UNSATISFIED,
                rejected = rejected,
                reason = "java-runtime.unsatisfied",
            )
        }
        val (candidate, result) = selected
        return JavaRuntimeSelection(
            requirement = requirement,
            status = JavaRuntimeSelectionStatus.SELECTED,
            selected =
                descriptor(candidate, result, requirementReason = requirement.reason),
            rejected = rejected,
            reason = "${candidate.provider.name.lowercase()}-runtime-satisfies-requirement",
        )
    }

    private fun descriptor(
        candidate: JavaRuntimeCandidate,
        result: JavaRuntimeValidationResult,
        requirementReason: String,
    ): JavaRuntimeDescriptor =
        JavaRuntimeDescriptor(
            id = "${candidate.provider.name.lowercase()}:${result.majorVersion}:${candidate.executable}",
            provider = candidate.provider,
            javaHome =
                candidate.executable.parent
                    ?.parent
                    ?.toString(),
            executable = candidate.executable.toString(),
            majorVersion = requireNotNull(result.majorVersion),
            version = requireNotNull(result.version),
            vendor = result.vendor,
            architecture = result.architecture,
            managed = candidate.managed,
            evidence =
                mapOf(
                    "selection" to "java-runtime-resolver",
                    "requirementReason" to requirementReason,
                ),
        )

    private fun List<JavaRuntimeCandidate>.deduplicateByExecutable(): List<JavaRuntimeCandidate> {
        val seen = linkedSetOf<String>()
        return filter { candidate ->
            seen.add(
                candidate.executable
                    .toAbsolutePath()
                    .normalize()
                    .toString(),
            )
        }
    }
}
