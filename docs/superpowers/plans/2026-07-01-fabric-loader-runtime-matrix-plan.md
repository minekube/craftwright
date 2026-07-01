# Fabric Loader Runtime Matrix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/versions/support-targets` enumerate discovered Fabric Loader runtime rows and reject unsupported loader identities with actionable reasons.

**Architecture:** Keep the supervisor route stable and version-neutral. Extend the protocol DTO/OpenAPI schema with loader stability and a loader-mismatch reason, then have daemon version discovery combine Fabric game targets, Fabric loader versions, and the configured driver mod manifest into explicit runtime rows.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization protocol DTOs, Ktor daemon tests, Gradle via mise.

---

### Task 1: Contract Tests

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`

- [x] Add a daemon route assertion that a supported Minecraft target includes both a supported loader row and an unsupported loader row.
- [x] Add a daemon route assertion that an unsupported Minecraft target includes every discovered loader row with `NO_DRIVER_MOD`.
- [x] Add a protocol OpenAPI assertion for nullable `loaderStable` and the `NO_COMPATIBLE_DRIVER_MOD` enum value.
- [x] Run focused tests and verify they fail before implementation.

### Task 2: Protocol Shape

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`

- [x] Add nullable `loaderStable` to `FabricSupportRuntimeTargetDescriptor`.
- [x] Add `NO_COMPATIBLE_DRIVER_MOD` to `FabricSupportReason`.
- [x] Expose `loaderStable` in the supervisor OpenAPI response schema.

### Task 3: Daemon Discovery

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/VersionDiscoveryService.kt`

- [x] Load Fabric Loader versions while composing support targets.
- [x] Emit one runtime row per discovered loader version.
- [x] Mark rows supported only when a driver mod matches the Minecraft and loader identity.
- [x] Preserve configured driver rows whose loader version is not present in Fabric metadata.

### Task 4: Verification And Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-07-01-fabric-loader-runtime-matrix.md`
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/project-completion-checklist.md`

- [x] Run focused protocol and daemon tests.
- [x] Regenerate/verify docs OpenAPI snapshot.
- [x] Run packaged matrix discovery-only verification.
- [x] Run full local CI.
- [ ] Commit, push `main`, and verify GitHub CI.
