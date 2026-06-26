package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.JavaRuntimeRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MinecraftJavaRuntimeRequirementResolver {
    fun derive(
        versionManifest: String,
        minecraftVersion: String,
    ): JavaRuntimeRequirement {
        val javaVersion = Json.parseToJsonElement(versionManifest).jsonObject["javaVersion"]?.jsonObject
        val major = javaVersion?.get("majorVersion")?.jsonPrimitive?.intOrNull
        val component = javaVersion?.get("component")?.jsonPrimitive?.content
        if (major != null) {
            return JavaRuntimeRequirement(
                majorVersion = major,
                component = component,
                reason = "minecraft-version-metadata",
            )
        }
        require(isLegacyMinecraftVersion(minecraftVersion)) {
            "minecraft version $minecraftVersion is missing Java runtime metadata"
        }
        return JavaRuntimeRequirement(
            majorVersion = 8,
            reason = "minecraft-version-metadata-missing",
        )
    }

    private fun isLegacyMinecraftVersion(minecraftVersion: String): Boolean {
        val parts = minecraftVersion.split(".")
        if (parts.firstOrNull() != "1") return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return minor <= 16
    }
}
