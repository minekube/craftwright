# Phase 43: Client Logging Config Design

## Goal

Craftless cache preparation must honor Mojang version metadata for client
logging configuration so prepared real-client launches include the expected
Log4j configuration file and JVM argument.

## Context

Current Minecraft metadata, including `1.21.6`, can contain:

```json
{
  "logging": {
    "client": {
      "argument": "-Dlog4j.configurationFile=${path}",
      "file": {
        "id": "client-1.21.2.xml",
        "url": "https://piston-data.mojang.com/..."
      },
      "type": "log4j2-xml"
    }
  }
}
```

The launcher is expected to download that file and pass the resolved logging
argument on the JVM command line. Craftless already prepares version manifests,
libraries, assets, natives, Java runtimes, and launch arguments; logging config
belongs in that same supervisor-owned cache/launch layer.

## Requirements

- Detect `logging.client.file.id`, `logging.client.file.url`, and
  `logging.client.argument` from the selected Minecraft version manifest.
- Cache the logging config as a prepared artifact under a safe Craftless cache
  handle derived from the Minecraft version and logging file id.
- Include the logging artifact in exported and cleaned prepared-cache handles.
- Add the logging JVM argument to the prepared launch arguments when metadata
  provides it.
- Resolve `${path}` in that logging JVM argument to the prepared logging config
  handle at cache-preparation time.
- Validate logging file ids before using them in handles.
- Keep this change inside supervisor cache preparation and process launch
  metadata.
- Do not expose Log4j, Mojang logging internals, Fabric names, gameplay
  actions, generated route families, CLI gameplay catalogs, scenario shortcuts,
  or Fabric descriptor/binding pairs as public gameplay API.

## Non-Goals

- Do not add a custom logging API.
- Do not configure application logging for the Craftless daemon.
- Do not add version-specific special cases for one Minecraft release.
- Do not change generated per-client gameplay OpenAPI.

## Verification

- Focused daemon test proves logging config is cached and the prepared JVM
  launch arguments contain the resolved `-Dlog4j.configurationFile=...` value.
- Existing cache preparation tests still pass.
- `mise run lint`, `mise run architecture-check`, and `mise run ci` pass
  before claiming this phase complete.
