# Version-Agnostic Driver Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Fabric driver architecture so Minecraft/Fabric version-specific code is isolated behind internal runtime discovery facades and provider strategies while public OpenAPI remains Craftless-owned and graph-generated.

**Architecture:** Keep `driver-fabric` as the consolidated product module where practical. Add internal version/runtime facades, provider selection, compatibility metadata, and matrix-driven tests so additional Minecraft Java versions can be supported without adding public action catalogs or scenario shortcuts.

**Tech Stack:** Kotlin/JVM, Java mixins/accessors where bytecode signatures require them, Fabric Loom, Ktor test surfaces, runtime capability graph, Gradle through mise.

---

### Task 1: Audit Current Version Coupling

**Files:**
- Inspect: `driver-fabric/build.gradle.kts`
- Inspect: `driver-fabric/src/main/resources/fabric.mod.json`
- Inspect: `driver-fabric/src/main/resources/craftless-driver-fabric.mixins.json`
- Inspect: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/`
- Inspect: `driver-fabric/src/main/java/com/minekube/craftless/driver/fabric/v1_21_6/`
- Inspect: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/`
- Inspect: `testkit/src/main/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmoke.kt`

- [ ] **Step 1: Record hard-coded version inputs**

  List every Minecraft, Yarn, Fabric Loader, Fabric API, package-path, mixin,
  entrypoint, smoke default, and artifact naming reference that assumes
  `1.21.6`.

- [ ] **Step 2: Classify each reference**

  Mark each reference as one of:

  - build target for the current compiled driver lane;
  - internal source/package organization;
  - runtime metadata/evidence;
  - test fixture input;
  - public-facing wording that must be removed or generalized.

- [ ] **Step 3: Add an audit note**

  Create `docs/superpowers/evidence/2026-06-26-version-agnostic-driver-audit.md`
  with the classification. The note must say which findings are acceptable
  bootstrap state and which findings block multi-version support.

- [ ] **Step 4: Verify docs only**

  Run:

  ```sh
  git diff --check
  ```

### Task 2: Define Stable Internal Version Facades

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeIdentity.kt`
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeAccess.kt`
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProvider.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProviderTest.kt`

- [ ] **Step 1: Write facade tests first**

  Assert that runtime identity can represent game version, loader version,
  Fabric API version, mappings fingerprint, installed mods fingerprint,
  registry fingerprint, server feature fingerprint, and permissions
  fingerprint without exposing raw Minecraft class names as public data.

- [ ] **Step 2: Define runtime identity values**

  Add internal data types for runtime identity and provider selection evidence.
  Evidence may include private diagnostic fields, but public projection must
  receive Craftless-owned codes only.

- [ ] **Step 3: Define runtime access ports**

  Add internal interfaces for client state, registry summaries, event hooks,
  targeting, interaction, inventory, screen, entity, and world access. Keep
  public graph DTOs out of low-level Minecraft access code except at the probe
  composition boundary.

- [ ] **Step 4: Define provider selection**

  Add an internal provider interface that can answer whether it supports a
  runtime identity and can create the runtime access ports for that lane.

- [ ] **Step 5: Verify focused tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricRuntimeProviderTest*'
  ```

### Task 3: Move 1.21.6 Code Behind A Provider

**Files:**
- Move/modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/`
- Move/modify: `driver-fabric/src/main/java/com/minekube/craftless/driver/fabric/v1_21_6/`
- Modify: `driver-fabric/src/main/resources/fabric.mod.json`
- Modify: `driver-fabric/src/main/resources/craftless-driver-fabric.mixins.json`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProviderTest.kt`
- Test: existing `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/*Test.kt`

- [ ] **Step 1: Preserve behavior with provider tests**

  Before moving implementation code, add tests proving the current lane selects
  a 1.21.6-compatible internal provider and still exposes the same
  Craftless-owned graph nodes.

- [ ] **Step 2: Introduce a non-versioned entrypoint boundary**

  Keep Fabric entrypoint and mod metadata pointing at a stable Craftless class
  that delegates to provider selection. The stable entrypoint must not become a
  public gameplay API.

- [ ] **Step 3: Keep bytecode-sensitive code version-scoped internally**

  Leave exact mixins/accessors in a version-family package when required, but
  route their observations into stable event/access facades.

- [ ] **Step 4: Route graph probes through the runtime access facade**

  Update capability discovery so probes consume runtime access ports and
  provider evidence instead of reaching directly across the whole driver into
  `net.minecraft.*` types.

- [ ] **Step 5: Verify current lane parity**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test
  ```

### Task 4: Add Compatibility Matrix And Simulated Lanes

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrix.kt`
- Create: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrixTest.kt`
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmoke.kt`

- [ ] **Step 1: Define matrix entries**

  Represent supported, experimental, and unsupported Minecraft/Fabric lanes
  with game version predicates, loader constraints, Java runtime expectations,
  optional backend compatibility, and machine-readable unsupported reasons.

- [ ] **Step 2: Add current-lane fixture**

  Add the existing 1.21.6 lane as supported with the current Fabric Loader,
  Fabric API, Java 21, and mapping fingerprint expectations.

- [ ] **Step 3: Add a simulated non-current lane**

  Add a test-only or metadata-only lane that proves provider selection and
  graph availability can vary by version without compiling a second full
  Minecraft dependency lane in the first refactor.

- [ ] **Step 4: Make smoke version selection matrix-aware**

  Keep `CRAFTLESS_SMOKE_MINECRAFT_VERSION` as an override, but validate that
  live smoke records the selected matrix lane and unsupported reason when a
  lane is not runnable.

- [ ] **Step 5: Verify matrix tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'
  ```

### Task 5: Add Probe Capability Metadata

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/RuntimeCapabilityGraph.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/RuntimeCapabilityGraphTest.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProviderTest.kt`

- [ ] **Step 1: Extend private source evidence carefully**

  Add or clarify private source evidence fields so graph nodes can record
  provider id, support range, selected runtime lane, and unavailable/failure
  reason without exposing raw implementation names publicly.

- [ ] **Step 2: Add failure and unavailable distinction**

  Preserve current available/unavailable graph semantics for projection, and
  add private evidence that distinguishes unsupported version, missing class,
  missing method, missing registry, permission-denied, mod-conflict, and
  client-state unavailable cases.

- [ ] **Step 3: Update namespace guard tests**

  Assert that public OpenAPI, action descriptors, resource descriptors, handle
  ids, and SSE event names still reject Fabric/Yarn/intermediary/raw Minecraft
  leakage after metadata changes.

- [ ] **Step 4: Verify protocol and driver tests**

  Run:

  ```sh
  mise exec -- gradle :protocol:test :driver-fabric:test
  ```

### Task 6: Generalize Build And Fixture Configuration

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `driver-fabric/src/main/resources/fabric.mod.json`
- Modify: `driver-fabric/src/main/resources/craftless-driver-fabric.mixins.json`
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmoke.kt`
- Modify: `.mise.toml`

- [ ] **Step 1: Separate compiled lane from runtime matrix**

  Make it explicit in Gradle and docs that the module has a compiled Fabric
  lane and a runtime compatibility matrix. Do not pretend one compiled Loom
  target means all versions work.

- [ ] **Step 2: Parameterize where Loom safely allows it**

  Move Minecraft, Yarn, Fabric Loader, and Fabric API pins behind Gradle
  properties only if the resulting build remains reproducible. Defaults must
  match the verified current lane.

- [ ] **Step 3: Keep resource metadata honest**

  Update Fabric mod naming/description and dependency wording so it does not
  overclaim broad support before lanes are verified.

- [ ] **Step 4: Add matrix verification task**

  Add a focused Gradle or mise task that runs unit compatibility checks without
  launching every live Minecraft client.

- [ ] **Step 5: Verify build metadata**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:processResources :driver-fabric:test
  ```

### Task 7: Add Multi-Lane CI And Smoke Evidence

**Files:**
- Modify: `.github/workflows/`
- Modify: `.mise.toml`
- Modify: `docs/final-gameplay-runbook.md`
- Modify: `docs/project-completion-checklist.md` only after implementation is accepted

- [ ] **Step 1: Add static compatibility CI**

  Add CI coverage for provider selection, compatibility matrix, namespace
  policy, and graph projection without launching Minecraft.

- [ ] **Step 2: Add opt-in live lane smoke**

  Keep live Minecraft smoke opt-in. Allow selecting a matrix lane and recording
  lane metadata in artifacts. Do not make final completion depend on a single
  1.21.6-only run.

- [ ] **Step 3: Keep final gameplay separate**

  Do not change the final survival completion criteria. Version architecture
  evidence is separate from Robin's final Minecraft chat confirmation.

- [ ] **Step 4: Verify CI task locally where practical**

  Run focused checks first:

  ```sh
  mise exec -- gradle :protocol:test :driver-api:test :driver-fabric:test :testkit:test
  ```

  Then run, when practical:

  ```sh
  mise run ci
  ```

### Task 8: Update Governance After Acceptance

**Files:**
- Modify: `AGENTS.md`
- Modify: `driver-fabric/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/specs/2026-06-26-26-version-agnostic-driver-architecture-design.md` only for accepted clarifications
- Modify: `docs/superpowers/plans/2026-06-26-26-version-agnostic-driver-architecture-plan.md` only for accepted clarifications

- [ ] **Step 1: Update agent guidance**

  After implementation is accepted, add the version-agnostic architecture rule:
  new Minecraft version support belongs behind runtime/provider facades and
  compatibility matrix evidence, not public action catalogs.

- [ ] **Step 2: Update checklist status**

  Add Phase 26 status with evidence commands. Do not mark Craftless complete.
  Do not check final gameplay items unless the separate final gameplay gate is
  actually satisfied.

- [ ] **Step 3: Verify docs and full suite**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :protocol:test :driver-api:test :driver-fabric:test :daemon:test :testkit:test
  ```

  Run `mise run ci` when practical before claiming the implementation complete.

### Guardrails For Every Task

- [ ] Do not add static survival shortcut actions.
- [ ] Do not add one public descriptor/binding pair per version/action.
- [ ] Do not expose raw Fabric, Yarn, intermediary, Minecraft, mod package, or
  launcher names in public OpenAPI, CLI help, README examples, or SSE event
  names.
- [ ] Keep Minecraft calls on the client thread.
- [ ] Treat unavailable runtime support as explicit metadata, not as silent
  omission when a probe can explain the reason.
- [ ] Preserve generated per-client OpenAPI as the source of truth.
- [ ] Preserve unrelated dirty files and do not push or commit unless asked.
