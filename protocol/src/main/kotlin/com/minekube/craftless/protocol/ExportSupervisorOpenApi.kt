package com.minekube.craftless.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private val openApiJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

fun main(args: Array<String>) {
    val output = args.firstOrNull()?.let(Path::of)
    val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())
    val encoded = openApiJson.encodeToString(document)

    if (output == null) {
        println(encoded)
        return
    }

    output.parent?.let(Files::createDirectories)
    Files.writeString(output, encoded + "\n")
}
