package com.minekube.craftless.protocol

import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val output = args.firstOrNull()?.let(Path::of)
    val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
    val encoded = PrettyOpenApiJson.encodeToString(document)

    if (output == null) {
        println(encoded)
        return
    }

    output.parent?.let(Files::createDirectories)
    Files.writeString(output, encoded + "\n")
}
