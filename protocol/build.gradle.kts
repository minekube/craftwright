dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

tasks.register<JavaExec>("exportSupervisorOpenApi") {
    group = "documentation"
    description = "Export the stable Craftless supervisor OpenAPI document for static docs."
    val outputFile =
        providers
            .gradleProperty("craftless.openapi.output")
            .orElse(
                rootProject.layout.projectDirectory
                    .file("docs-site/openapi/craftless-supervisor.json")
                    .asFile
                    .absolutePath,
            )
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.minekube.craftless.protocol.ExportSupervisorOpenApiKt")
    args(outputFile.get())
    outputs.file(outputFile)
}
