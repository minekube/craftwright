# Invoke Fallback Naming Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove stale old-invoke naming from active code and governance while preserving graph-owned generic invocation behavior.

**Architecture:** Add a repository policy guard in `NamespacePolicyTest` for active source/docs, then rename test-only fallback counters and active docs. Do not touch runtime dispatch semantics.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Policy Guard

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt`

- [x] **Step 1: Add active wording guard**

  Add a test named `active code and governance avoid stale invoke wording`.
  It should scan Kotlin source plus `AGENTS.md`,
  `docs/project-completion-checklist.md`, and active spec/plan markdown files,
  and reject the stale old-invoke spellings assembled in the test as split
  strings.

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.active code and governance avoid stale invoke wording*'
  ```

  Expected: fail before renaming because active tests/docs still contain stale
  old-invoke wording.

### Task 2: Rename Active Test Wording

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Rename daemon fallback counters**

  Rename the stale fallback counter to `fallbackInvokeCount` and change
  failure messages to `fallback invoke should not run`.

- [x] **Step 2: Rename test names**

  Rename daemon/Fabric tests from stale old-invoke wording to
  `generic invoke fallback` or `compatibility invoke` wording.

### Task 3: Rename Active Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/specs/2026-06-28-79-graph-owned-fabric-invoke-design.md`
- Modify: `docs/superpowers/plans/2026-06-28-79-graph-owned-fabric-invoke-plan.md`
- Create: `docs/superpowers/evidence/2026-06-28-legacy-invoke-naming-removal.md`

- [x] **Step 1: Update Phase 79 wording**

  Replace active stale old-invoke wording with `generic invoke compatibility`
  wording.

- [x] **Step 2: Add Phase 120 governance**

  Add Phase 120 to `AGENTS.md` and checklist, including scope guards and
  verification commands.

- [x] **Step 3: Record evidence**

  Create the Phase 120 evidence file with red/green and local gate results.

### Task 4: Verify, Commit, Push

- [x] **Step 1: Run focused tests**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.active code and governance avoid stale invoke wording*'
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server dispatches graph operations through registered operation adapters*' --tests '*LocalSessionApiServerTest.server rejects graph operation availability and schema before operation adapters*'
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'
  ```

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [ ] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-79-graph-owned-fabric-invoke-design.md docs/superpowers/plans/2026-06-28-79-graph-owned-fabric-invoke-plan.md docs/superpowers/specs/2026-06-28-120-invoke-fallback-naming-removal-design.md docs/superpowers/plans/2026-06-28-120-invoke-fallback-naming-removal-plan.md docs/superpowers/evidence/2026-06-28-invoke-fallback-naming-removal.md protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "test: remove stale invoke wording"
  git push origin main
  ```

## Self-Review

- Spec coverage: policy guard, active source/docs rename, governance/evidence,
  focused tests, and local gates are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no runtime dispatch behavior, public action, generated route family,
  CLI gameplay catalog, Fabric binding, scenario shortcut, compiled lane,
  public version-specific API, or support claim changes.
