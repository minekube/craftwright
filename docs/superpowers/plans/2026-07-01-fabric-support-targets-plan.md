# Fabric Support Targets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose an exhaustive support-status list for Fabric Minecraft targets known to the current daemon.

**Architecture:** Reuse the existing version discovery service and configured driver mod provider. Add protocol DTOs and one stable supervisor route that composes Fabric game metadata with packaged driver lanes.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization, Ktor server tests, OpenAPI route catalog tests, Gradle through `mise`.

---

### Task 1: Protocol And OpenAPI Surface

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/ApiRouteCatalogTest.kt`
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`

- [x] **Step 1: Add failing route/OpenAPI tests**

Require `GET /versions/support-targets` in the stable route catalog and require the OpenAPI response schema to include `targets`.

- [x] **Step 2: Implement protocol DTOs and OpenAPI schema**

Add `FabricSupportTargetListResult`, `FabricSupportTargetDescriptor`, and `FabricSupportReason.NO_DRIVER_MOD`. Add the route metadata and response schema.

- [x] **Step 3: Verify protocol tests**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.ApiRouteCatalogTest.catalog exposes required stable session routes' --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest.stable supervisor openapi describes version discovery and latest defaults' --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest.stable supervisor openapi includes useful descriptions for core pillars'
```

Result: passed.

### Task 2: Daemon Composition

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/VersionDiscoveryService.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Add failing HTTP test**

Extend the existing version discovery endpoint test so `/versions/support-targets` returns one supported target with a driver lane and one unsupported Fabric target with `NO_DRIVER_MOD`.

- [x] **Step 2: Implement support composition**

Group configured Fabric driver mod descriptors by exact Minecraft version. For each Fabric game target, return matching driver mods or `NO_DRIVER_MOD`.

- [x] **Step 3: Verify daemon test**

Run:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.server exposes Minecraft Fabric and packaged driver version discovery'
```

Result: passed.
