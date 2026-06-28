import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Path

plugins {
    application
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":daemon"))
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core-jvm:3.5.0")
    implementation("io.ktor:ktor-client-cio-jvm:3.5.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(project(":driver-api"))
    testImplementation(project(":testkit"))
    testImplementation("io.ktor:ktor-server-core-jvm:3.5.0")
    testImplementation("io.ktor:ktor-server-cio-jvm:3.5.0")
}

application {
    applicationName = "craftless"
    mainClass.set("com.minekube.craftless.cli.MainKt")
}

val fabricDriverProject = project(":driver-fabric")
val fabricDriverArtifactStagingDir = layout.buildDirectory.dir("generated/driver-lane-artifacts")

fun catalogEntries(catalog: Map<*, *>): List<Map<*, *>> {
    val entries = catalog["entries"] as? List<*> ?: error("Fabric driver lane catalog entries must be a list")
    return entries.map { rawEntry ->
        rawEntry as? Map<*, *> ?: error("Fabric driver lane catalog entry must be an object")
    }
}

fun requiredCatalogString(
    entry: Map<*, *>,
    field: String,
): String =
    entry[field]
        ?.toString()
        ?.takeIf { value -> value.isNotBlank() }
        ?: error("Fabric driver lane entry requires $field")

fun validatedDistributionPath(distributionPath: String): String {
    val relativePath = Path.of(distributionPath)
    require(!relativePath.isAbsolute && relativePath.normalize() == relativePath) {
        "Fabric driver lane distributionPath must be a relative normalized path: $distributionPath"
    }
    return distributionPath
}

fun renderDriverModManifest(catalog: Map<*, *>): String {
    val entries =
        catalogEntries(catalog).map { entry ->
            linkedMapOf(
                "loader" to requiredCatalogString(entry, "loader"),
                "minecraftVersion" to requiredCatalogString(entry, "minecraftVersion"),
                "loaderVersion" to requiredCatalogString(entry, "loaderVersion"),
                "path" to validatedDistributionPath(requiredCatalogString(entry, "distributionPath")),
            )
        }
    return JsonOutput.prettyPrint(JsonOutput.toJson(linkedMapOf("entries" to entries))) + "\n"
}

gradle.projectsEvaluated {
    val fabricDriverRemapJar = fabricDriverProject.tasks.named("remapJar")
    val fabricDriverLaneCatalogTask = fabricDriverProject.tasks.named("writeFabricDriverLaneCatalog")
    val fabricDriverLaneCatalog =
        fabricDriverProject.layout.buildDirectory.file("generated/driver-lanes/fabric-driver-lanes.json")
    val driverModManifest =
        tasks.register("writeDriverModManifest") {
            val outputFile = layout.buildDirectory.file("generated/driver-mods/driver-mods.json")
            dependsOn(fabricDriverLaneCatalogTask)
            inputs.file(fabricDriverLaneCatalog)
            outputs.file(outputFile)

            doLast {
                val output = outputFile.get().asFile
                val catalog = JsonSlurper().parse(fabricDriverLaneCatalog.get().asFile) as Map<*, *>
                output.parentFile.mkdirs()
                output.writeText(renderDriverModManifest(catalog))
            }
        }
    val stageFabricDriverLaneArtifacts =
        tasks.register("stageFabricDriverLaneArtifacts") {
            dependsOn(fabricDriverLaneCatalogTask)
            dependsOn(fabricDriverRemapJar)
            inputs.file(fabricDriverLaneCatalog)
            inputs.files(fabricDriverRemapJar)
            outputs.dir(fabricDriverArtifactStagingDir)

            doLast {
                val outputRoot = fabricDriverArtifactStagingDir.get().asFile
                outputRoot.deleteRecursively()
                val catalog = JsonSlurper().parse(fabricDriverLaneCatalog.get().asFile) as Map<*, *>
                catalogEntries(catalog).forEach { entry ->
                    val artifactKey = requiredCatalogString(entry, "artifactKey")
                    val distributionPath = validatedDistributionPath(requiredCatalogString(entry, "distributionPath"))
                    val source =
                        when (artifactKey) {
                            "fabric-current-remap-jar" ->
                                fabricDriverRemapJar
                                    .get()
                                    .outputs
                                    .files
                                    .singleFile

                            else -> error("Unsupported Fabric driver lane artifactKey: $artifactKey")
                        }
                    val target = outputRoot.toPath().resolve(distributionPath).toFile()
                    target.parentFile.mkdirs()
                    source.copyTo(target, overwrite = true)
                }
            }
        }

    distributions {
        main {
            contents {
                from(driverModManifest)
                from(stageFabricDriverLaneArtifacts)
            }
        }
    }

    tasks.named("distZip") {
        dependsOn(stageFabricDriverLaneArtifacts)
        dependsOn(driverModManifest)
    }

    tasks.named("distTar") {
        dependsOn(stageFabricDriverLaneArtifacts)
        dependsOn(driverModManifest)
    }

    tasks.named("installDist") {
        dependsOn(stageFabricDriverLaneArtifacts)
        dependsOn(driverModManifest)
    }
}
