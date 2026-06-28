# Create Client Loader Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let supervisor client creation express a concrete loader-version lane and pass it into cache/runtime preparation.

**Architecture:** Extend the supervisor request DTO and OpenAPI schema, then thread the optional loader version into existing cache preparation. Reuse cache preparation's compatibility validation and resolved-lane driver-mod provider request.

**Tech Stack:** Kotlin/JVM protocol and daemon modules, kotlinx serialization, Gradle through mise.

---

### Task 1: Add Red Protocol Guards

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/ClientModelsTest.kt`
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`

- [x] **Step 1: Test request validation**

  Add a test showing `CreateClientRequest(loaderVersion = "0.19.3")` is
  accepted and a loader version containing a slash is rejected.

- [x] **Step 2: Test OpenAPI schema**

  Add a test assertion that the create-client request schema exposes nullable
  `loaderVersion`.

- [x] **Step 3: Run red protocol tests**

  ```sh
  mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'
  ```

  Expected: fail before implementation because `CreateClientRequest` has no
  `loaderVersion` property/schema.

### Task 2: Add Red Daemon Pass-Through Guard

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Test pinned loader creation**

  Add a create-client test with `"loaderVersion": "0.16.14"` and metadata that
  offers both `0.17.2` and `0.16.14`. Assert:

  - the driver-mod provider receives `loaderVersion = "0.16.14"`;
  - the prepared manifest path contains `1.21.6-fabric-0.16.14.json`.

- [x] **Step 2: Run red daemon test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'
  ```

  Expected: fail before implementation because the request loader version is
  not threaded to cache preparation.

### Task 3: Implement Loader Version Pass-Through

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ClientModels.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt`

- [x] **Step 1: Extend CreateClientRequest**

  Add `val loaderVersion: String? = null` and validate it with
  `requireFileSafeSegment(it, "loader version")`.

- [x] **Step 2: Update OpenAPI schema**

  Add nullable `loaderVersion` to the supervisor create-client request schema.

- [x] **Step 3: Pass through to cache preparation**

  Set `loaderVersion = request.loaderVersion` in the `CachePrepareRequest`
  built by `WorkspaceClientRuntimeDriverFactory.prepare`.

### Task 4: Governance, Evidence, Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-create-client-loader-version.md`

- [x] **Step 1: Record Phase 123 governance/checklist**
- [x] **Step 2: Record red/green/local evidence**
- [x] **Step 3: Run local gates**

  ```sh
  mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'
  git diff --check
  mise run ci
  ```

- [x] **Step 4: Commit and push**

  ```sh
  git add AGENTS.md protocol/src/main/kotlin/com/minekube/craftless/protocol/ClientModels.kt protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt protocol/src/test/kotlin/com/minekube/craftless/protocol/ClientModelsTest.kt protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-123-create-client-loader-version-design.md docs/superpowers/plans/2026-06-28-123-create-client-loader-version-plan.md docs/superpowers/evidence/2026-06-28-create-client-loader-version.md
  git commit -m "feat: pass loader version through client creation"
  git push origin main
  ```

## Self-Review

- Spec coverage: protocol DTO, OpenAPI, daemon pass-through, validation,
  governance, evidence, local gates, and non-goals are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new gameplay action, route family, CLI gameplay catalog, Fabric
  binding, scenario shortcut, compiled lane, public version-specific API, or
  new support claim.
