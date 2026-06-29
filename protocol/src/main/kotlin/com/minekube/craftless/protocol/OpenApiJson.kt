package com.minekube.craftless.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val OpenApiJson: Json =
    Json {
        encodeDefaults = false
        explicitNulls = false
    }

@OptIn(ExperimentalSerializationApi::class)
val PrettyOpenApiJson: Json =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = false
        explicitNulls = false
    }
